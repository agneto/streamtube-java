# StreamTube (Java) — Guia de Uso, Teste e Depuração

Este documento explica, em português e passo a passo, **o que o sistema faz**, **como
subir o ambiente**, **como testar** (com `curl` e Postman) e **como depurar** quando
algo der errado. É escrito para alguém que vai mexer no projeto pela primeira vez.

> Projeto: `streamtube-java` — backend de uma plataforma de vídeos (estilo YouTube)
> em **Java 21 + Spring Boot + Clean Architecture**, porte do backend NestJS de
> referência (fases 01 a 03).

---

## 1. O que o sistema faz

StreamTube é o backend de uma plataforma de vídeos. As três fases implementadas:

- **Fase 01 — Base:** projeto Gradle multi-módulo (Clean Architecture), conexão com
  PostgreSQL via Flyway, endpoint de saúde e documentação OpenAPI/Swagger.
- **Fase 02 — Autenticação e Conta:** cadastro de usuário (com canal criado
  automaticamente), confirmação de e-mail, login com **JWT** (access token) +
  **refresh token rotativo** (com detecção de reuso e período de carência),
  recuperação de senha e endpoint "usuário atual".
- **Fase 03 — Vídeos:** upload de vídeos grandes **sem passar o arquivo pela API**
  (URL pré-assinada direto para o object storage), processamento automático em
  segundo plano (extração de duração/metadados com FFprobe + geração de thumbnail
  com FFmpeg) em um **worker** que consome uma **fila RabbitMQ**, URL única por
  vídeo (slug), e **streaming/download** via redirecionamento 302 para URL
  pré-assinada.

### Conceitos-chave

- **Usuário 1:1 Canal:** ao se cadastrar, o usuário ganha um canal automaticamente
  (com um nickname gerado). Todo vídeo pertence a um canal.
- **Ciclo de vida do vídeo:** `PENDING_UPLOAD → QUEUED → PROCESSING → READY | ERROR`.
- **Upload assíncrono:** a API só cria o registro do vídeo e devolve uma URL
  pré-assinada; o cliente (browser/curl) envia o arquivo direto para o MinIO. Isso
  permite arquivos de até 10GB sem travar a API.

---

## 2. Arquitetura

### Camadas (Clean Architecture)

```
domain         → entidades puras + regras + "ports" (sem framework)
   ↑
application    → casos de uso (orquestram os ports)
   ↑
infrastructure → adapters: JPA (Postgres), S3 (MinIO), RabbitMQ, e-mail, JWT, Argon2
   ↑
bootstrap-api      → aplicação web (controllers REST, segurança, main)
bootstrap-worker   → aplicação worker (consome a fila, roda FFmpeg)
```

A dependência aponta sempre para dentro: `web/worker → infrastructure → application → domain`.
O `domain` não conhece Spring, JPA nem HTTP.

### Serviços (Docker Compose)

| Serviço | Imagem | Porta no host | Para quê |
|---------|--------|---------------|----------|
| `api` | build local (`Dockerfile.api`) | `8080` | API REST |
| `worker` | build local (`Dockerfile.worker`, tem FFmpeg) | — | processa vídeos da fila |
| `db` | postgres:17 | `5432` | banco de dados |
| `rabbitmq` | rabbitmq:3-management | `5673` (AMQP), `15673` (UI) | fila de processamento |
| `minio` | minio/minio | `9000` (API S3), `9001` (console) | object storage |
| `minio-init` | minio/mc | — | cria o bucket `streamtube-videos` |
| `mailpit` | axllent/mailpit | `1025` (SMTP), `8025` (UI) | captura e-mails em dev |

> **Por que RabbitMQ em 5673 e não 5672?** Para conviver com outras instâncias de
> RabbitMQ que possam já existir na sua máquina. Dentro do Docker, os serviços
> conversam pelo nome (`rabbitmq:5672`); o `5673` é só o que é exposto ao host.

---

## 3. Pré-requisitos

- **Docker** + **Docker Compose** (para subir tudo).
- Para rodar os testes/builds localmente (opcional): **Java 21 (JDK)**. O Gradle vem
  embutido via *wrapper* (`./gradlew`), não precisa instalar Gradle.
- `curl` e `python3` (para os exemplos de teste via terminal). Postman é opcional.

> Não é necessário ter Java instalado para apenas **rodar** o sistema — o build
> acontece dentro do Docker. Java só é necessário para rodar `./gradlew` no host.

