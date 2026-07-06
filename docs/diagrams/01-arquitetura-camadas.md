# Arquitetura em camadas (Clean Architecture)

A regra de dependência aponta sempre para dentro e é verificada por teste
(`ArchitectureTest`, ArchUnit): o domínio não conhece framework algum; os use cases falam com
interfaces (ports); os adapters implementam essas interfaces; os bootstraps compõem tudo.

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

| Módulo | Papel | Exemplos |
|---|---|---|
| `domain` | Entidades e regras puras | `Video`, `VideoRepository` (interface), `VideoExceptions` |
| `application` | Use cases orquestrando ports | `InitiateUploadUseCase`, `StoragePort`, `VideoProcessingPublisher` |
| `infrastructure` | Adapters com tecnologia real | `S3StorageAdapter`, `VideoRepositoryAdapter`, `RabbitVideoProcessingPublisher` |
| `bootstrap-api` | Aplicação REST | `VideosController`, `JwtAuthenticationFilter`, `SecurityConfig` |
| `bootstrap-worker` | Consumidor da fila + FFmpeg | `VideoProcessingListener`, `FfmpegVideoAnalyzer` |