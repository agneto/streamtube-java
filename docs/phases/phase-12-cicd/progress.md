# phase-12-cicd — Progress

**Status:** completed
**SIs:** 5/5 completed

| SI | Description | Status | Notes |
|----|-------------|--------|-------|
| SI-12.1 | Restructure ci.yml (verify/images/release) + PR test annotations + tag-safe concurrency | done | verify keeps spotlessCheck + full `gradle build` verbatim; mikepenz/action-junit-report on `**/build/test-results/test/*.xml`; concurrency exempts tag refs (ADV-03) |
| SI-12.2 | Container images → GHCR (build every run, push on main/v* tags) | done | buildx + metadata-action + build-push-action@v6, gha cache scoped per image; push gated off pull_request/dev via a `publish` step (ADV-01); login only when publishing (ADV-02) |
| SI-12.3 | Automated GitHub Release from v* tags | done | release `if: refs/tags/v*`, `needs:[verify,images]` (ADV-04); body carries `streamtube-{api,worker}:X.Y.Z` coordinates |
| SI-12.4 | Supply chain: Dependabot + CodeQL + dependency submission | done | codeql.yml (java-kotlin, **build-mode: none** — corrects ADV-06: manual build-mode failed with "no source code seen" because CodeQL's tracer doesn't observe Gradle's daemon/in-process javac; `none` extracts from source); dependabot.yml (gradle grouped minor/patch + github-actions); dependency-submission.yml on main |
| SI-12.5 | Docs + DoD (README badges/pull note, deploy.md §10, compose.prod image refs, progress) | done | compose.prod api/worker now pull `ghcr.io/agneto/streamtube-*:${STREAMTUBE_VERSION:-latest}`; README CI+CodeQL badges + CI/CD note; deploy.md §10 (topology, coordinates, required repo settings ADV-08); checklist item for pinned version |

## Notes

- Fifth post-roadmap improvement, first touching delivery rather than the app. The pre-existing
  single-job CI (spotlessCheck + build + report upload) is preserved inside `verify` and extended;
  this was additive hardening + the missing CD, not a rewrite.
- The hand-pushed `vX.Y.Z` tag on `main` is now the trigger that rebuilds, revalidates and
  publishes both images and cuts the GitHub Release — the release ritual itself is unchanged.
- **No application/migration/runtime code changed.** The only non-workflow edits are
  `compose.prod.yaml` (pullable `image:` refs + usage header), `README.md` (badges + CI/CD note)
  and `docs/deploy.md` (§10 + checklist).
- Validation: all workflow/dependabot YAML parses clean; actionlint + `docker compose config` run
  green; `./gradlew build` untouched and unaffected (no build-script or source changes).
- Two prerequisites live outside YAML and are documented in deploy.md §10: Actions
  `packages: write`, and the one-time GHCR package link-to-repo + visibility on first publish
  (ADV-02/ADV-08). They block the *first* image push, not the merge.
