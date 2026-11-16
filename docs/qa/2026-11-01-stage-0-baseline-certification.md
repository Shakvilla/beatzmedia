# Stage 0 Baseline Certification

**Assessment date:** 2026-11-01  
**Certified branch:** `master`  
**Certified input commit:** `2e81fd12d7716a5ab03b59f6534083ddd174513e`  
**Stage 0 work unit:** `ST0-06`  
**Decision:** Local Stage 0 baseline PASS; production release remains NO-GO pending hosted security evidence and dependency-risk disposition.

## Scope and method

This certification evaluates the committed Stage 0 baseline, not the mixed development working tree. Commit `18927674c788bfd97c2f2e1b50585937f1d8f5ad` was checked out as a detached, initially clean worktree at `/private/tmp/beatzclik-stage0.LoMLZB/repo`. Uncommitted provider, web and mobile work in the primary worktree was therefore excluded from every result below.

The run found two operational defects in that input: Compose did not supply the newly required provider-configuration key, and the smoke assertion called a nonexistent catalog route. Those defects were corrected in `2e81fd12d7716a5ab03b59f6534083ddd174513e`. The affected Compose, shell and CI paths were then validated and the full smoke gate reran successfully. The certified commit is therefore the corrected descendant above; backend production code is unchanged between the two commits.

The certification covers the CI-equivalent backend phases, frontend production build and tests, migration integrity, runtime configuration failure behavior, the full Compose smoke path, and the release-gate decision logic. Hosted security-workflow results are tracked separately because a local checkout cannot truthfully substitute for GitHub CodeQL and dependency-review status checks.

## Toolchain

| Component | Version |
|---|---|
| macOS | Darwin 25.6.0, x86_64 |
| Java | OpenJDK 25.0.1 |
| Maven wrapper runtime | Apache Maven 3.9.12 |
| Node.js | 22.17.1 |
| npm | 10.9.2 |
| Docker Engine | 29.9.2 |
| Docker Compose | 5.6.1 |
| FFmpeg | 6.1 |

Java 25 was selected explicitly because the host default was Java 27 and the CI contract is Java 25. Frontend commands used the repository's Node 22 contract.

## Verification matrix

| Gate | Command or equivalent | Result | Evidence |
|---|---|---|---|
| Backend unit tests | `./mvnw test` | PASS | 1,390 tests; 0 failures, 0 errors, 0 skipped; Maven 33.814 s, wall 36.22 s. |
| Contract tests | `./mvnw test -Pcontract-tests` | PASS | 113 tests; 0 failures, 0 errors, 0 skipped; wall 422.41 s. The first sandboxed attempt could not access the Docker socket; the authorized rerun passed and is the recorded result. |
| Architecture tests | `./mvnw test -Parchitecture-tests` | PASS | 7 tests; 0 failures; wall 213.35 s. |
| Integration tests | `./mvnw verify -Pintegration-tests` | PASS | 635 tests; 0 failures, 0 errors, 0 skipped; wall 950.60 s. This includes 7 CORS assertions and 2 real-transcode assertions. |
| Coverage gate | `./mvnw verify -DskipITs=false -Pcoverage` | PASS | Replayed 1,390 unit and 635 integration tests; all JaCoCo checks met: 80% overall lines, 90% domain lines, 85% domain branches and 90% application lines. Maven 25:27, wall 1,531.63 s. |
| Formatting | `./mvnw spotless:check` | PASS | 1,720 files clean; wall 58.515 s. |
| Release-gate logic | `node --test .github/scripts/release-gate.test.cjs` | PASS | 4 of 4 decision cases passed, including rejection of a failed security workflow. |
| Frontend dependency install | resilient `npm ci` using the committed lockfile | PASS WITH DEBT | 375 packages installed after transient registry timeouts; npm reported 27 known vulnerabilities: 15 moderate, 9 high and 3 critical. No automatic dependency rewrite was performed. |
| Frontend production build | `npm run build` | PASS WITH DEBT | 2,170 modules built; wall 165.89 s. Vite reports a 1,100.94 kB main chunk. |
| Frontend tests | `npx vitest run --reporter=default` | PASS WITH FLAKE DEBT | Initial full run: 350 passed and one player recovery timeout; immediate focused rerun passed 2 of 2; second full run passed all 351 tests across 46 files in 19.58 s. The original timeout is classified as load-sensitive debt, not a reproducible failure. |
| Empty-database migration | CI-equivalent Flyway clean, migrate and validate | PASS | PostgreSQL 16.15; clean, migrate and post-migration validate all succeeded; 65 migrations applied through `V981`; Maven 49.325 s after the missing Flyway plugin was downloaded. |
| Compose smoke | `backend/scripts/smoke.sh --timeout 300` | PASS AFTER FIX | Fresh image and empty volumes; application ready; `GET /q/health/live` 200, `GET /q/health/ready` 200 and `GET /v1/home` 200; automatic teardown passed. |
| Production missing-secret startup | packaged application under the `prod` profile with required values absent | PASS | Exited 1 in 8.7 s and named only missing configuration properties; no secret values were printed. |

