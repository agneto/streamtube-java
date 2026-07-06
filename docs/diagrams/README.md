# Diagramas

Diagramas Mermaid do StreamTube — o GitHub renderiza cada arquivo diretamente. As versões
inline (com o contexto completo do fluxo) estão em [`../fluxo-upload-video.md`](../fluxo-upload-video.md).

| Diagrama | Conteúdo |
|---|---|
| [01 — Arquitetura em camadas](01-arquitetura-camadas.md) | Módulos, regra de dependência e quem mora em cada camada |
| [02 — Sequência: fluxo completo de upload](02-sequencia-upload-completo.md) | Do cadastro ao streaming, com todos os sistemas |
| [03 — Sequência: iniciar upload](03-sequencia-initiate-upload.md) | `POST /api/v1/videos` camada por camada |
| [04 — Topologia RabbitMQ](04-topologia-rabbitmq.md) | Exchange, fila, retry e dead-letter |
| [05 — Ciclo de vida do vídeo](05-ciclo-de-vida-video.md) | Máquina de estados do `VideoStatus` |