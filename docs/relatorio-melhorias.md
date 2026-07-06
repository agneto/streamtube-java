# Relatório de Melhorias — StreamTube (Java)

**Data:** 2026-07-04
**Escopo analisado:** todos os módulos (`domain`, `application`, `infrastructure`, `bootstrap-api`, `bootstrap-worker`), configuração, Docker, migrações e testes.

## Status da implementação (atualizado em 2026-07-06)

Os **12 itens do roadmap foram implementados e mergeados em `dev`** (PRs #7 a #25). Cada achado abaixo traz seu status individual; a tabela do roadmap (seção 7) referencia os PRs. Pendências remanescentes são todas 🟢/🟡 de baixa prioridade: 1.6, 1.8, 2.5, 2.6, 4.3, 5.3 (parcial), 6.2–6.4.

## Avaliação geral

O projeto está bem acima da média: Clean Architecture aplicada com disciplina real (domínio livre de framework, ports/adapters consistentes), rotação de refresh token com detecção de reuso, tokens armazenados como hash, Argon2, Flyway, DLQ no RabbitMQ, dois presigners S3 para resolver o problema de assinatura de host — tudo com javadoc explicando o *porquê*. As melhorias abaixo são, em sua maioria, sobre **prontidão para produção** (transações, resiliência, observabilidade, CI), não sobre defeitos estruturais.

Prioridade: 🔴 alta (corrigir antes de produção) · 🟡 média · 🟢 baixa (qualidade/evolução).

---

## 1. Segurança

### 🔴 1.1 Reset de senha não revoga sessões existentes

> **Status:** ✅ Resolvido (PR #7)

`ResetPasswordUseCase` troca a senha e consome o token, mas **não revoga os refresh tokens do usuário**. Se a conta foi comprometida (motivo típico de um reset), o atacante continua com sessão válida por até 30 dias.

**Correção:** chamar `refreshTokenRepository.revokeAllByUserId(...)` (ou revogar todas as famílias) dentro da mesma transação do reset. Considere o mesmo em `ConfirmEmailUseCase` se houver fluxo de troca de e-mail no futuro.

### 🔴 1.2 Rate limiting quebra atrás de proxy e tem memória sem limite

> **Status:** ✅ Resolvido (PR #15)

`RateLimitingFilter`:
- Usa `request.getRemoteAddr()`. Atrás de um load balancer/reverse proxy, todos os clientes compartilham o IP do proxy — um único usuário abusivo bloqueia todo mundo (e o limite real fica global, não por IP). É preciso honrar `X-Forwarded-For` **de forma segura** (via `ForwardedHeaderFilter` + configuração do proxy confiável, nunca lendo o header cru).
- O `ConcurrentHashMap<String, Bucket>` cresce indefinidamente (uma entrada por IP×rota, para sempre) — vetor de exaustão de memória. Trocar por cache com expiração (Caffeine `expireAfterAccess`) ou Bucket4j com store distribuído (Redis) se houver mais de uma instância da API.
- `/auth/reset-password` e `/auth/confirm-email` **não estão** em `LIMITED_PATHS`, permitindo força bruta contra tokens (mitigado pela alta entropia, mas barato de fechar).
- A resposta 429 não envia `Retry-After` e o JSON é montado à mão, divergindo do `ErrorEnvelope`.

### 🔴 1.3 Segredo JWT com fallback inseguro embutido

> **Status:** ✅ Resolvido (PR #13)

`application.yml` define `JWT_SECRET` com default `dev-only-insecure-secret-change-me-...`. Se a variável não for setada em produção, a aplicação **sobe silenciosamente com segredo público** (está no Git). O mesmo vale para credenciais de storage/DB.

**Correção:** criar perfil `prod` sem defaults (ou validar no startup — ex.: `@ConfigurationProperties` com validação que rejeita o valor dev fora do perfil `dev`/`local`). Falhar rápido é o comportamento correto aqui.

### 🟡 1.4 Sem configuração de CORS

> **Status:** ✅ Resolvido (PR #22)

Não há nenhuma configuração de CORS no projeto. Qualquer frontend em navegador (o caso de uso natural de uma plataforma de vídeos) vai falhar no preflight. Adicionar `CorsConfigurationSource` com origens configuráveis por ambiente.

### 🟡 1.5 Upload presignado sem restrições de tamanho/tipo

> **Status:** ✅ Resolvido (PR #22)

`S3StorageAdapter.presignUpload` assina um PUT sem `Content-Length` nem `Content-Type` na assinatura. Um usuário autenticado pode subir um arquivo de qualquer tamanho (custo de storage/egress) e de qualquer tipo. Incluir `contentLength`/`contentType` esperados na assinatura (o S3 rejeita se divergirem) e validar limites no `InitiateUploadUseCase` (o cliente informaria tamanho/tipo no request).

### 🟡 1.6 Registro tem condição de corrida no e-mail duplicado

> **Status:** ⏳ Pendente

`RegisterUserUseCase` faz `existsByEmail` + `save`. Dois registros simultâneos do mesmo e-mail passam do check e um deles estoura a constraint UNIQUE como `DataIntegrityViolationException` → **500** em vez de 409. Capturar a violação de constraint e traduzir para `EmailAlreadyRegisteredException` (mesmo padrão para nickname e slug).

### 🟢 1.7 Containers rodam como root

> **Status:** ✅ Resolvido (PR #25)

`Dockerfile.api`/`Dockerfile.worker` não declaram `USER`. Adicionar usuário não-root no estágio de runtime (`useradd`/`adduser` + `USER app`).

### 🟢 1.8 Falha de verificação JWT é silenciosa

> **Status:** ⏳ Pendente

`JwtTokenService.verify` engole qualquer `RuntimeException` sem log. Um `debug`/`trace` do motivo (expirado vs. assinatura inválida) ajuda muito em investigação de incidentes sem vazar informação ao cliente.

---

## 2. Confiabilidade e consistência

### 🔴 2.1 Transação aberta durante todo o processamento de vídeo

> **Status:** ✅ Resolvido (PR #11)

`ProcessVideoUseCase.execute` é um único `@Transactional` que engloba `ffprobe` + extração de thumbnail + upload S3 — processos externos com timeout de **até 120s cada**. Consequências:
- Uma conexão do pool de DB fica presa por minutos por vídeo; com poucos vídeos simultâneos o pool esgota.
- O `markProcessing` + `save` intermediário **nunca fica visível** (só comita no fim), então o status `PROCESSING` é inatingível na prática.
- Se o worker morrer no meio, o vídeo volta a `QUEUED` — mas a checagem de idempotência só cobre `isReady()`.

**Correção:** dividir em três transações curtas — (1) marcar `PROCESSING` e comitar; (2) ffprobe/thumbnail/S3 **fora** de transação; (3) marcar `READY`/`ERROR` e comitar. Com `TransactionTemplate` ou métodos `@Transactional` separados chamados de um orquestrador não-transacional.

### 🔴 2.2 Mensagem publicada no Rabbit antes do commit da transação

> **Status:** ✅ Resolvido (PR #9)

Em `CompleteUploadUseCase`, `publisher.publish(video.id())` roda **dentro** do `@Transactional`. Dois problemas:
- Se o commit falhar depois do publish, o worker recebe um job para um vídeo que continua `PENDING_UPLOAD` (e falha).
- O worker pode consumir a mensagem **antes** do commit e não enxergar o status `QUEUED` (corrida real, já que o consumo é em outro processo).

**Correção:** publicar após o commit — `TransactionSynchronizationManager.registerSynchronization(afterCommit)`, `@TransactionalEventListener(AFTER_COMMIT)` ou, para garantia forte, padrão *transactional outbox*.

### 🔴 2.3 Envio de e-mail dentro da transação de registro

> **Status:** ✅ Resolvido (PR #9)

`RegisterUserUseCase` chama `mailSender.sendConfirmationEmail(...)` dentro do `@Transactional`. Um SMTP lento segura a transação (conexão de pool presa) e uma falha de SMTP **desfaz o registro inteiro** — o usuário recebe erro e não sabe se a conta existe. O mesmo padrão está em `ForgotPasswordUseCase` e `ResendConfirmationUseCase`.

**Correção:** enviar após o commit e de preferência assíncrono (`@Async` + `AFTER_COMMIT`, ou fila). Falha de e-mail vira retry/log, não rollback.

### 🟡 2.4 Sem handler catch-all de exceções

> **Status:** ✅ Resolvido (PR #18)

`GlobalExceptionHandler` só trata `DomainException` e `MethodArgumentNotValidException`. `IllegalStateException` (usada em `InitiateUploadUseCase`, `RegisterUserUseCase`), `HttpMessageNotReadableException` (JSON malformado → 400 fora do envelope), `MethodArgumentTypeMismatchException` (UUID inválido no path) e erros inesperados caem no formato padrão do Spring — contrato de erro inconsistente e risco de vazar detalhes internos. Adicionar handlers para esses casos + um `@ExceptionHandler(Exception.class)` que loga com stack trace e devolve 500 no `ErrorEnvelope`.

### 🟡 2.5 Mapeamento código→status é frágil

> **Status:** ⏳ Pendente

O `switch` de strings em `GlobalExceptionHandler.statusFor` exige lembrar de atualizar o handler a cada nova exceção de domínio — esquecido, vira 400 genérico silenciosamente. Alternativa: `DomainException` expõe um enum/categoria (`NOT_FOUND`, `CONFLICT`, `FORBIDDEN`...) que o handler traduz mecanicamente, mantendo o domínio sem dependência de HTTP.

### 🟡 2.6 DLQ sem tratamento de falha do próprio handler

> **Status:** ⏳ Pendente

Se `processVideo.markFailed` lançar exceção no listener da DLQ, com `default-requeue-rejected: false` e sem DLX na DLQ a mensagem é **descartada** e o vídeo fica preso em `QUEUED`/`PROCESSING` para sempre. Vale: (a) capturar exceções no handler da DLQ e logar com alerta; (b) um job de reconciliação que marca como `ERROR` vídeos presos em estado intermediário há mais de X horas.

### 🟢 2.7 Idempotência do worker incompleta

> **Status:** 🟡 Parcial (redelivery de PROCESSING coberto no PR #11; política para ERROR segue indefinida)

`ProcessVideoUseCase.execute` retorna cedo se `isReady()`, mas em redelivery de um vídeo já `ERROR` ele reprocessa. Decidir explicitamente se `ERROR` deve ser reprocessável em redelivery e codificar isso.

---

## 3. Observabilidade e operação

### 🔴 3.1 Sem pipeline de CI

> **Status:** ✅ Resolvido (PR #17)

Não há `.github/workflows` (nem outro CI). Com Testcontainers já no projeto, um workflow de `./gradlew build` + testes em PR é barato e evita regressão nos merges para `dev`/`main`. Incluir `spotlessCheck` no mesmo pipeline.

### 🟡 3.2 Sem métricas nem tracing

> **Status:** ✅ Resolvido (PR #24)

Actuator expõe só `health,info`. Para um sistema com fila + worker + storage, o mínimo útil: `micrometer-registry-prometheus` (+ expor `/actuator/prometheus`), métricas de fila (profundidade da DLQ é o alerta mais valioso do sistema) e, idealmente, Micrometer Tracing para correlacionar request → mensagem → processamento. Logs hoje são texto puro; formato JSON estruturado (logstash-encoder) facilita agregação.

### 🟡 3.3 Worker sem health check

> **Status:** ✅ Resolvido (PR #24)

O container `worker` no compose não tem `healthcheck` (a API tem). Adicionar actuator ao `bootstrap-worker` com o health group de liveness/readiness — importante quando isso for para um orquestrador real.

### 🟢 3.4 Dockerfiles sem cache de dependências e sem tuning de JVM

> **Status:** ✅ Resolvido (PR #25)

- `COPY . .` seguido de `gradle bootJar` invalida o cache a cada mudança de código e **rebaixa todas as dependências**. Copiar primeiro `settings.gradle.kts`, `build.gradle.kts`, `gradle/` e os `build.gradle.kts` dos módulos, rodar `gradle dependencies`, e só então copiar o código. Melhor ainda: usar layered jars do Spring Boot (`java -Djarmode=layertools -jar app.jar extract`).
- Usar o wrapper do projeto (`./gradlew`) no build em vez da imagem `gradle:8.10.2` evita divergência de versão.
- Runtime sem flags de container (`-XX:MaxRAMPercentage=75`) — o default pode subdimensionar o heap.

---

## 4. Dependências e build

### 🟡 4.1 Spring Boot 3.3.x está fora de suporte OSS

> **Status:** ✅ Resolvido (PR #21 — Spring Boot 3.5.3)

Spring Boot 3.3 saiu de suporte open-source em meados de 2025. Hoje (jul/2026) o caminho é **3.5.x** (mudança pequena vinda de 3.3) — traz patches de segurança do Spring Security 6.x e Hibernate mais novos. Atualizar também `springdoc` para a linha compatível.

### 🟢 4.2 Ferramentas de qualidade ausentes no build

> **Status:** 🟡 Parcial (ArchUnit e JaCoCo no PR #25; ErrorProne/SpotBugs e googleJavaFormat pendentes)

Spotless só formata (imports/whitespace). Sugestões de baixo custo e alto retorno:
- **JaCoCo** com relatório agregado — hoje não há visibilidade de cobertura.
- **ArchUnit** — as regras de camada (domain sem framework, application sem web/persistence) estão só em comentários; um teste ArchUnit as torna executáveis.
- **ErrorProne** ou SpotBugs para análise estática.
- Spotless com `googleJavaFormat()` (o código já segue esse estilo; hoje nada o garante).

### 🟢 4.3 Wrapper Gradle desatualizado

> **Status:** ⏳ Pendente (wrapper segue em 8.10.2)

Wrapper em 8.10.2 (out/2024). Atualizar para a linha 8.x atual melhora o suporte a Java 21+ e cache de configuração (`org.gradle.configuration-cache=true` em `gradle.properties`, hoje ausente).

---

## 5. Testes

### 🟡 5.1 Cobertura desigual de use cases

> **Status:** ✅ Resolvido (PR #19)

Só 4 dos ~18 use cases têm teste unitário (`RefreshTokens`, `ProcessVideo`, `RenameVideo`, `UpdateChannelDescription`). Ficaram de fora justamente os fluxos com mais ramificações de segurança: `LoginUseCase` (senha errada, e-mail não confirmado), `RegisterUserUseCase` (duplicado, retry de nickname), `ResetPasswordUseCase` (token consumido/expirado), `CompleteUploadUseCase` (dono errado, status errado, objeto ausente). Os E2E cobrem o caminho feliz, mas os testes unitários de branch são mais rápidos e precisos para esses casos.

### 🟡 5.2 Zero testes no worker

> **Status:** ✅ Resolvido (PR #19)

`FfmpegVideoAnalyzer` (parsing do ffprobe, timeout, exit code ≠ 0) e `VideoProcessingListener` não têm teste algum. O parsing do JSON do ffprobe é testável sem ffmpeg instalado (injetar o runner de processo ou testar `probe` parsing separado).

### 🟢 5.3 Filtros e handler sem testes

> **Status:** 🟡 Parcial (RateLimitingFilter no PR #15 e GlobalExceptionHandler no PR #18; JwtAuthenticationFilter pendente)

`RateLimitingFilter` (estourar o limite → 429), `JwtAuthenticationFilter` (token inválido/expirado) e `GlobalExceptionHandler` (mapa código→status) são pequenos e críticos — bons candidatos a testes de slice (`@WebMvcTest`).

---

## 6. API e modelo de dados

### 🟡 6.1 Contrato de erro inconsistente entre camadas

> **Status:** ✅ Resolvido (PRs #15 e #18)

Três formatos de erro convivem: `ErrorEnvelope` (handler), corpo vazio no 401 (`HttpStatusEntryPoint`) e JSON manual no 429 (filtro). Unificar: um `AuthenticationEntryPoint` custom que escreve o `ErrorEnvelope`, e o filtro de rate limit serializando o mesmo record via `ObjectMapper`.

### 🟢 6.2 Sem versionamento de API

> **Status:** ⏳ Pendente

Endpoints em `/auth`, `/videos` sem prefixo de versão. Adotar `/api/v1/...` agora é barato; depois de ter clientes, não é.

### 🟢 6.3 `metadata` como `text` em vez de `jsonb`

> **Status:** ⏳ Pendente

`videos.metadata` guarda o JSON do ffprobe como `text`. Em PostgreSQL, `jsonb` permite consultas futuras (codec, resolução) e valida o JSON na escrita.

### 🟢 6.4 Índices para consultas de tokens

> **Status:** ⏳ Pendente

`verification_tokens.token_hash` tem índice não-único, mas o lookup é sempre `hash + type` — um índice composto `(token_hash, type)` (ou UNIQUE em `token_hash`) casa melhor com a query. `refresh_tokens` está correto (UNIQUE em `token_hash`).

---

## 7. Roadmap sugerido

| Ordem | Item | Refs | Esforço | Status |
|-------|------|------|---------|--------|
| 1 | Revogar refresh tokens no reset de senha | 1.1 | Baixo | ✅ PR #7 |
| 2 | Publicar no Rabbit e enviar e-mails após o commit | 2.2, 2.3 | Médio | ✅ PR #9 |
| 3 | Quebrar a transação do processamento de vídeo | 2.1 | Médio | ✅ PR #11 |
| 4 | Perfil `prod` sem segredos default (fail-fast) | 1.3 | Baixo | ✅ PR #13 |
| 5 | Rate limit: proxy-aware + eviction + rotas faltantes | 1.2 | Médio | ✅ PR #15 |
| 6 | CI (build + testes + spotlessCheck) | 3.1 | Baixo | ✅ PR #17 |
| 7 | Catch-all no exception handler + contrato de erro único | 2.4, 6.1 | Baixo | ✅ PR #18 |
| 8 | Upgrade Spring Boot 3.5.x | 4.1 | Médio | ✅ PR #21 |
| 9 | Testes unitários dos use cases de auth/upload + worker | 5.1, 5.2 | Médio | ✅ PR #19 |
| 10 | Limites no upload presignado + CORS | 1.5, 1.4 | Médio | ✅ PR #22 |
| 11 | Métricas/observabilidade + healthcheck do worker | 3.2, 3.3 | Médio | ✅ PR #24 |
| 12 | Docker (cache de camadas, non-root, JVM flags), ArchUnit, JaCoCo | 3.4, 4.2, 1.7 | Baixo | ✅ PR #25 |

---

*Relatório gerado por análise estática do código em 2026-07-04 (branch `dev`, commit `87f9f11`). Nenhuma alteração de código foi feita.*
