# Exemplo de nova feature — "Editar título do vídeo" (referência viva)

Este documento mostra, com **código real que está no repositório**, como adicionar
uma nova feature nesta Clean Architecture, camada por camada. A feature de exemplo
é **`PATCH /videos/{id}`** — renomear um vídeo, **somente o dono**.

Use isto como modelo quando for criar um novo caso de uso.

> Branch: `feature/video-rename-example`. Todos os arquivos abaixo existem e têm
> testes verdes (`./gradlew build`).

---

## A regra de ouro: onde cada regra de negócio mora

- **domain** = regra **da própria entidade** (invariante). Ex.: "o título tem de ter
  de 1 a 255 caracteres" — vale para qualquer vídeo, independente do caso de uso.
- **application** = regra **do fluxo** (orquestração). Ex.: "carregar o vídeo,
  conferir se quem pede é o dono, então renomear e salvar".

---

## Passo 1 — domain (a regra da entidade)

**`domain/.../video/Video.java`** — o título deixou de ser `final` e ganhou um
método de comportamento com o invariante:

```java
public void rename(String newTitle, Instant now) {
  if (newTitle == null || newTitle.isBlank() || newTitle.trim().length() > 255) {
    throw new VideoExceptions.InvalidVideoTitleException();
  }
  this.title = newTitle.trim();
  this.updatedAt = now;
}
```

**`domain/.../shared/VideoExceptions.java`** — nova exceção de domínio:

```java
public static final class InvalidVideoTitleException extends DomainException {
  public InvalidVideoTitleException() {
    super("INVALID_VIDEO_TITLE", "Video title must be between 1 and 255 characters");
  }
}
```

**Teste:** `domain/src/test/.../video/VideoTest.java` (renomeia, rejeita vazio,
rejeita > 255). Não usa Spring.

---

## Passo 2 — application (o caso de uso / o fluxo)

**`application/.../video/RenameVideoUseCase.java`** — `@Service @Transactional`.
Orquestra os ports já existentes (`VideoRepository`, `ChannelRepository`,
`StoragePort`); a validação do título fica na entidade:

```java
@Transactional
public VideoInfoView execute(UUID videoId, UUID userId, String newTitle) {
  Video video = videoRepository.findById(videoId).orElseThrow(VideoNotFoundException::new);

  Channel channel = channelRepository.findByUserId(userId)
      .orElseThrow(ForbiddenVideoAccessException::new);
  if (!video.channelId().equals(channel.id())) {
    throw new ForbiddenVideoAccessException();   // regra de fluxo: só o dono
  }

  video.rename(newTitle, clock.instant());       // regra da entidade (domain)
  Video saved = videoRepository.save(video);
  // ...monta o VideoInfoView de resposta
}
```

**Teste:** `application/src/test/.../video/RenameVideoUseCaseTest.java` com Mockito
(renomeia quando é dono; 404 se não existe; 403 se não é dono).

---

## Passo 3 — infrastructure (só se faltar algo)

**Nada a fazer aqui.** Reaproveitamos `VideoRepository.findById/save`, que já têm
adapter JPA. Só mexeríamos aqui se precisássemos de:

- uma nova consulta → método novo no `VideoJpaRepository` + adapter + port;
- um campo novo no banco → **nova migration** `V5__...sql` +
  ajuste em `VideoEntity` e `PersistenceMapper`.

---

## Passo 4 — web (o endpoint)

**`bootstrap-api/.../web/dto/VideoDtos.java`** — DTO de entrada com validação
(feedback rápido pro cliente, 400):

```java
public record UpdateVideoRequest(@NotBlank @Size(max = 255) String title) {}
```

**`bootstrap-api/.../web/VideosController.java`** — o endpoint, fino, só delega:

```java
@PatchMapping("/{id}")
public VideoInfoResponse rename(
    @AuthenticationPrincipal AuthenticatedUser principal,
    @PathVariable("id") UUID id,
    @Valid @RequestBody UpdateVideoRequest request) {
  return toResponse(renameVideo.execute(id, principal.id(), request.title()));
}
```

**Segurança:** não precisou mexer. `PATCH` não casa com a regra
`permitAll` de `GET /videos/**`, então cai em `anyRequest().authenticated()`.

**Erros:** o `GlobalExceptionHandler` mapeia exceções de domínio por código; um
código novo sem mapeamento explícito cai no default **400** — então
`INVALID_VIDEO_TITLE` já vira 400 sem alterar o handler.

**Teste:** `bootstrap-api/src/test/.../video/VideosE2ETest.java` (dono renomeia →
200 + novo título; não-dono → 403; sem token → 401) contra Postgres real
(Testcontainers).

---

## Passo 5 — Definition of Done

```bash
export JAVA_HOME=<jdk-21>
./gradlew build      # compila + spotless + todos os testes
```

Resultado desta feature: `VideoTest` (3) + `RenameVideoUseCaseTest` (3) +
`VideosE2ETest` (7, inclui os 3 de rename) — verdes.

---

## Resumo visual (o que toquei para esta feature)

```
domain/
  video/Video.java                          (+ método rename, title deixou de ser final)
  shared/VideoExceptions.java               (+ InvalidVideoTitleException)
  test/video/VideoTest.java                 (novo)
application/
  video/RenameVideoUseCase.java             (novo)
  test/video/RenameVideoUseCaseTest.java    (novo)
infrastructure/
  (nada — reaproveitou repository/adapter existentes)
bootstrap-api/
  web/dto/VideoDtos.java                     (+ UpdateVideoRequest)
  web/VideosController.java                  (+ PATCH /{id}, helper toResponse)
  test/video/VideosE2ETest.java             (+ 3 testes de rename)
```

Repare como a mudança "engrossa" do centro para fora e como cada camada só conhece
a de dentro: o controller não sabe de JPA, o caso de uso não sabe de HTTP, e a
entidade não sabe de nada além de si mesma.
