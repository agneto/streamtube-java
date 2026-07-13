# Phase 12 — CI/CD Hardening (context)

## Goal

Turn the minimal CI that already exists into a real CI/**CD** pipeline. Today a single workflow
(`.github/workflows/ci.yml`) runs `spotlessCheck` + `gradle build` (unit + integration +
Testcontainers E2E + ArchUnit — the ubuntu runner ships Docker, so the E2E suites run for real)
and uploads test/coverage reports as artifacts. That proves the code compiles and passes; it does
nothing about what we ship or how a release reaches a server.

This phase closes four gaps, none of which touch application or runtime code:

- **The Dockerfiles are never built in CI.** `Dockerfile.api`/`Dockerfile.worker` can break (a bad
  layer copy, a dependency-resolution step failing) and every green build would still hide it. The
  images are the actual deliverable and are currently unverified.
- **Nothing is published.** A release is a hand-tagged `vX.Y.Z` and a version bump; there is no
  artifact a deployer can pull. `compose.prod.yaml` references images that no pipeline produces.
- **PR feedback is a zip.** Failures land in an uploaded `test-reports` archive instead of inline
  annotations / a checks summary on the pull request.
- **No supply-chain signal.** No dependency update automation, no code scanning, no dependency
  graph — standard hygiene for a repo of this maturity.

The through-line: the tag the release flow already produces (`vX.Y.Z` on `main`) becomes the
trigger that builds, verifies and **publishes** the two container images to GHCR and cuts a
GitHub Release — the manual tag stays the single source of truth, the pipeline does the rest.

## What CI does today vs. the gaps

| Concern | Today | After Phase 12 |
|---------|-------|----------------|
| Compile + unit/integration/E2E/arch | ✅ `gradle build` (real Testcontainers) | unchanged, restructured into a named `verify` job |
| Formatting gate | ✅ `spotlessCheck` | unchanged |
| Test results on the PR | ❌ artifact zip only | inline annotations + checks summary |
| Dockerfile builds | ❌ never built | built on every PR (validation), pushed on `main`/tags |
| Published images | ❌ none | `ghcr.io/agneto/streamtube-{api,worker}` |
| Release notes / GitHub Release | ❌ manual/none | auto-created from the `vX.Y.Z` tag |
| Dependency updates | ❌ | Dependabot (Gradle + Actions), weekly |
| Code scanning / dep graph | ❌ | CodeQL + Gradle dependency submission |

## Capabilities & decisions

| Capability | Decision |
|------------|----------|
| Workflow topology | One workflow, several jobs so failures are legible and independent: **`verify`** (format + full `gradle build` + test reporting), **`images`** (matrix over api/worker: docker build; push conditional), **`codeql`** (independent), **`release`** (only on `v*` tags, `needs: [verify, images]`). Keep it in one file (`ci.yml`) plus a separate `codeql.yml` — CodeQL's schedule and permissions are cleaner isolated. |
| Image registry | **GHCR** (`ghcr.io/agneto/streamtube-api`, `-worker`). No extra account or secret: the built-in `GITHUB_TOKEN` with `packages: write` authenticates the push. `compose.prod.yaml` already expects pullable images — this fills that hole. |
| Push policy | **Build always, push selectively.** PRs and `dev` pushes build both images (Dockerfile validation) but never push — a PR from a fork must not get registry credentials, and untagged pushes would litter the registry. Push happens only on `main` pushes (`:latest`, `:sha-<short>`) and on `v*` tags (`:X.Y.Z`, `:X.Y`, `:latest`), tags computed by `docker/metadata-action`. |
| Release automation | The `v*` tag (which the existing release flow already pushes to `main`) triggers `release`: after `verify` and `images` are green, `softprops/action-gh-release` publishes a GitHub Release with auto-generated notes. The human still decides when to tag; the pipeline never tags on its own. |
| Docker layer caching | `type=gha` cache on `docker/build-push-action` so the (expensive) dependency-resolution layer is reused across runs. The Dockerfiles are already layered for exactly this. |
| Supply chain | **Dependabot** (`gradle` + `github-actions` ecosystems, weekly, grouped minor/patch to cut PR noise). **CodeQL** (`java-kotlin`, on push/PR to `main`/`dev` + weekly schedule). **Gradle dependency submission** on `main` so the dependency graph and Dependabot alerts see the real resolved versions. |
| Concurrency | Keep the existing `cancel-in-progress` group for PR/branch churn, but **never cancel a `v*`-tag run** — a superseded PR build is fine to drop; a half-published release is not. Key the group so tag refs are exempt. |
| Required repo settings | Documented, not codified: Actions must be allowed to write packages (default `GITHUB_TOKEN` permissions or per-job `permissions:`), and the two GHCR packages start private and are linked to the repo. `deploy.md` gets the checklist. |

## Lessons carried over

- The runner has Docker, so nothing about the E2E suites changes — they already run in CI today
  (the `disabledWithoutDocker` guard only matters locally).
- Per-job `permissions:` set to least privilege (`contents: read` by default; `packages: write`
  only on `images`, `security-events: write` only on `codeql`, `contents: write` only on
  `release`) — the token should carry only what each job needs.
- Ops notes ship with the change: image coordinates, the required repo settings, and "how a
  release publishes images" go in `deploy.md`, matching every prior phase.

## Out of scope

- **Deployment.** No SSH/k8s/cloud rollout — the pipeline *publishes* images; where they run is a
  deployment concern this repo does not own (it ships `compose.prod.yaml` as the reference).
- **Signing/SBOM/provenance** (cosign, SLSA attestations) — valuable but a separate slice; not
  needed to close the "nothing is published" gap.
- **Multi-arch images** (arm64). Single `linux/amd64` for now; note the one-line path to add it.
- **Changing the release ritual.** Branch → bump → annotated tag → merge to main+dev stays exactly
  as documented; this phase only *reacts* to the tag.
- Any application, migration, or runtime behaviour change.
