# Fluxo completo de upload de vídeo — passo a passo pelas camadas

Este documento acompanha uma requisição real do início ao fim: o usuário se cadastra, confirma o
e-mail, faz login, envia um vídeo e o assiste. Para cada passo: **qual endpoint é chamado, quais
camadas a chamada atravessa, quais classes participam e o trecho de código relevante**.

Reflete o código em `dev` após os PRs #7–#25 (relatório de melhorias implementado).
Os diagramas deste documento também existem como arquivos independentes em
[`diagrams/`](diagrams/README.md).

## Sumário

1. [As camadas e quem mora em cada uma](#1-as-camadas)
2. [Visão geral do fluxo](#2-visão-geral)
3. [Passo 0 — Cadastro, confirmação e login](#3-passo-0)
4. [Passo 1 — Iniciar o upload (`POST /api/v1/videos`)](#4-passo-1)
5. [Passo 2 — Upload direto ao storage (PUT presignado)](#5-passo-2)
6. [Passo 3 — Confirmar o upload (`POST /api/v1/videos/{id}/complete-upload`)](#6-passo-3)
7. [Passo 4 — Processamento no worker (RabbitMQ + FFmpeg)](#7-passo-4)
8. [Passo 5 — Assistir (`GET /api/v1/videos/{slug}` e `/stream`)](#8-passo-5)
9. [Ciclo de vida do status e caminhos de erro](#9-status-e-erros)

---

## 1. As camadas <a id="1-as-camadas"></a>

O projeto é um Gradle multi-módulo em Clean Architecture. A regra de dependência aponta sempre
para dentro (e é **verificada por teste** — `ArchitectureTest`, ArchUnit):

```mermaid
flowchart TB
    subgraph bootstrap["bootstrap-api / bootstrap-worker (composição)"]
        CTRL["Controllers, Filtros de segurança,<br/>Config Spring, Listener Rabbit"]
    end
    subgraph infra["infrastructure (adapters)"]
        ADP["JPA Repositories, S3StorageAdapter,<br/>RabbitPublisher, JwtTokenService, MailSender"]
    end
    subgraph app["application (use cases)"]
        UC["InitiateUploadUseCase, CompleteUploadUseCase,<br/>ProcessVideoUseCase, ports de saída"]
    end
    subgraph dom["domain (puro, sem framework)"]
        ENT["Video, Channel, User,<br/>VideoRepository (interface), exceções"]
    end
    CTRL --> UC
    ADP -. implementa os ports .-> UC
    UC --> ENT
    ADP --> ENT
```

| Módulo | Papel no fluxo de upload | Exemplos |
|---|---|---|
| `domain` | Entidades e regras puras; **zero** dependência de framework | `Video` (transições de status), `VideoRepository` (interface) |
| `application` | Use cases: orquestram entidades através de **ports** (interfaces) | `InitiateUploadUseCase`, `StoragePort`, `VideoProcessingPublisher` |
| `infrastructure` | Adapters que implementam os ports com tecnologia real | `S3StorageAdapter` (AWS SDK), `VideoRepositoryAdapter` (JPA), `RabbitVideoProcessingPublisher` |
| `bootstrap-api` | A aplicação REST: controllers, segurança, wiring | `VideosController`, `JwtAuthenticationFilter`, `SecurityConfig` |
| `bootstrap-worker` | A aplicação consumidora da fila: listener + FFmpeg | `VideoProcessingListener`, `FfmpegVideoAnalyzer` |

O ponto-chave da arquitetura: `InitiateUploadUseCase` não conhece S3 nem JPA. Ele fala com
`StoragePort` e `VideoRepository` (interfaces); o Spring injeta `S3StorageAdapter` e
`VideoRepositoryAdapter` em runtime.

---

## 2. Visão geral do fluxo <a id="2-visão-geral"></a>

O upload usa **URLs presignadas**: os bytes do vídeo **nunca passam pela API**. A API só emite
autorizações criptográficas (URLs assinadas) e coordena estado; o cliente fala direto com o
MinIO/S3, e o worker processa de forma assíncrona via RabbitMQ.

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant API as bootstrap-api
    participant DB as PostgreSQL
    participant S3 as MinIO/S3
    participant MQ as RabbitMQ
    participant W as bootstrap-worker

    Note over C,API: Passo 0 — conta
    C->>API: POST /api/v1/auth/register
    API->>DB: user + channel + token de confirmação
    C->>API: GET /api/v1/auth/confirm-email?token=...
    C->>API: POST /api/v1/auth/login
    API-->>C: access_token (JWT) + refresh_token

    Note over C,S3: Passos 1–2 — upload
    C->>API: POST /api/v1/videos {title, sizeBytes, contentType}
    API->>DB: INSERT video (PENDING_UPLOAD)
    API-->>C: 201 {id, slug, uploadUrl presignada}
    C->>S3: PUT uploadUrl (bytes do vídeo)
    S3-->>C: 200

    Note over C,MQ: Passo 3 — confirmação
    C->>API: POST /api/v1/videos/{id}/complete-upload
    API->>S3: HEAD object (existe?)
    API->>DB: UPDATE video → QUEUED (commit)
    API->>MQ: publish {videoId} (após o commit)
    API-->>C: 204

    Note over MQ,W: Passo 4 — processamento assíncrono
    MQ->>W: VideoProcessingMessage {videoId}
    W->>DB: UPDATE → PROCESSING (commit imediato)
    W->>S3: GET presignado (ffprobe + thumbnail)
    W->>S3: PUT thumbnails/{slug}.jpg
    W->>DB: UPDATE → READY (duração, thumbnail, metadata)

    Note over C,S3: Passo 5 — consumo
    C->>API: GET /api/v1/videos/{slug}/stream
    API-->>C: 302 Location: URL presignada
    C->>S3: GET (streaming direto)
```

---

## 3. Passo 0 — Cadastro, confirmação e login <a id="3-passo-0"></a>

### 3.1 `POST /api/v1/auth/register`

```json
{ "email": "maria@exemplo.com", "password": "senhaSegura123" }
```

**Trajeto da requisição, na ordem em que as camadas são atravessadas:**

1. **`RateLimitingFilter`** (bootstrap-api/security) — `/api/v1/auth/register` está na lista de rotas
   limitadas: token bucket de 10 req/min por IP (o IP real vem do `X-Forwarded-For` via
   RemoteIp valve do Tomcat, apenas quando o peer é proxy confiável). Estourou → `429` com
   `Retry-After`.
2. **`SecurityConfig`** — a rota está no allowlist `permitAll()`, segue sem JWT.
3. **`AuthController`** (bootstrap-api/web) — bean validation no DTO (`@Email`, `@NotBlank`) e
   delegação:

```java
@PostMapping("/register")
@ResponseStatus(HttpStatus.CREATED)
public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
  RegisterResult result = registerUser.execute(request.email(), request.password());
  return new RegisterResponse(result.id(), result.email());
}
```

4. **`RegisterUserUseCase`** (application/auth) — a orquestração inteira numa transação:
   normaliza o e-mail, checa duplicidade, cria `User` + `Channel` (nickname único com retry) e
   emite o token de confirmação. Note que ele só fala com **ports**:

```java
@Transactional
public RegisterResult execute(String email, String rawPassword) {
  String normalizedEmail = email.trim().toLowerCase();
  if (userRepository.existsByEmail(normalizedEmail)) {
    throw new EmailAlreadyRegisteredException();          // → 409 no handler
  }

  Instant now = clock.instant();
  User user = userRepository.save(
      User.register(UUID.randomUUID(), normalizedEmail, passwordHasher.hash(rawPassword), now));

  String nickname = generateUniqueNickname(normalizedEmail);
  String name = normalizedEmail.substring(0, normalizedEmail.indexOf('@'));
  channelRepository.save(Channel.createForUser(UUID.randomUUID(), user.id(), name, nickname, now));

  IssuedVerificationToken token = verificationTokenService.issueConfirmation();
  verificationTokenRepository.save(VerificationToken.issue(
      UUID.randomUUID(), user.id(), VerificationTokenType.EMAIL_CONFIRMATION,
      token.tokenHash(), token.expiresAt(), now));

  mailSender.sendConfirmationEmail(user.email(), token.rawValue());
  return new RegisterResult(user.id(), user.email());
}
```

   Ports usados → adapters reais: `PasswordHasher` → `ArgonPasswordHasher` (Argon2);
   `UserRepository` → `UserRepositoryAdapter` (JPA); `MailSender` → `SpringMailSender`.

5. **`SpringMailSender`** (infrastructure/mail) — detalhe importante do PR #9: o e-mail **não sai
   dentro da transação**. O adapter adia o envio para depois do commit via `AfterCommitExecutor`
   e engole falha de SMTP (loga em vez de fazer rollback do cadastro):

```java
private void send(String to, String subject, String template, Context context) {
  afterCommit.run(() -> {
    try {
      // monta MimeMessage com template Thymeleaf e envia
      mailSender.send(message);
    } catch (Exception e) {
      log.error("Failed to send email '{}' to {}", subject, to, e);
    }
  });
}
```

### 3.2 `GET /api/v1/auth/confirm-email?token=...`

O link chega por e-mail (Mailpit em dev). `ConfirmEmailUseCase` faz hash do token cru, busca por
`(hash, EMAIL_CONFIRMATION)`, valida consumo/expiração, marca `user.confirm(now)` e consome o
token — single-use. Token desconhecido/consumido → `400 INVALID_TOKEN`; expirado → `410 TOKEN_EXPIRED`.

### 3.3 `POST /api/v1/auth/login`

`LoginUseCase` valida credenciais (Argon2 `matches`), exige e-mail confirmado (senão
`403 EMAIL_NOT_CONFIRMED`) e emite o par de tokens:

```java
Instant now = clock.instant();
UUID family = UUID.randomUUID();                    // nova família de refresh tokens
IssuedRefreshToken refresh = refreshTokenService.issue(user.id(), family, jti);
refreshTokenRepository.save(RefreshToken.issue(...));// persiste só o HASH do refresh token

IssuedAccessToken access = accessTokenService.issue(user.id(), user.email()); // JWT HS256, 15 min
return new TokenPair(access.token(), access.expiresInSeconds(), refresh.rawValue());
```

Resposta:

```json
{ "access_token": "eyJhbGciOiJIUzI1NiJ9...", "token_type": "Bearer",
  "expires_in": 900, "refresh_token": "b64url-opaco..." }
```

Daqui em diante o cliente envia `Authorization: Bearer <access_token>` em toda rota protegida.

---

## 4. Passo 1 — Iniciar o upload: `POST /api/v1/videos` <a id="4-passo-1"></a>

```http
POST /api/v1/videos
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{ "title": "Meu primeiro vídeo", "sizeBytes": 1048576, "contentType": "video/mp4" }
```

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant F as JwtAuthenticationFilter
    participant VC as VideosController
    participant UC as InitiateUploadUseCase
    participant CR as ChannelRepository (port→JPA)
    participant VR as VideoRepository (port→JPA)
    participant SP as StoragePort (port→S3Adapter)

    C->>F: POST /api/v1/videos (Bearer JWT)
    F->>F: verifica assinatura/expiração,<br/>popula SecurityContext
    F->>VC: segue a cadeia
    VC->>UC: execute(userId, title, sizeBytes, contentType)
    UC->>UC: valida sizeBytes ≤ máx e contentType video/*
    UC->>CR: findByUserId(userId)
    UC->>UC: gera slug único (retry até 5x)
    UC->>VR: save(Video PENDING_UPLOAD)
    UC->>SP: presignUpload(key, sizeBytes, contentType)
    SP-->>UC: URL assinada (SigV4, TTL 15 min)
    UC-->>VC: InitiateUploadResult
    VC-->>C: 201 {id, slug, uploadUrl}
```

**Camada por camada:**

1. **`JwtAuthenticationFilter`** (bootstrap-api/security) — extrai o Bearer, delega ao
   `JwtTokenService` (infrastructure) que valida assinatura HS256 + expiração e devolve o
   principal; o filtro popula o `SecurityContext`:

```java
String token = header.substring(BEARER_PREFIX.length());
jwtTokenService.verify(token).ifPresent(user -> {
  AuthenticatedUser principal = new AuthenticatedUser(user.id(), user.email());
  SecurityContextHolder.getContext().setAuthentication(
      new UsernamePasswordAuthenticationToken(principal, null, List.of()));
});
```

   Sem token válido, `SecurityConfig` (`anyRequest().authenticated()`) barra a requisição e o
   `SecurityErrorResponses` escreve o `ErrorEnvelope` de `401 UNAUTHORIZED`.

2. **`VideosController`** — recebe o principal via `@AuthenticationPrincipal` e o DTO validado
   (`@NotBlank title`, `@NotNull @Positive sizeBytes`, `@NotBlank contentType`):

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public InitiateUploadResponse initiate(
    @AuthenticationPrincipal AuthenticatedUser principal,
    @Valid @RequestBody CreateVideoRequest request) {
  InitiateUploadResult result = initiateUpload.execute(
      principal.id(), request.title(), request.sizeBytes(), request.contentType());
  return new InitiateUploadResponse(result.id(), result.slug(), result.uploadUrl());
}
```

3. **`InitiateUploadUseCase`** (application/video) — validações de negócio, criação do rascunho
   e emissão da URL presignada:

```java
@Transactional
public InitiateUploadResult execute(UUID userId, String title, long sizeBytes, String contentType) {
  if (sizeBytes <= 0 || sizeBytes > maxUploadSizeBytes) {      // UPLOAD_MAX_SIZE_BYTES (2 GiB)
    throw new InvalidUploadSizeException();                    // → 400 INVALID_UPLOAD_SIZE
  }
  if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("video/")) {
    throw new UnsupportedVideoTypeException();                 // → 400 UNSUPPORTED_VIDEO_TYPE
  }

  Channel channel = channelRepository.findByUserId(userId)
      .orElseThrow(() -> new IllegalStateException("User has no channel"));

  String slug = generateUniqueSlug();                          // ex.: "TKNJAGcikRY"
  String storageKey = "videos/" + slug;
  Video video = videoRepository.save(
      Video.initiate(UUID.randomUUID(), channel.id(), title, slug, storageKey, now));

  String uploadUrl = storage.presignUpload(storageKey, sizeBytes, contentType);
  return new InitiateUploadResult(video.id(), video.slug(), uploadUrl);
}
```

4. **`Video.initiate`** (domain) — factory que nasce no status inicial:

```java
public static Video initiate(
    UUID id, UUID channelId, String title, String slug, String storageKey, Instant now) {
  return new Video(id, channelId, title, slug, VideoStatus.PENDING_UPLOAD,
      storageKey, null, null, null, null, now, now);
}
```

5. **`VideoRepositoryAdapter`** (infrastructure/persistence) — mapeia domínio ↔ entidade JPA via
   `PersistenceMapper` e grava na tabela `videos` (migração `V4__videos.sql`).

6. **`S3StorageAdapter.presignUpload`** (infrastructure/storage) — assina a URL com SigV4.
   Crucial (PR #22): `contentLength` e `contentType` entram **na assinatura** — se o cliente
   subir bytes a mais ou outro tipo, o próprio storage rejeita com 403:

```java
@Override
public String presignUpload(String key, long contentLength, String contentType) {
  PutObjectRequest put = PutObjectRequest.builder()
      .bucket(bucket).key(key)
      .contentLength(contentLength)     // vira header assinado
      .contentType(contentType)         // idem
      .build();
  PutObjectPresignRequest req = PutObjectPresignRequest.builder()
      .signatureDuration(UPLOAD_TTL)    // 15 minutos
      .putObjectRequest(put)
      .build();
  return publicPresigner.presignPutObject(req).url().toString();
}
```

   Detalhe de infraestrutura: existem **dois presigners** — `publicPresigner` assina contra o
   host público (`localhost:9000`) para URLs entregues ao cliente, e `internalPresigner` contra o
   host interno (`minio:9000`) para o worker. O host é header assinado no SigV4: assinar num host
   e trocar depois gera `SignatureDoesNotMatch`.

**Resposta:**

```json
{ "id": "9be8654e-...", "slug": "TKNJAGcikRY",
  "uploadUrl": "http://localhost:9000/streamtube-videos/videos/TKNJAGcikRY?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost&X-Amz-Expires=900&..." }
```

Estado no banco: `videos.status = 'PENDING_UPLOAD'`.

---

## 5. Passo 2 — Upload direto ao storage <a id="5-passo-2"></a>

O cliente faz o PUT **direto no MinIO/S3** — a API está fora do caminho dos bytes:

```http
PUT {uploadUrl}
Content-Type: video/mp4
Content-Length: 1048576

<bytes do vídeo>
```

Regras impostas pela assinatura (sem nenhum código nosso executando):

| O cliente envia | Resultado |
|---|---|
| Exatamente o tamanho e tipo declarados | `200 OK`, objeto gravado em `videos/{slug}` |
| `Content-Type` diferente do declarado | `403 SignatureDoesNotMatch` |
| Mais/menos bytes que o declarado | `403 SignatureDoesNotMatch` |
| Depois de 15 minutos | `403` (assinatura expirada) |

(Comportamento coberto por teste de integração real: `S3StorageAdapterIntegrationTest.presignedUploadRejectsMismatchedSizeAndType`.)

---

## 6. Passo 3 — Confirmar o upload: `POST /api/v1/videos/{id}/complete-upload` <a id="6-passo-3"></a>

O storage não avisa a API quando o PUT termina — quem avisa é o cliente. A API então **verifica**
no storage antes de acreditar.

1. **`VideosController.complete`** → `completeUpload.execute(id, principal.id())` → `204`.

2. **`CompleteUploadUseCase`** (application/video) — quatro validações em sequência e a
   transição de estado:

```java
@Transactional
public void execute(UUID videoId, UUID userId) {
  Video video = videoRepository.findById(videoId).orElseThrow(VideoNotFoundException::new); // 404

  Channel channel = channelRepository.findByUserId(userId)
      .orElseThrow(ForbiddenVideoAccessException::new);
  if (!video.channelId().equals(channel.id())) {
    throw new ForbiddenVideoAccessException();          // 403 — não é o dono
  }
  if (video.status() != VideoStatus.PENDING_UPLOAD) {
    throw new VideoStatusConflictException();           // 422 — complete duplicado etc.
  }
  if (!storage.objectExists(video.storageKey())) {      // HEAD no MinIO/S3
    throw new UploadNotCompletedException();            // 409 — PUT não aconteceu
  }

  video.markQueued(clock.instant());
  videoRepository.save(video);
  publisher.publish(video.id());                        // só efetiva APÓS o commit (abaixo)
}
```

3. **`RabbitVideoProcessingPublisher`** (infrastructure/messaging) — o ponto mais sutil do fluxo
   (PR #9). Publicar dentro da transação criaria uma corrida: o worker poderia consumir a
   mensagem **antes** do status `QUEUED` estar commitado (ou após um rollback). O publish é
   adiado para depois do commit:

```java
@Override
public void publish(UUID videoId) {
  afterCommit.run(() ->
      rabbitTemplate.convertAndSend(
          VideoQueue.EXCHANGE, VideoQueue.ROUTING_KEY, new VideoProcessingMessage(videoId)));
}
```

   com o `AfterCommitExecutor` registrando uma sincronização de transação:

```java
public void run(Runnable action) {
  if (TransactionSynchronizationManager.isSynchronizationActive()) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override public void afterCommit() { action.run(); }
        });
  } else {
    action.run();   // fora de transação, executa na hora
  }
}
```

4. **Topologia RabbitMQ** (`RabbitConfig` + `VideoQueue`): a mensagem `{"videoId": "..."}` (JSON)
   vai para o exchange `video.exchange` com routing key `video.process`, cai na fila durável
   `video.processing`, que tem dead-letter para `video.dlx` → `video.processing.dlq`.

```mermaid
flowchart LR
    P[Publisher<br/>após commit] -->|video.process| EX(("video.exchange"))
    EX --> Q[["video.processing"]]
    Q -->|"3 tentativas esgotadas<br/>(retry 5s ×2 backoff)"| DLX(("video.dlx"))
    DLX --> DLQ[["video.processing.dlq"]]
    Q --> L[VideoProcessingListener<br/>onMessage]
    DLQ --> LD[VideoProcessingListener<br/>onDeadLetter → marca ERROR]
```

Estado no banco: `videos.status = 'QUEUED'`. Resposta ao cliente: `204 No Content`.

---

## 7. Passo 4 — Processamento no worker <a id="7-passo-4"></a>

Aplicação separada (`bootstrap-worker`, container próprio com ffmpeg instalado, rodando como
usuário non-root). Consome a fila e atualiza o mesmo banco.

1. **`VideoProcessingListener`** (bootstrap-worker/listener):

```java
@RabbitListener(queues = VideoQueue.QUEUE)
public void onMessage(VideoProcessingMessage message) {
  log.info("Processing video {}", message.videoId());
  processVideo.execute(message.videoId());
  log.info("Video {} ready", message.videoId());
}
```

2. **`ProcessVideoUseCase`** (application/video) — deliberadamente **sem** `@Transactional` no
   `execute` (PR #11): FFprobe/FFmpeg podem rodar por minutos, e uma transação nesse escopo
   prenderia uma conexão do pool o tempo todo e esconderia o status `PROCESSING` até o commit
   final. Cada `save` commita na hora, em sua própria transação curta:

```java
public void execute(UUID videoId) {
  Video video = videoRepository.findById(videoId).orElseThrow(VideoNotFoundException::new);
  if (video.isReady()) {
    return; // idempotente em redelivery
  }

  // Commita já: PROCESSING fica visível enquanto a análise (longa) roda abaixo.
  video.markProcessing(clock.instant());
  videoRepository.save(video);

  // Trabalho externo longo — nenhuma transação (nem conexão) presa durante este bloco.
  String inputUrl = storage.presignInternal(video.storageKey());
  ProbeResult probe = analyzer.probe(inputUrl);
  byte[] thumbnail = analyzer.extractThumbnail(inputUrl);

  String thumbnailKey = "thumbnails/" + video.slug() + ".jpg";
  storage.putObject(thumbnailKey, thumbnail, "image/jpeg");

  video.markReady(probe.durationSeconds(), thumbnailKey, probe.rawJson(), clock.instant());
  videoRepository.save(video);
}
```

   Repare no `presignInternal`: o worker lê o vídeo via URL assinada contra o host **interno**
   (`minio:9000`), pois `localhost:9000` não resolve dentro do container.

3. **`FfmpegVideoAnalyzer`** (bootstrap-worker/ffmpeg) — dois processos externos via
   `ProcessBuilder` (timeout de 120s cada):

```java
// metadados (duração, streams, codecs) em JSON
ffprobe -v quiet -print_format json -show_format -show_streams <url-presignada>

// melhor frame como thumbnail, 1280px de largura
ffmpeg -i <url-presignada> -vf thumbnail,scale=1280:-1 -frames:v 1 -y /tmp/thumb-XXX.jpg
```

```java
JsonNode duration = root.path("format").path("duration");
Double seconds = duration.isMissingNode() ? null : duration.asDouble();
return new ProbeResult(seconds, json);   // o JSON cru inteiro vai para videos.metadata
```

4. **Caminho de falha** — arquivo corrompido, ffmpeg quebrando, S3 fora:
   o listener relança a exceção → Spring Retry tenta **3 vezes** (5s, backoff ×2) → esgotou,
   a mensagem é rejeitada sem requeue → dead-letter → `video.processing.dlq` → o listener da DLQ
   marca o desfecho:

```java
@RabbitListener(queues = VideoQueue.DLQ)
public void onDeadLetter(VideoProcessingMessage message) {
  log.error("Video {} failed processing after retries; marking ERROR", message.videoId());
  processVideo.markFailed(message.videoId(), "Processing failed after retries");
}
```

   Se o worker **morrer no meio** (crash), o vídeo fica `PROCESSING`; a redelivery do Rabbit
   reentra no `execute`, que re-marca `PROCESSING` e reprocessa (coberto por teste).

Estado final no banco:

```sql
status = 'READY', duration_seconds = 90.0,
thumbnail_key = 'thumbnails/TKNJAGcikRY.jpg', metadata = '{ ...json do ffprobe... }'
```

---

## 8. Passo 5 — Assistir <a id="8-passo-5"></a>

Rotas públicas (`GET /api/v1/videos/**` no allowlist — não exigem login).

**`GET /api/v1/videos/{slug}`** → `GetVideoInfoUseCase` → info pública com a thumbnail já presignada:

```json
{ "id": "9be8654e-...", "slug": "TKNJAGcikRY", "title": "Meu primeiro vídeo",
  "status": "READY", "durationSeconds": 90.0,
  "thumbnailUrl": "http://localhost:9000/streamtube-videos/thumbnails/TKNJAGcikRY.jpg?X-Amz-...",
  "channelId": "...", "createdAt": "2026-07-06T..." }
```

**`GET /api/v1/videos/{slug}/stream`** → redirect para o storage; o player segue o 302 e faz streaming
(com range requests) direto do MinIO/S3 — de novo, bytes fora da API:

```java
@GetMapping("/{slug}/stream")
public ResponseEntity<Void> stream(@PathVariable("slug") String slug) {
  return ResponseEntity.status(HttpStatus.FOUND)
      .location(URI.create(getStreamUrl.execute(slug)))   // presignStream, TTL 1h
      .build();
}
```

`GetStreamUrlUseCase` recusa vídeo não processado:

```java
Video video = videoRepository.findBySlug(slug).orElseThrow(VideoNotFoundException::new);
if (!video.isReady()) {
  throw new VideoNotReadyException();    // → 422 VIDEO_NOT_READY
}
return storage.presignStream(video.storageKey());
```

`GET /api/v1/videos/{slug}/download` é idêntico, mas assina com
`response-content-disposition: attachment; filename="..."` para forçar download.

---

## 9. Ciclo de vida do status e caminhos de erro <a id="9-status-e-erros"></a>

```mermaid
stateDiagram-v2
    [*] --> PENDING_UPLOAD: POST /api/v1/videos
    PENDING_UPLOAD --> QUEUED: complete-upload<br/>(objeto existe no storage)
    QUEUED --> PROCESSING: worker consome<br/>(commit imediato)
    PROCESSING --> READY: ffprobe + thumbnail OK
    PROCESSING --> PROCESSING: crash + redelivery<br/>(re-marca e reprocessa)
    QUEUED --> ERROR: 3 tentativas esgotadas → DLQ
    PROCESSING --> ERROR: 3 tentativas esgotadas → DLQ
    READY --> [*]
```

**Erros por passo (todos no `ErrorEnvelope` padrão):**

| Passo | Cenário | HTTP | `code` |
|---|---|---|---|
| 0 | e-mail já cadastrado | 409 | `EMAIL_ALREADY_REGISTERED` |
| 0 | login sem confirmar e-mail | 403 | `EMAIL_NOT_CONFIRMED` |
| 0 | senha errada | 401 | `INVALID_CREDENTIALS` |
| 0 | rate limit estourado | 429 | `RATE_LIMITED` (+ `Retry-After`) |
| 1 | sem/expirado Bearer | 401 | `UNAUTHORIZED` |
| 1 | `sizeBytes` acima do limite | 400 | `INVALID_UPLOAD_SIZE` |
| 1 | `contentType` não é `video/*` | 400 | `UNSUPPORTED_VIDEO_TYPE` |
| 2 | tamanho/tipo divergem do declarado | 403 | (direto do storage, `SignatureDoesNotMatch`) |
| 3 | vídeo de outro usuário | 403 | `FORBIDDEN_VIDEO_ACCESS` |
| 3 | complete sem PUT no storage | 409 | `UPLOAD_NOT_COMPLETED` |
| 3 | complete duplicado | 422 | `VIDEO_STATUS_CONFLICT` |
| 5 | slug inexistente | 404 | `VIDEO_NOT_FOUND` |
| 5 | stream antes do READY | 422 | `VIDEO_NOT_READY` |

**Onde cada decisão de design está testada:**

| Decisão | Teste |
|---|---|
| Publish só após o commit | `RabbitVideoProcessingPublisherTest` |
| E-mail após commit, falha não propaga | `SpringMailSenderTest` |
| `PROCESSING` visível antes da análise | `ProcessVideoUseCaseTest.processesVideoToReady` |
| Storage rejeita upload divergente | `S3StorageAdapterIntegrationTest` |
| Dono/status/objeto no complete-upload | `CompleteUploadUseCaseTest` |
| Fluxo HTTP completo com Postgres real | `VideosE2ETest.initiateCompleteAndStreamFlow` |
| Regras de camada | `ArchitectureTest` (ArchUnit) |