---

## 4. Subir o ambiente (passo a passo)

A partir da pasta `streamtube-java`:

```bash
# 1) Sobe e builda tudo (a primeira vez demora: baixa imagens e compila)
docker compose up -d --build

# 2) Confere que está tudo "healthy"/"running"
docker compose ps
```

Ordem de subida (o Compose cuida disso): `db`, `rabbitmq`, `minio` ficam *healthy* →
`minio-init` cria o bucket → `api` sobe e roda as **migrations Flyway** → `worker`
sobe **depois** da API (porque depende do schema criado por ela).

### Verificar que cada serviço respondeu

```bash
# API viva (espera 200 e um JSON)
curl http://localhost:8080/
# -> {"status":"ok","service":"streamtube-api"}

# Saúde detalhada (banco etc.)
curl http://localhost:8080/actuator/health
# -> {"status":"UP"}
```

Interfaces web úteis:

- **Swagger UI (documentação interativa da API):** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs
- **MinIO console:** http://localhost:9001 — usuário `streamtube`, senha `streamtube_secret`
- **RabbitMQ UI:** http://localhost:15673 — usuário `streamtube`, senha `streamtube`
- **Mailpit (e-mails):** http://localhost:8025

### Derrubar / reiniciar

```bash
docker compose stop        # para os containers (mantém dados)
docker compose start       # religa
docker compose down        # remove containers e rede (mantém imagens)
docker compose down -v     # remove TAMBÉM os volumes (apaga o banco e o storage!)
```

---

## 5. Endpoints

### Autenticação (`/auth`)

| Método | Caminho | Autenticação | O que faz |
|--------|---------|--------------|-----------|
| POST | `/api/v1/auth/register` | pública | Cria usuário + canal, envia e-mail de confirmação |
| GET | `/api/v1/auth/confirm-email?token=...` | pública | Confirma o e-mail |
| POST | `/api/v1/auth/resend-confirmation` | pública | Reenvia o e-mail de confirmação |
| POST | `/api/v1/auth/login` | pública | Devolve `access_token` + `refresh_token` |
| POST | `/api/v1/auth/refresh` | pública | Rotaciona o refresh token |
| POST | `/api/v1/auth/forgot-password` | pública | Envia e-mail de reset (sempre 204) |
| POST | `/api/v1/auth/reset-password` | pública | Troca a senha usando o token |
| POST | `/api/v1/auth/logout` | autenticada | Revoga o refresh token enviado |
| GET | `/api/v1/auth/me` | autenticada | Dados do usuário logado + canal |

### Vídeos (`/api/v1/videos`)

| Método | Caminho | Autenticação | O que faz |
|--------|---------|--------------|-----------|
| POST | `/api/v1/videos` | autenticada | Inicia upload — cria o vídeo e devolve a URL pré-assinada (PUT) |
| POST | `/api/v1/videos/{id}/complete-upload` | autenticada (dono) | Confirma o upload e enfileira o processamento |
| GET | `/api/v1/videos/{slug}` | pública | Informações do vídeo (inclui URL da thumbnail) |
| GET | `/api/v1/videos/{slug}/stream` | pública | Redireciona (302) para a URL de streaming (só se `READY`) |
| GET | `/api/v1/videos/{slug}/download` | pública | Redireciona (302) para download (só se `READY`) |

### CDN (Fase 10)

Perfil opt-in: com `CDN_ENABLED=true` (padrão do compose de dev), as URLs de leitura (stream,
download, thumbnails, segmentos HLS) apontam para o edge nginx em `http://localhost:8090` com
token de expiração e cache (`X-Cache-Status: HIT` na segunda busca). Uploads continuam indo
direto ao MinIO. Nenhum endpoint muda — só o host das URLs devolvidas.

### Streaming adaptativo HLS (Fase 09)

| Método | Caminho | Autenticação | O que faz |
|--------|---------|--------------|-----------|
| GET | `/api/v1/videos/{slug}/hls/master.m3u8` | pública | Playlist master (conta 1 view quando publicado); 404 se o vídeo não tem HLS |
| GET | `/api/v1/videos/{slug}/hls/{rendition}/playlist.m3u8` | pública | Playlist da qualidade com segmentos presignados (TTL 6h) |

O worker gera a escada (até 720p/480p/360p, sem upscale) no processamento. O `hlsUrl` no
Get info indica se o vídeo tem HLS; `null` = use o `/stream` progressivo (catálogo antigo).

