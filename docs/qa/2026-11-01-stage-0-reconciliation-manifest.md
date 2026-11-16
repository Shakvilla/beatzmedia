# Stage 0 Repository Reconciliation Manifest

**Assessment date:** 2026-11-01  
**Baseline branch:** `master`  
**Baseline commit:** `6d1c81fcfafa` (`docs(product): add stage zero implementation backlog`)  
**Stage 0 work unit:** `ST0-00`  
**Disposition:** Reconciled; the mixed working tree must not be merged as one change.

## Purpose

This manifest freezes the repository state before delivery work begins and assigns every active change stream to a reviewable work unit. It is intentionally non-destructive: no existing source, staged file, local artifact, provider implementation, or mobile implementation was removed or reset during reconciliation.

At the frozen baseline the working tree contains 75 tracked/index entries and 77 untracked entries. Several untracked entries are directories, so these numbers describe Git status entries rather than individual files.

## Delivery decision

The current tree contains valid work, but it combines provider infrastructure, provider adapters, independent web features, a complete mobile application, documentation, generated files, and local-only artifacts. It is therefore not a releasable or reviewable unit.

The safe integration order is:

1. Preserve this manifest and the delivery baseline documents.
2. Separate local/generated artifacts from product changes.
3. Land the provider selection and encrypted runtime-configuration kernel (`WU-EXT-1`).
4. Land payment vendor pinning (`WU-EXT-2`).
5. Land S3-compatible storage support (`WU-EXT-3`), while keeping migration tooling (`WU-EXT-4`) incomplete and unshipped.
6. Land provider-aware email delivery (`WU-EXT-5`).
7. Land password-reset routing (`WU-EXT-6`).
8. Keep SMS (`WU-EXT-7`), hosted card checkout (`WU-EXT-8`), and full activation evidence (`WU-EXT-9`) deferred to their planned stages.
9. Integrate podcast/player and Studio web work in independent changes after their failing tests are corrected.
10. Keep the mobile application and its CI job parked until the mobile delivery phase resumes.

## Change-stream inventory

| Bucket | Scope | Primary paths | Decision |
|---|---|---|---|
| Stage 0 product records | Current-state assessment, sequence design, backlog, this manifest | `docs/assessments/`, `docs/plans/2026-11-01-*`, `docs/qa/` | Keep as the source of delivery truth. |
| Provider planning | Requirements, architecture, configuration matrix, implementation plan, PRD and backlog entries | `docs/plans/provider-integrations/`, `docs/plans/2026-09-21-provider-integrations-design.md`, `BACKEND-PRD.md`, provider portions of `backend/.project/backlog.yaml` | Land as provider program documentation; do not combine with runtime code. |
| `WU-EXT-1` provider control plane | Capability/provider types, selection service/repository, readiness, encrypted runtime configuration, admin API and UI | New provider files under `backend/.../platform/`, `AdminProviderConfig*`, `Frontend/src/lib/api/queries/admin-provider-config.ts`, provider section of `admin.settings.tsx`, `V982`, `V986` | First provider implementation. Treat runtime secret configuration as an explicit extension of the selection kernel and cover it in the same security review. |
| `WU-EXT-2` payment pinning | Per-operation gateway identity, routing, guards, persistence, webhook/reconciliation behavior | Changed payment package, `GatewayVendor.java`, `ProductionPaymentSelectionGuard.java`, `V983`, payment architecture updates | Land only after `WU-EXT-1`; verify active legacy rows remain fail-closed as `unknown`. |
| `WU-EXT-3` S3-compatible storage | Provider-specific clients, endpoint selection, durable object-store identity, media adapters | Changed media package, `StorageClient.java`, storage settings in `application.properties` and `docker-compose.yml`, media architecture updates | Land after `WU-EXT-1`; require credentialed MEGA S4 evidence before activation. |
| `WU-EXT-4` media migration | Inventory, copy, verification, cutover and rollback between object stores | Only the durable `ObjectKey` prerequisite is present; migration tooling is absent | Do not mark complete or activate storage cutover. Continue later as a separate operational work unit. |
| `WU-EXT-5` provider-aware email | SMTP/Resend routing, rendering, delivery provider/message IDs, welcome and split-invite flows | Changed notification package, new email adapters/renderers/templates, `V984`, `V985`, notification/email architecture docs | Land after `WU-EXT-1`; require sandbox evidence and retry/idempotency verification. |
| `WU-EXT-6` password reset | Reset email rendered and sent through the selected transactional provider | Identity mailer/service, password-reset templates, `forgot-password.tsx`, identity architecture update | Land after `WU-EXT-5`; verify enumeration resistance and reset-link flow. |
| Deferred provider work | FROG SMS, hosted Redde checkout, full provider activation evidence | FROG configuration placeholders; planned `WU-EXT-7` through `WU-EXT-9` | Configuration placeholders do not constitute implementation. Keep deferred. |
| Podcast/player web slice | Podcast list/detail playback and player recovery changes | Player context, playback/podcast queries, podcast data/routes, shared frontend types | Independent web feature. Correct its test failure before integration. |
| Studio releases web slice | Release creation resume/detail and track presentation | Studio query/data and release routes | Independent web feature. Correct the two fixture/normalization failures before integration. |
| Mobile application | Flutter app, platform folders, mobile docs/tests and mobile CI job | `mobile/`, the mobile-only change in `.github/workflows/ci.yml`, `docs/plans/2026-09-21-beatzclik-fan-mobile-implementation.md` | Park unchanged. Mobile was explicitly deferred. |
| API documentation cleanup | Consolidated OpenAPI tags on nine admin resources | Existing `Admin*Resource.java` files | Independent documentation cleanup; not required by provider configuration. |
| Historical backlog corrections | `WU-CAT-8` and `WU-CAT-6` status changes | Two existing entries in `backend/.project/backlog.yaml` | Separate commit from Phase 5 registration. |
| Reference artifacts | Postman collection, standalone fan prototypes, older implementation plans | Root Postman/HTML files, `docs/superpowers/plans/` | Preserve, but do not ship as provider implementation. |
| Local/generated artifacts | Finder metadata, logs, TanStack temporary files, local launch/agent files | `.DS_Store`, `errorlog.txt`, `mobile/errors.txt`, `Frontend/.tanstack/tmp/`, `.claude/launch.json`, `AGENTS.md` | Exclude from release commits. Do not delete without owner confirmation. |

