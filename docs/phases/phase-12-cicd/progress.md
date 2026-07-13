# phase-12-cicd — Progress

**Status:** planned
**SIs:** 0/5 completed

| SI | Description | Status | Notes |
|----|-------------|--------|-------|
| SI-12.1 | Restructure ci.yml (verify/images/codeql/release) + PR test annotations + tag-safe concurrency | todo | keeps the current full Testcontainers build verbatim; adds inline JUnit reporting |
| SI-12.2 | Container images → GHCR (build every PR, push on main/v* tags) | todo | buildx + metadata-action + gha cache; push gated off pull_request/dev (ADV-01/02) |
| SI-12.3 | Automated GitHub Release from v* tags | todo | needs:[verify,images] (ADV-04); body carries image coordinates |
| SI-12.4 | Supply chain: Dependabot + CodeQL + dependency submission | todo | independent files; CodeQL manual build-mode (ADV-06) |
| SI-12.5 | Docs + DoD (README badges/pull note, deploy.md CI/CD section, repo settings, progress) | todo | actionlint on all YAML; confirm build untouched (ADV-10) |

## Notes

- Fifth post-roadmap improvement, first one that touches delivery rather than the app. A minimal CI
  already exists (single job: spotlessCheck + build + report upload) — this phase is additive
  hardening + the missing CD, not a rewrite of what verifies the code.
- The through-line is the existing release ritual: the hand-pushed `vX.Y.Z` tag on `main` becomes
  the trigger that builds, verifies and publishes the two images and cuts the GitHub Release. The
  human still owns *when* to release; the pipeline owns *what happens next*.
- Two prerequisites live outside YAML and must be documented (deploy.md): Actions `packages: write`
  permission, and the one-time GHCR package link-to-repo + visibility on first publish.
- No application/migration/runtime code changes; validation is actionlint + an unchanged
  `./gradlew build`.