### Upload multipart (Fase 08 — arquivos grandes / conexão ruim)

| Método | Caminho | Autenticação | O que faz |
|--------|---------|--------------|-----------|
| POST | `/api/v1/videos/multipart` | autenticada | Cria o vídeo e abre a sessão; devolve `partSizeBytes` (8 MiB) e `totalParts` |
| POST | `/api/v1/videos/{id}/parts` | autenticada (dono) | URLs presignadas das partes pedidas — re-emissíveis (retry) |
| GET | `/api/v1/videos/{id}/parts` | autenticada (dono) | Partes que já chegaram (resume após queda) |
| POST | `/api/v1/videos/{id}/complete-multipart` | autenticada (dono) | Servidor monta o objeto (você nunca lida com ETags), confere o tamanho e enfileira |
| DELETE | `/api/v1/videos/{id}/multipart` | autenticada (dono) | Aborta a sessão e descarta as partes |

### Home e busca (Fase 07)

| Método | Caminho | Autenticação | O que faz |
|--------|---------|--------------|-----------|
| GET | `/api/v1/videos?page&size&categoryId` | pública | Grid da home: publicados + PUBLIC, mais recentes primeiro; filtro opcional por categoria |
| GET | `/api/v1/search?q=&page&size` | pública | Busca por título do vídeo ou nome do canal (contains, sem ranking; `q` mín. 2 chars) |

Os dois devolvem o "card" da home: thumbnail, título, canal (id/name/nickname), views,
`publishedAt` e categoria.

### Interações sociais (Fase 06)

| Método | Caminho | Autenticação | O que faz |
|--------|---------|--------------|-----------|
| PUT / DELETE | `/api/v1/videos/{id}/reaction` | autenticada | Define/troca (`LIKE` \| `DISLIKE`) ou remove a minha reação (vídeo publicado) |
| POST | `/api/v1/videos/{id}/comments` | autenticada | Comenta; `parentId` opcional = resposta de 1 nível |
| GET | `/api/v1/videos/{slug}/comments` | pública | Comentários raiz, mais recentes primeiro |
| GET | `/api/v1/comments/{id}/replies` | pública | Respostas, mais antigas primeiro |
| DELETE | `/api/v1/comments/{id}` | autenticada (autor) | Apaga o comentário (respostas caem junto) |
| PUT / DELETE | `/api/v1/comments/{id}/reaction` | autenticada | Reação no comentário |
| PUT / DELETE | `/api/v1/channels/{nickname}/subscription` | autenticada | Inscreve/cancela (idempotente; o próprio canal dá 400) |
| GET | `/api/v1/subscriptions` | autenticada | Canais que sigo |
| GET | `/api/v1/subscriptions/videos` | autenticada | Feed: últimos vídeos publicados dos canais que sigo |

A lista completa (incl. edição de vídeo/canal, publish, thumbnails, categorias e páginas de
canal das Fases 04–05) está no Swagger (`http://localhost:8080/swagger-ui.html`) e na coleção
Postman (`docs/postman/`).

> Os campos de token no JSON usam *snake_case*: `access_token`, `refresh_token`,
> `token_type`, `expires_in` (para bater com o contrato do backend de referência).

---

## 6. Testando o fluxo completo (passo a passo, via `curl`)

Este é o "caminho feliz" de ponta a ponta: cadastrar → confirmar e-mail → logar →
enviar um vídeo → ver ele ficar pronto → tocar.

### 6.1. Cadastrar um usuário

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"teste@exemplo.com","password":"senha12345"}'
# -> 201 {"id":"...","email":"teste@exemplo.com"}
```

### 6.2. Pegar o token de confirmação no Mailpit

O e-mail de confirmação foi "enviado" para o Mailpit. Abra http://localhost:8025,
clique no e-mail e copie o link/token. Ou via terminal:

```bash
EMAIL="teste@exemplo.com"
MSGID=$(curl -s "http://localhost:8025/api/v1/search?query=to:$EMAIL" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['messages'][0]['ID'])")
TOKEN=$(curl -s "http://localhost:8025/api/v1/message/$MSGID/raw" \
  | python3 -c "import sys,re;r=sys.stdin.read().replace('=\r\n','').replace('=\n','').replace('=3D','=');import re;m=re.search(r'token=([A-Za-z0-9._-]+)',r);print(m.group(1))")
