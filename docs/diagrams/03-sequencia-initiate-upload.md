# Sequência — iniciar upload (`POST /videos`), camada por camada

O trajeto de uma única requisição autenticada pelas camadas da Clean Architecture: filtros de
segurança (bootstrap) → controller → use case (application) → ports → adapters (infrastructure).

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

    C->>F: POST /videos (Bearer JWT)
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

O tamanho e o content-type declarados entram **na assinatura SigV4** da URL: o próprio storage
rejeita (403) um PUT cujos bytes ou tipo real divirjam do declarado.