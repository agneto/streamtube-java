# Phase 12 — CI/CD Hardening (plan)

## Objective

Evolve the single-job CI into a CI/CD pipeline that **verifies the containers we ship** and
**publishes them**: every PR builds both Docker images (not just the code), every push to `main`
and every `vX.Y.Z` tag pushes versioned images to GHCR, and the tag also cuts a GitHub Release —
while Dependabot and CodeQL add the supply-chain signal a mature repo expects. No application,
migration, or runtime code changes.

---

## Technical Specifications

### Workflow topology

`.github/workflows/ci.yml` (rewritten from one job into four) + `.github/workflows/codeql.yml`
(isolated for its schedule/permissions) + `.github/dependabot.yml`.

```
                         push: [main, dev] · pull_request · tag: v*
                                          │
        ┌───────────────┬────────────────┼──────────────────┐
     verify           images           codeql            (release)
  format + build   matrix api/worker  java-kotlin      v* tags only
  + test report    build always,      scan (push/PR    needs:[verify,
  + coverage       push on main/tag    + weekly)        images]
```

Least-privilege per job: workflow default `permissions: contents: read`; `images` adds
`packages: write`; `codeql` adds `security-events: write`; `release` adds `contents: write`.

### `verify` job (the current job, restructured)

- `spotlessCheck` → `gradle build` (unit + integration + Testcontainers E2E + ArchUnit), same as
  today; Gradle cache via `gradle/actions/setup-gradle`.
- **Add** inline test reporting: publish JUnit XML as PR annotations + a checks summary
  (`mikepenz/action-junit-report`, reading `**/build/test-results/test/*.xml`, `if: always()`).
- Keep the existing coverage/test-report artifact uploads (`if: always()` / `if: failure()`).

### `images` job

- `strategy.matrix: [{name: api, dockerfile: Dockerfile.api}, {name: worker, dockerfile: Dockerfile.worker}]`,
  `needs: verify` (don't build images for code that failed).
- `docker/setup-buildx-action` → `docker/metadata-action`
  (`images: ghcr.io/agneto/streamtube-${{ matrix.name }}`) → `docker/build-push-action` with
  `cache-from/to: type=gha`, `platforms: linux/amd64`.
- **Push condition:** `push: ${{ github.event_name != 'pull_request' && github.ref != 'refs/heads/dev' }}`
  — i.e. build-only on PRs and `dev`; push on `main` and on `v*` tags. Login via
  `docker/login-action` to `ghcr.io` with `GITHUB_TOKEN` runs only in the push case.
- **Tag policy** (`metadata-action` `tags:`): `type=raw,value=latest,enable={{is_default_branch}}`
  (main → `latest`), `type=sha,prefix=sha-` (main → `sha-<short>`),
  `type=semver,pattern={{version}}` + `{{major}}.{{minor}}` + `latest` (on `v*` tags).

### `release` job

- `if: startsWith(github.ref, 'refs/tags/v')`, `needs: [verify, images]`.
- `softprops/action-gh-release` with `generate_release_notes: true`; body prepends the pullable
  image coordinates for this version (`ghcr.io/agneto/streamtube-api:X.Y.Z` and `-worker`).

### `codeql.yml`

- Triggers: `push`/`pull_request` on `main`,`dev` + weekly `schedule`.
- `github/codeql-action` init (`languages: java-kotlin`, `build-mode: manual`) → `gradle build -x test`
  (compile only; tests already run in `verify`) → `analyze`.

### `dependabot.yml`

- `gradle` ecosystem (root; picks up the version catalog + module scripts), weekly, grouped
  minor/patch to keep PR volume sane; `github-actions` ecosystem, weekly. Optional
  `gradle/actions/dependency-submission` step on `main` so the dependency graph reflects resolved
  versions and Dependabot security alerts light up.

### Concurrency

Keep `cancel-in-progress` but exempt tags so a release run is never cancelled:
`group: ci-${{ github.workflow }}-${{ github.ref }}`,
`cancel-in-progress: ${{ !startsWith(github.ref, 'refs/tags/') }}`.

---

## Sub-issues

- **SI-12.1 — Restructure + PR test reporting:** split `ci.yml` into `verify`/`images`/`codeql`/
  `release` skeleton, wire per-job `permissions`, add JUnit annotations to `verify`, keep coverage
  artifacts, adjust concurrency to spare tag runs. (No push/publish yet — jobs are stubs where they
  depend on later SIs.)
- **SI-12.2 — Container images to GHCR:** `images` matrix job; buildx + metadata + build-push with
  gha cache; build-only on PR/`dev`, push `:latest`/`:sha-*` on `main` and `:X.Y.Z`/`:X.Y`/`:latest`
  on `v*`; GHCR login gated to the push case.
- **SI-12.3 — Release automation:** `release` job on `v*` (`needs: [verify, images]`) creating the
  GitHub Release with generated notes + image coordinates.
- **SI-12.4 — Supply chain:** `dependabot.yml` (gradle + github-actions, grouped, weekly);
  `codeql.yml` (java-kotlin, manual build-mode); dependency submission on `main`.
- **SI-12.5 — Docs + DoD:** `README.md` CI + GHCR badges and a "run from published images" note;
  `deploy.md` new CI/CD section (pipeline topology, image coordinates, how a `vX.Y.Z` tag publishes
  images + a Release, required repo settings: Actions package-write permission, GHCR package
  visibility); `GUIA-DE-USO.md` if a user-facing note fits; `progress.md`. Validate every YAML with
  `actionlint`; confirm `spotlessCheck`/`build` are untouched.

## Dependency Map

```
SI-12.1 ── SI-12.2 ── SI-12.3 ── SI-12.5
SI-12.4 ──────────────────────────/
```

SI-12.4 is independent (own files); it only rejoins at the docs/DoD gate.

## Deliverables

1. `docs/phases/phase-12-cicd/` — context, plan, validation, progress
2. `ci.yml` reestruturado: `verify` (build atual + anotações de teste no PR) · `images` · `release`
3. Imagens `ghcr.io/agneto/streamtube-{api,worker}` construídas em todo PR e publicadas em `main`/tags `v*`
4. GitHub Release automático a partir da tag `vX.Y.Z`, com as coordenadas das imagens
5. Supply-chain: Dependabot (Gradle + Actions) + CodeQL + dependency graph
6. `deploy.md`/`README.md` atualizados (topologia do pipeline, coordenadas das imagens, settings do repo)