echo "TOKEN=$TOKEN"
```

### 6.3. Confirmar o e-mail

```bash
curl "http://localhost:8080/api/v1/auth/confirm-email?token=$TOKEN"
# -> 204 (sem corpo)
```

### 6.4. Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"teste@exemplo.com","password":"senha12345"}'
# -> 200 {"access_token":"eyJ...","token_type":"Bearer","expires_in":900,"refresh_token":"..."}
```

Guarde o `access_token`:

```bash
JWT="cole_aqui_o_access_token"
```

### 6.5. Ver o usuário logado (rota protegida)

```bash
curl http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer $JWT"
# -> 200 {"id":"...","email":"...","confirmed":true,"channel":{"id":"...","nickname":"...","name":"..."}}
```

### 6.6. Iniciar o upload de um vídeo

```bash
curl -X POST http://localhost:8080/videos \
  -H "Authorization: Bearer $JWT" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Meu primeiro vídeo"}'
# -> 201 {"id":"...","slug":"...","uploadUrl":"http://localhost:9000/streamtube-videos/videos/...?X-Amz-..."}
```

Guarde `id`, `slug` e `uploadUrl`.

### 6.7. Enviar o arquivo direto para o MinIO (URL pré-assinada)

Precisa de um arquivo de vídeo. Se não tiver, gere um de teste com FFmpeg (se tiver
instalado) ou use qualquer `.mp4`:

```bash
# (opcional) gerar um vídeo de teste de 3s com ffmpeg, se você tiver ffmpeg no host:
ffmpeg -f lavfi -i testsrc=duration=3:size=320x240:rate=15 -pix_fmt yuv420p -y meu.mp4

# enviar o arquivo para a uploadUrl devolvida no passo anterior:
curl -X PUT "COLE_A_uploadUrl_AQUI" --upload-file meu.mp4
# -> 200 (sem corpo)
```

> Não tem FFmpeg no host? Você pode gerar o vídeo dentro do container worker:
> `docker compose exec worker ffmpeg -f lavfi -i testsrc=duration=3:size=320x240:rate=15 -pix_fmt yuv420p -y /tmp/meu.mp4`
> e depois copiar: `docker cp $(docker compose ps -q worker):/tmp/meu.mp4 ./meu.mp4`

### 6.8. Confirmar o upload (dispara o processamento)

```bash
VID="cole_o_id_do_video"
curl -X POST "http://localhost:8080/videos/$VID/complete-upload" \
  -H "Authorization: Bearer $JWT"
# -> 204
```

Isso publica uma mensagem na fila RabbitMQ; o worker consome, roda FFprobe + FFmpeg,
salva a thumbnail no MinIO e marca o vídeo como `READY`.

### 6.9. Acompanhar o processamento

```bash
SLUG="cole_o_slug"
curl "http://localhost:8080/videos/$SLUG"
# status vai de QUEUED -> PROCESSING -> READY (normalmente em poucos segundos)
```

Ou olhando direto no banco:

```bash
docker compose exec db psql -U streamtube -d streamtube \
  -c "SELECT slug, status, duration_seconds, thumbnail_key FROM videos ORDER BY created_at DESC LIMIT 5;"
```

### 6.10. Tocar (streaming) e baixar

Quando estiver `READY`:

```bash
# 302 redirecionando para a URL pré-assinada de streaming
curl -i "http://localhost:8080/videos/$SLUG/stream"

# 302 para download (com Content-Disposition: attachment)
curl -i "http://localhost:8080/videos/$SLUG/download"

# seguir o redirect e baixar de fato:
curl -L "http://localhost:8080/videos/$SLUG/download" -o baixado.mp4
```

---

## 7. Testando com Postman

> **Atalho:** já existe uma coleção pronta em `docs/postman/`. No Postman,
> *Import* → arraste os dois arquivos:
> - `docs/postman/StreamTube.postman_collection.json` (requests + scripts que
>   capturam tokens automaticamente)
> - `docs/postman/StreamTube.postman_environment.json` (variáveis; selecione o
>   environment "StreamTube Local" no canto superior direito)
>
> Depois é só rodar, em ordem, **Auth → 1..4** (o Register gera um e-mail único, o
> "2. Pegar token" lê o Mailpit e salva o `confirmToken`, o Login salva os tokens) e
> então **Videos → 1..6** (no "2. Upload file", selecione o arquivo em Body → binary).
> Para ver o 302 em stream/download, desligue "Automatically follow redirects" em
> *Settings*.

