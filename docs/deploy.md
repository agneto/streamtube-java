# Deploy em produção

Como levar o StreamTube para produção com a própria stack compose. A escolha do host (VPS,
ECS, k8s...) fica com você — este guia cobre o que o **backend exige** de qualquer ambiente.

## 1. Subir

```bash
docker compose -f compose.yaml -f compose.prod.yaml up -d --build
```

O `compose.prod.yaml` (requer docker compose ≥ 2.24 pelos merges `!reset`/`!override`):

- **Falha rápido sem segredos**: toda variável sensível usa `${VAR:?...}` — subir sem
  `JWT_SECRET` aborta na hora, em vez de rodar com o default de desenvolvimento.
- **Só API (8080) e storage (9000) expostos.** Postgres, RabbitMQ e o console do MinIO ficam
  internos à rede do compose. Termine TLS num proxy (nginx/caddy/traefik/ALB) na frente do 8080
  **e** do 9000 (as URLs pré-assinadas apontam para `STORAGE_PUBLIC_URL`).
- **Mailpit não sobe** (perfil `dev-only`): `MAIL_*` deve apontar para um SMTP real.
- **Volumes nomeados** (`pgdata`, `miniodata`): dados sobrevivem a `up`/`down`
  (não use `down -v` em produção).
- `restart: unless-stopped` em todos os serviços.

## 2. Variáveis obrigatórias

