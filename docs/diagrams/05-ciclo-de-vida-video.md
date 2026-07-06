# Ciclo de vida do vídeo — máquina de estados

Transições do `VideoStatus`. Cada escrita de status no worker commita em transação própria e
curta (nenhuma conexão fica presa durante o FFmpeg), então `PROCESSING` é visível pela API
pública enquanto a análise roda.

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

| Status | Quem grava | Significado |
|---|---|---|
| `PENDING_UPLOAD` | API (`InitiateUploadUseCase`) | Rascunho criado; aguardando o PUT do cliente |
| `QUEUED` | API (`CompleteUploadUseCase`) | Objeto confirmado no storage; job publicado |
| `PROCESSING` | Worker (`ProcessVideoUseCase`) | FFprobe/FFmpeg em execução |
| `READY` | Worker | Duração, thumbnail e metadata gravados; stream liberado |
| `ERROR` | Worker (listener da DLQ) | Tentativas esgotadas; `error_message` preenchido |