Se preferir montar do zero a partir da especificação:

1. **Importe a OpenAPI:** no Postman, *Import* → *Link* →
   `http://localhost:8080/v3/api-docs`. Ele gera a coleção com todos os endpoints.
2. **Variáveis de ambiente:** crie um Environment com `baseUrl = http://localhost:8080`
   e (depois do login) `accessToken` e `refreshToken`.
3. **Fluxo sugerido:**
   - `POST {{baseUrl}}/api/v1/auth/register` (body JSON com `email` e `password`).
   - Pegue o token no Mailpit (http://localhost:8025) e chame
     `GET {{baseUrl}}/api/v1/auth/confirm-email?token=...`.
   - `POST {{baseUrl}}/api/v1/auth/login` → copie `access_token` para a variável `accessToken`.
   - Nas rotas protegidas, aba **Authorization** → tipo **Bearer Token** →
     `{{accessToken}}`.
   - `POST {{baseUrl}}/videos` → copie a `uploadUrl`.
   - **Upload:** crie um request `PUT` para a `uploadUrl`, aba **Body** → **binary** →
     selecione o arquivo de vídeo. (Não coloque header de Authorization aqui — a URL
     já é assinada.)
   - `POST {{baseUrl}}/videos/{id}/complete-upload`.
   - `GET {{baseUrl}}/videos/{slug}` até ver `status: READY`.
   - `GET {{baseUrl}}/videos/{slug}/stream` → o Postman seguirá o 302.

> Dica: nas rotas `/stream` e `/download`, desligue "Automatically follow redirects"
> nas *Settings* do Postman se quiser **ver** o 302 e o header `Location`.

---

## 8. Rodando os testes automatizados

Não precisa subir o Compose para os testes — eles usam **Testcontainers** (sobem
Postgres e MinIO temporários automaticamente; exigem Docker rodando).

```bash
# precisa de Java 21 no host; o Gradle vem pelo wrapper
export JAVA_HOME=/caminho/para/jdk-21   # ex.: ~/.sdkman/candidates/java/21.0.2-open
./gradlew build                          # compila + checa formatação + roda todos os testes
```

O que cada nível cobre:

- **Unit** (`*Test`): regras dos casos de uso (ex.: rotação/reuso do refresh token,
  processamento de vídeo) com tudo mockado.
- **Integração** (`*IntegrationTest`): adapter de storage contra **MinIO real**
  (faz um PUT/GET pré-assinado de verdade), contexto contra **Postgres real**.
- **E2E** (`*E2ETest`): ciclo HTTP completo (auth e vídeos) com **Postgres real**.

Rodar um teste específico:

```bash
./gradlew :bootstrap-api:test --tests "*AuthE2ETest"
```

Relatórios HTML ficam em `*/build/reports/tests/test/index.html`.

---

## 9. Como depurar (quando der ruim)

### 9.1. Ver logs

```bash
docker compose ps                 # quem está de pé / saudável
docker compose logs api           # logs da API
docker compose logs worker        # logs do worker (processamento)
docker compose logs -f worker     # seguir em tempo real
docker compose logs db            # banco
```

### 9.2. Inspecionar o banco

```bash
docker compose exec db psql -U streamtube -d streamtube

# dentro do psql:
\dt                                  -- lista tabelas
SELECT * FROM flyway_schema_history; -- migrations aplicadas
SELECT email, is_confirmed FROM users;
SELECT slug, status, error_message FROM videos ORDER BY created_at DESC;
```

### 9.3. Inspecionar a fila (RabbitMQ)

Abra http://localhost:15673 (user/senha `streamtube`/`streamtube`). Veja as filas
`video.processing` e a DLQ `video.processing.dlq`. Se mensagens estão indo para a
**DLQ**, o processamento está falhando (veja os logs do worker).

### 9.4. Inspecionar o storage (MinIO)

Abra http://localhost:9001 (`streamtube` / `streamtube_secret`). No bucket
`streamtube-videos` você verá `videos/<slug>` (o arquivo enviado) e
`thumbnails/<slug>.jpg` (gerado pelo worker).

### 9.5. Ver os e-mails

Todos os e-mails (confirmação, reset de senha) caem no Mailpit:
http://localhost:8025.

### 9.6. Problemas comuns e soluções

| Sintoma | Causa provável | Solução |
|---------|----------------|---------|
| `docker compose up` falha numa porta (`port is already allocated`) | Outra coisa usando 5432/9000/8080/etc. na sua máquina | Pare o serviço conflitante, ou ajuste a porta no `compose.yaml`. (O RabbitMQ já usa 5673 no host para evitar conflito.) |
| `initdb: ... No space left on device` | Disco do Docker cheio | `docker image prune -a` e `docker builder prune` (seguros). **Evite** `docker volume prune` sem ter certeza — apaga dados. |
| Vídeo fica preso em `PENDING_UPLOAD` | O `PUT` para a `uploadUrl` não foi feito (ou falhou) | Refaça o passo 6.7; confira que a `uploadUrl` é a que veio na resposta e não expirou (vale 15 min). |
| `complete-upload` retorna 409 | O arquivo não está no storage | Faça o `PUT` (passo 6.7) antes do complete-upload. |
| Vídeo vai para `ERROR` | FFmpeg/FFprobe falhou no worker | `docker compose logs worker`; confira se o arquivo é um vídeo válido. |
| `/stream` ou `/download` retorna 422 | Vídeo ainda não está `READY` | Espere o processamento terminar. |
| 401 numa rota protegida | Sem header `Authorization: Bearer <token>` ou token expirado (15 min) | Faça login de novo ou use `/api/v1/auth/refresh`. |
| `SignatureDoesNotMatch` ao dar `PUT` no MinIO | URL pré-assinada inconsistente (host/credenciais) | Use exatamente a `uploadUrl` da resposta; confira que `STORAGE_PUBLIC_URL` aponta para `http://localhost:9000`. |
| `./gradlew` reclama de `JAVA_HOME` inválido | `JAVA_HOME` apontando para um JDK que não existe | `export JAVA_HOME=<caminho de um JDK 21 válido>` |
| Worker reinicia/cai no boot | Subiu antes de a API rodar as migrations | Já tratado: o worker `depends_on` a API saudável. Se rodar o worker isolado, garanta que o schema existe. |

### 9.7. "Resetar" tudo

```bash
docker compose down -v        # apaga containers + volumes (banco e storage zerados)
docker compose up -d --build  # sobe do zero
```

---

## 10. Estrutura de pastas (resumo)

```
streamtube-java/
├── docs/
│   ├── decisions/technical-decisions-springboot-backend.md   # decisões técnicas (research)
│   ├── phases/phase-0X-*/                                     # context, plan, validation, progress
│   └── GUIA-DE-USO.md                                         # este arquivo
├── domain/            # entidades + ports (sem framework)
├── application/       # casos de uso
├── infrastructure/    # adapters (JPA, S3, RabbitMQ, e-mail, JWT, Argon2)
├── bootstrap-api/     # app web (controllers, segurança, migrations Flyway)
├── bootstrap-worker/  # app worker (listener da fila + FFmpeg)
├── compose.yaml       # todos os serviços
├── Dockerfile.api / Dockerfile.worker
└── build.gradle.kts, settings.gradle.kts, gradle/
```

---

## 11. Convenções do projeto (Git Flow)

- Branches: `main` (estável) ← `dev` (integração) ← `feature/*`.
- Cada fase foi desenvolvida numa `feature/phase-0X-*` a partir da `dev`.
- "Pronto" (Definition of Done) por fase: `./gradlew build` verde (compila +
  formatação + testes). A Fase 03 também foi validada com o smoke test do stack real
  descrito na seção 6.

---

## 12. Onde olhar primeiro se for mexer no código

- **Adicionar/alterar um endpoint:** `bootstrap-api/.../web/*Controller.java` +
  o caso de uso correspondente em `application/.../*UseCase.java`.
- **Regra de negócio:** `application` (casos de uso) e `domain` (entidades).
- **Banco / nova tabela:** crie uma migration `V5__...sql` em
  `bootstrap-api/src/main/resources/db/migration/` e a entidade/adapter em
  `infrastructure/.../persistence/`.
- **Processamento de vídeo:** `bootstrap-worker/.../ffmpeg/FfmpegVideoAnalyzer.java`
  e o caso de uso `application/.../video/ProcessVideoUseCase.java`.
- **Segurança/JWT:** `bootstrap-api/.../config/SecurityConfig.java` e
  `infrastructure/.../security/`.