## Configuration and CORS evidence

The committed production configuration has no development fallback for the database password, S3 credentials, JWT key locations, payment webhook secret, provider-configuration encryption key or allowed origins. The local Compose file now supplies a clearly labeled development-only encryption key so the packaged development stack can boot; it does not change `%prod` property requirements. The integration suite passed the seven-case CORS policy matrix, covering allowed and rejected origins and methods. Production startup failure behavior is recorded separately in the verification matrix.

## Migration boundary

The certified commit contains 65 Flyway migrations and ends at `V981__ownership_grant_downloadable.sql`, plus the repeatable development seed. Flyway successfully validated and applied this set to an empty test schema during the coverage run.

Provider migrations `V982` and later exist only in the mixed primary working tree and are deliberately excluded. They must enter through the provider work-unit sequence defined in the reconciliation manifest, with matching application code and rollback evidence.

## Release-path evidence

Release publication is bound to the exact commit SHA. The release gate requires successful `ci` and `security` workflow runs for a `push` to `master` at that SHA; tags cannot bypass the decision. Images use immutable `sha-<full-sha>` tags and include the source revision label. Deployment rejects empty and `latest` image tags, and rollback has no `latest` fallback.

The local four-case release-gate test validates the decision algorithm. It does not manufacture hosted workflow evidence: the certified commit is not releasable until GitHub records successful exact-SHA `ci` and `security` runs.

## Known debt and warnings

- The build reports `quarkus.log.console.json` as an unrecognized configuration key. Structured production logging therefore cannot be assumed active.
- Hibernate reports deprecated configuration and Bean Validation reports deprecated container-level `@Valid` usage.
- npm reports 27 dependency vulnerabilities, including 3 critical and 9 high findings. These require triage against reachable production code before release approval.
- The frontend production bundle has a 1.1 MB main JavaScript chunk and needs code-splitting/performance work.
- The first complete frontend test run had one player-recovery timeout; both its focused rerun and the second complete suite passed. Keep the case under flake monitoring.
- Player tests warn that an empty string is passed as a media `src`; render no element or pass `null` instead.
- The smoke helper's `./mvnw -DskipTests package` step still replayed unit-test output, increasing smoke duration. Its Maven skip behavior should be corrected without weakening the separate test gates.
- Test-profile mail logging prints captured message bodies, including reset links. Confirm body logging is disabled or redacted in every production logging configuration.
- Provider sandboxes, mobile CI and provider migrations are intentionally out of scope for this baseline and remain parked.

## Decision

The committed baseline is **GO for Stage 1 feature delivery**: all locally reproducible Stage 0 gates pass, the frontend suite is green, migrations are clean, the production configuration fails closed, and the real Compose stack boots and serves its public catalog.

Production release remains **NO-GO** until both conditions below are satisfied:

1. GitHub records successful exact-SHA `ci` and `security` workflow runs for `2e81fd12d7716a5ab03b59f6534083ddd174513e` (or its documentation-only descendant).
2. The 3 critical and 9 high npm findings are remediated or explicitly risk-accepted using package-level reachability evidence.

Provider sandboxes, provider migrations and mobile remain outside this baseline and must follow their parked delivery sequence.
