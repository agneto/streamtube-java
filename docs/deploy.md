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

## 6. Uploads multipart abandonados (lifecycle do bucket)

Uma sessão multipart iniciada e abandonada segura bytes **invisíveis** no bucket (as partes não
aparecem como objetos). Configure a regra de lifecycle que aborta uploads incompletos:

```bash
# MinIO
mc ilm rule add local/streamtube-videos --expire-abort-incomplete-multipart-days 7
# AWS S3: regra de lifecycle "AbortIncompleteMultipartUpload" com DaysAfterInitiation = 7
```

Mesma filosofia dos rascunhos `PENDING_UPLOAD` órfãos (system-design §3.3): limpeza é tarefa de
infraestrutura, não um cron dentro da aplicação.

## 7. Checklist antes do primeiro deploy

- [ ] `.env` com todos os segredos (tabela acima) — `docker compose ... config` valida sem subir
- [ ] TLS na frente de 8080 e 9000; `STORAGE_PUBLIC_URL`/`APP_BASE_URL` com os hosts públicos
- [ ] `CORS_ALLOWED_ORIGINS` com a origem exata do frontend (esquema + host + porta)
- [ ] SMTP real testado (registro manda e-mail de confirmação — sem ele ninguém loga)
- [ ] Backup do volume `pgdata` agendado (o storage pode ser recriado; o banco não)
- [ ] Smoke pós-deploy: register → confirm → login → upload → publish → stream (o
      `docs/GUIA-DE-USO.md` §6 tem o passo a passo em curl)