| Variável | Papel |
|----------|-------|
| `JWT_SECRET` | Assinatura dos tokens (>= 32 bytes aleatórios; `openssl rand -base64 48`) |
| `DB_PASSWORD` | Senha do Postgres (API, worker e o próprio container do banco) |
| `RABBITMQ_PASSWORD` | Senha do RabbitMQ |
| `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | Credenciais do MinIO/S3 |
| `STORAGE_PUBLIC_URL` | Host **público** do storage — vai embutido nas URLs pré-assinadas |
| `CORS_ALLOWED_ORIGINS` | Origem(ns) do frontend, separadas por vírgula |
| `MAIL_HOST` / `MAIL_FROM` (e `MAIL_PORT`, default 587) | SMTP real para confirmação/reset |
| `APP_BASE_URL` | Base dos links de e-mail (confirmação/reset) |

Opcionais com default sensato: `JWT_ACCESS_TTL_SECONDS` (900), `UPLOAD_MAX_SIZE_BYTES` (2 GiB),
`THUMB_MAX_SIZE_BYTES` (5 MiB). Coloque tudo num `.env` ao lado do compose (fora do git) ou no
secret manager do host.

## 3. Migrações e ordem de subida

**A API é dona das migrações Flyway** (V1..V9); o worker valida o schema mas não migra. O
compose já ordena: o worker só sobe depois da API estar `healthy`, então o schema sempre existe
antes. Em upgrades: `git pull && docker compose -f compose.yaml -f compose.prod.yaml up -d
--build` — a API aplica as novas migrações no boot (são todas aditivas).

## 4. Escala e observabilidade

- **API é stateless** (JWT, sem sessão): escala horizontal com réplicas atrás do proxy. O rate
  limiting de auth é por instância (in-memory) — com N réplicas o limite efetivo é N×10/min/IP;
  para limite global, mova para o proxy.
- **Worker escala pela fila**: mais réplicas consumindo `video.processing` = mais vídeos
  processados em paralelo (cada mensagem é de um vídeo; o processamento é idempotente).
  Monitore a DLQ `video.processing.dlq` — mensagem lá = vídeo em `ERROR` para investigar.
- `GET /actuator/health` (API 8080, worker 8081, interno) para liveness;
  `GET /actuator/prometheus` para métricas — mantenha-o acessível só pela rede interna.
- Os bytes de vídeo **nunca passam pela API** (upload e playback vão direto ao storage), então o
  dimensionamento da API é por JSON/Postgres, não por tráfego de vídeo.

## 5. Transcodificação HLS (Fase 09) — o que muda na operação

- **PROCESSING demora mais**: além de ffprobe + thumbnail, o worker transcodifica a escada HLS
  (até 3 qualidades). Vídeos longos podem levar dezenas de minutos.
- **Ack timeout do RabbitMQ**: o broker derruba entregas não confirmadas após
  `consumer_timeout` (default 30 min) — um transcode mais longo que isso vira redelivery em
  loop. Para catálogos com vídeos longos, aumente (`consumer_timeout` no rabbitmq.conf) ou
  escale a régua de bitrate/preset.
- **Disco do worker**: a escada é montada em disco local antes do upload — reserve ~2–3× o
  tamanho do maior vídeo esperado em `/tmp` do container.
- **Catálogo antigo**: vídeos processados antes da fase 09 não têm HLS (`hlsUrl: null`) e tocam
  pelo `/stream` progressivo. Para gerar a escada retroativamente, reenfileire o vídeo
  manualmente (publique `{videoId}` na fila `video.processing` — o processamento é idempotente e
  regrava thumbnail/metadata/HLS).

## 6. Ciclo de vida: deleção e órfãos (Fase 11)

O worker roda um **sweeper** agendado (`CLEANUP_INTERVAL_CRON`, default a cada 15 min) que:

- **Drena a fila `storage_cleanups`**: cada `DELETE /videos/{id}` apaga a linha na hora e
  enfileira os prefixos de storage (original, thumbnails, escada HLS) na mesma transação; o
  sweeper remove os objetos e só então tira a entrada da fila — falha de storage fica na fila e
  tenta de novo no próximo tick (at-least-once; apagar chave inexistente é no-op).
- **Aposenta rascunhos abandonados**: `PENDING_UPLOAD` mais velho que
  `CLEANUP_STALE_UPLOAD_DAYS` (default 7) tem a sessão multipart abortada, os prefixos
  enfileirados e a linha removida.

Com múltiplos workers o sweep pode rodar em dobro — inofensivo, tudo é idempotente. Fila
crescendo (`SELECT count(*) FROM storage_cleanups`) = storage rejeitando deleções; investigue.
Reconciliação total bucket↔banco (objetos sem registro por causas externas) permanece um
procedimento manual: liste os prefixos de primeiro nível e confira os slugs contra `videos`.

## 7. Uploads multipart abandonados (lifecycle do bucket)

Uma sessão multipart iniciada e abandonada segura bytes **invisíveis** no bucket (as partes não
aparecem como objetos). Configure a regra de lifecycle que aborta uploads incompletos:

```bash
# MinIO
mc ilm rule add local/streamtube-videos --expire-abort-incomplete-multipart-days 7
# AWS S3: regra de lifecycle "AbortIncompleteMultipartUpload" com DaysAfterInitiation = 7
```

Mesma filosofia dos rascunhos `PENDING_UPLOAD` órfãos (system-design §3.3): limpeza é tarefa de
infraestrutura, não um cron dentro da aplicação.

## 8. CDN para leitura (Fase 10)

Com `CDN_ENABLED=true` (+ `CDN_BASE_URL` e `CDN_SECRET` — sem eles a API aborta no boot), todas
as URLs públicas de leitura (stream, download, thumbnails, segmentos HLS) apontam para o edge com
token `secure_link`; uploads e leituras do worker seguem no storage.

- **CDN gerenciada (recomendado em produção):** o token do signer é o formato `secure_link` —
  compatível com token-auth de BunnyCDN/KeyCDN (confira a ordem de concatenação) ou substituível
  por CloudFront signed URLs trocando só o signer. Proteja o origin com OAC (CloudFront) ou
  allowlist de IP do provedor.
- **Edge embutido (nginx):** `docker compose ... --profile edge up -d` sobe o serviço `cdn`
  (porta 8090) com validação de token (403 adulterado / 410 expirado) e cache (`X-Cache-Status`).
  Termine TLS na frente dele como nos demais.
- **Trade-off do origin:** o bucket fica com leitura anônima e a proteção real é a combinação
  chaves não adivinháveis (slugs de 16 chars — mesma classe dos links UNLISTED) + rede: em
  produção, restrinja o acesso de leitura ao 9000 para o edge/CDN. O token protege o caminho
  público e expira; não é o único portão.
- **Purge:** desnecessário por desenho — vídeos são imutáveis após READY e a thumbnail custom
  troca de chave (`-custom`), então nunca há conteúdo velho com a mesma URL.

## 9. Checklist antes do primeiro deploy

- [ ] `.env` com todos os segredos (tabela acima) — `docker compose ... config` valida sem subir
- [ ] TLS na frente de 8080 e 9000; `STORAGE_PUBLIC_URL`/`APP_BASE_URL` com os hosts públicos
- [ ] `CORS_ALLOWED_ORIGINS` com a origem exata do frontend (esquema + host + porta)
- [ ] SMTP real testado (registro manda e-mail de confirmação — sem ele ninguém loga)
- [ ] Backup do volume `pgdata` agendado (o storage pode ser recriado; o banco não)
- [ ] `STREAMTUBE_VERSION` fixado numa tag publicada (ex.: `1.4.0`), não `latest`, para deploy
      reproduzível — ver §10
- [ ] Smoke pós-deploy: register → confirm → login → upload → publish → stream (o
      `docs/GUIA-DE-USO.md` §6 tem o passo a passo em curl)

## 10. CI/CD e imagens publicadas (Fase 12)

O pipeline (`.github/workflows/ci.yml`) roda em todo push e PR:

- **`verify`** — `spotlessCheck` + `./gradlew build` completo (unit + integração + Testcontainers
  E2E + ArchUnit; o runner do GitHub tem Docker, então o E2E roda de verdade). Resultados dos
  testes aparecem anotados no PR.
- **`images`** — constrói `Dockerfile.api` e `Dockerfile.worker` em **todo** run (valida que as
  imagens ainda buildam). **Publica** no GHCR só em push na `main` e em tags `v*`; PRs (inclusive
  de forks) nunca autenticam no registry.
- **`release`** — só em tag `v*`, depois de `verify` e `images` verdes: cria o GitHub Release com
  notas geradas + as coordenadas das imagens.

`codeql.yml` (análise estática Java, semanal + push/PR), `dependabot.yml` (Gradle + Actions,
semanal) e `dependency-submission.yml` (grafo de dependências na `main`) completam o supply-chain.

### Imagens

| Imagem | Coordenada |
|--------|-----------|
| API | `ghcr.io/agneto/streamtube-api` |
| Worker | `ghcr.io/agneto/streamtube-worker` |

Tags: em push na `main` → `latest` e `sha-<curto>`; em tag `vX.Y.Z` → `X.Y.Z`, `X.Y` e `latest`.
Consuma via `STREAMTUBE_VERSION` no `compose.prod.yaml` (§1) — pinne numa versão em produção.

### Como um release publica as imagens

O ritual de release não muda (branch → bump da `version` → tag anotada `vX.Y.Z` → merge em `main`
e `dev`). O **push da tag** é o gatilho: o pipeline reconstrói, revalida e publica
`streamtube-api:X.Y.Z`/`-worker:X.Y.Z` e cria o Release. Você decide *quando* taggear; o pipeline
faz o resto.

### Configuração única do repositório (fora do YAML)

Antes do primeiro push de imagem dar certo, no GitHub:

- **Permissão de escrita de pacotes:** Settings → Actions → General → *Workflow permissions* com
  "Read and write" (ou confie no `permissions: packages: write` já declarado no job `images`, que
  basta com o `GITHUB_TOKEN` padrão — nenhum PAT é necessário).
- **Pacotes GHCR:** na primeira publicação os dois pacotes nascem **privados** e não vinculados ao
  repo. Em cada pacote (Profile/Org → Packages): *Connect repository* → `streamtube-java` e defina
  a visibilidade (público para `docker pull` anônimo; privado exige login no deploy).
- **Dependency graph:** Settings → Code security and analysis → *Dependency graph* → **Enable**.
  Em repositório privado vem desligado por padrão, e o job `dependency-submission` (push na `main`)
  falha com "Dependency graph is disabled for this repository" enquanto não estiver ligado — o
  Dependabot de *version updates* funciona sem ele, mas o submit do grafo (que alimenta os alertas
  de segurança) não. Habilite também *Dependabot alerts* no mesmo painel para fechar o ciclo.
- **Deploy privado:** se mantiver privado, o host de produção precisa
  `docker login ghcr.io` (com um token de leitura de pacotes) antes do `compose ... up`.
