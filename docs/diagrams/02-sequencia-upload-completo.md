# Sequência — fluxo completo de upload

Do cadastro ao streaming. Os bytes do vídeo nunca passam pela API: o cliente envia e recebe
direto do MinIO/S3 via URLs presignadas; o processamento é assíncrono via RabbitMQ.

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

    Note over C,API: Passo 5 — publicação (Fase 04)
    C->>API: POST /api/v1/videos/{id}/publish (dono, exige READY)
    API->>DB: UPDATE video → published_at = now()
    API-->>C: 200 (antes disso, leituras respondem 404 para não-donos)

    Note over C,S3: Passo 6 — consumo
    C->>API: GET /api/v1/videos/{slug}/stream
    API-->>C: 302 Location: URL presignada
    C->>S3: GET (streaming direto)
```