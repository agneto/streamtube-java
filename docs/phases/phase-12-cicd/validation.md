---
kind: phase
name: phase-12-cicd
status: clean
issue_count: 0
issues: []
advisories:
  - id: ADV-01
    text: "Never push images from pull_request builds. A PR from a fork runs with a read-only GITHUB_TOKEN and secrets are withheld — a push step would fail, and if it somehow succeeded it would be an untrusted-code publish. Gate push on event_name != 'pull_request' AND ref != dev; keep GHCR login inside the same condition so PRs never even authenticate."
  - id: ADV-02
    text: "Publishing to GHCR needs packages: write on the job (or workflow) token. The default GITHUB_TOKEN can be restricted to read-only by repo/org settings — set per-job permissions: packages: write explicitly rather than assuming. No PAT/secret is required for ghcr.io with the built-in token."
  - id: ADV-03
    text: "The v* release run MUST NOT be cancellable by concurrency. cancel-in-progress: true would let a newer ref kill a half-finished publish, leaving a tag with no images / no Release. Make cancel-in-progress false for tag refs."
  - id: ADV-04
    text: "release needs [verify, images] — never cut a GitHub Release or publish a versioned image for a tag whose tests or image build failed. A published :X.Y.Z that never passed verify is worse than no release."
  - id: ADV-05
    text: "Do not split spotlessCheck/unit/E2E across jobs that each run gradle build — that re-runs the expensive Testcontainers suite N times and re-warms the cache N times. Keep one verify job doing the full build; parallelism comes from images/codeql, which do genuinely different work."
  - id: ADV-06
    text: "CodeQL for Java on a Gradle multi-module build: use build-mode: manual and compile with a plain gradle build -x test (tests already run in verify). autobuild frequently mis-detects layered multi-module Gradle and either builds nothing or the wrong subset, yielding empty analysis."
  - id: ADV-07
    text: "metadata-action tag rules are event-sensitive: type=raw latest with enable={{is_default_branch}} only fires on main, type=semver only on tag refs. Verify the matrix produces exactly the intended tag set on each trigger (main push vs v* tag vs dev/PR) before trusting the publish — a mis-scoped rule silently tags dev builds as :latest."
  - id: ADV-08
    text: "The two GHCR packages are created on first push and default to private, initially NOT linked to the repo (so the repo token can't necessarily re-push until linked). This is a one-time manual settings step (link package to repo, set visibility). It cannot be done in YAML — document it in deploy.md or the first real release will surprise you."
  - id: ADV-09
    text: "type=gha docker cache is scoped per branch/ref by default; PR builds won't hit main's cache, and cache can evict. Treat it as best-effort speedup, never correctness — the Dockerfiles must still build cold (they do: dependency layer is self-contained)."
  - id: ADV-10
    text: "This phase must not perturb the existing green build. ci.yml is the only behavioural surface; validate every workflow/dependabot file with actionlint and confirm gradle spotlessCheck/build are byte-for-byte unaffected. There is no application code to test."
---

# Phase 12 — Validation

## Decisions coverage

Every gap named in context.md maps to a sub-issue: unverified Dockerfiles → `images` build-on-every-PR
(SI-12.2); nothing published → GHCR push on main/tags (SI-12.2) driven by the tag the release flow
already produces; zip-only PR feedback → JUnit annotations (SI-12.1); no supply-chain signal →
Dependabot + CodeQL + dependency graph (SI-12.4); no release artifact → automated GitHub Release
(SI-12.3). The existing `verify` behaviour (spotlessCheck + full Testcontainers build) is preserved
verbatim — this is additive. New conventions: GHCR image coordinates, push-policy matrix, tag-exempt
concurrency, least-privilege per-job tokens. No undecided topic blocks implementation.

## Dependency gaps

None in code. Two **operational** prerequisites are external to the repo and must be documented
rather than automated (see ADV-02, ADV-08): the Actions token needs `packages: write`, and the two
GHCR packages need a one-time link-to-repo + visibility choice on first publish. Neither blocks
merging the workflows; both block the *first* successful push and belong in `deploy.md`.

## Verdict

**clean** — ready to implement.