## Shared-file collision matrix

These files cannot be assigned by pathname alone. Their changes must be split by hunk or recreated in clean work units.

| File | Colliding concerns | Required split |
|---|---|---|
| `backend/src/main/resources/application.properties` | S4 (`EXT-3`), Resend (`EXT-5`), runtime-config encryption (`EXT-1`), future FROG (`EXT-7`) | Put encryption/runtime settings in `EXT-1`; S4 settings in `EXT-3`; Resend settings in `EXT-5`; leave FROG settings for `EXT-7`. |
| `backend/docker-compose.yml` | Local PostgreSQL exposure, S3 browser endpoint, provider encryption key | Local DB exposure is dev-infrastructure-only; public S3 endpoint belongs to `EXT-3`; encryption key belongs to `EXT-1`. |
| `backend/.project/backlog.yaml` | Phase 5 registration plus two historical catalog status corrections | One provider-planning change and one backlog-maintenance change. |
| `Frontend/src/routes/admin.settings.tsx` | Existing settings plus provider configuration for several capabilities | Keep provider UI with `EXT-1`; activation of each adapter remains gated by its own work unit. |
| `BACKEND-PRD.md` and provider plan documents | Cross-provider requirements | Planning/documentation change preceding implementation units. |
| `backend/docs/00-system-architecture.md` | Cross-provider topology | Update incrementally with the work unit that introduces each runtime dependency. |

## Migration ownership and order

| Migration | Owner | Dependency and rollout rule |
|---|---|---|
| `V982__provider_selection.sql` | `WU-EXT-1` | Foundation. Seeds safe defaults for storage, email, SMS and payment. |
| `V983__payments_gateway_pinning.sql` | `WU-EXT-2` | Requires `V982`. Legacy active rows become `unknown` and must fail closed pending operator reconciliation. |
| `V984__pin_email_provider_on_delivery_attempt.sql` | `WU-EXT-5` | Requires the email selection model from `V982`. |
| `V985__delivery_attempt_provider_message_id.sql` | `WU-EXT-5` | Must travel with `V984` and the delivery-attempt mapper changes. |
| `V986__provider_runtime_configuration.sql` | `WU-EXT-1` | Encrypted operational configuration is part of the control plane and must land before adapters attempt runtime credentials. |

The numeric order is valid, but the release order still matters. `V983` through `V985` must not be deployed without their matching application code, and `V986` requires a non-empty production encryption key before any secret is written.

## Verification snapshot

The following evidence was collected from this reconciled tree:

- Backend compilation passed.
- Backend test suite passed: 1,512 tests, 0 failures.
- Flyway applied 70 migrations through `V986` in the test environment.
- Frontend production build passed under Node.js 22.
- Frontend tests: 348 passed and 3 failed out of 351.
  - Two Studio release tests fail because the response fixture lacks `tracks` while the normalization path calls `wire.tracks.map`.
  - One player recovery test times out while waiting for unavailable-media recovery.
- Contract CI is currently ineffective: 21 `*ContractTest.java` classes exist, but none has `@Tag("contract")` while the CI command selects the `contract` group.
- Mobile static analysis reports 44 issues; mobile remains outside the active release scope.
- No credentialed MEGA S4, Resend, FROG or Redde activation evidence is present in this baseline.

Passing backend tests establish a useful regression baseline, but they do not certify the provider program because unit, integration, container and contract checks currently run as one mixed phase and external-provider sandboxes have not been exercised.

## Reviewable commit sequence

The working tree should be reconstructed into the following changes, each rebased on a clean predecessor and verified independently:

1. `docs(product): register provider integration program`
2. `chore(backlog): reconcile completed catalog work units`
3. `feat(platform): add audited provider selection and runtime configuration`
4. `feat(payments): pin gateway vendor per operation`
5. `feat(media): add selectable S3-compatible storage clients`
6. `feat(notifications): add provider-aware transactional email`
7. `feat(identity): route password reset through transactional email`
8. `docs(api): consolidate administrator OpenAPI grouping`
9. `feat(web): wire podcast playback and player recovery` after its test is green
10. `feat(studio): complete release resume and detail wiring` after its tests are green

The mobile tree, mobile CI job, `WU-EXT-4` migration tooling, `WU-EXT-7`, `WU-EXT-8`, and full `WU-EXT-9` activation remain parked.

## Stage 0 acceptance implications

`ST0-00` reconciliation is satisfied by this inventory and ownership map, but the overall Stage 0 gate remains open. The next required work is `ST0-01` through `ST0-06`: make the web baseline green, repair the contract gate, separate test phases, constrain configuration/CORS, establish a truthful CI/release path, and run the final certification matrix.

