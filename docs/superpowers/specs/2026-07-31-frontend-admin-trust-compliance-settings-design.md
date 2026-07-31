# Frontend Admin Trust, Compliance & Settings Wiring — Design

**Date:** 2026-07-31
**Slice:** Admin console — the final slice (trust & safety, compliance, platform settings).
**Branch:** `feat/frontend-admin-trust-compliance-settings` off `master`. Independent; every earlier admin slice is merged.
**Backend:** `AdminRiskResource`, `AdminComplianceResource`, `AdminSettingsResource` — already merged. No backend change.

## Goal

Wire the last three mock-backed admin screens to their live endpoints. This slice differs from its predecessors in two ways: it contains the console's **only settings write** (a full-replace `PUT` that governs platform money constants), and it has the **most unbacked surface** — several controls look functional but have nothing behind them.

## Verified backend surface

Read from the source, including the "honest-static" disclosures.

### `AdminRiskResource` — `/v1/admin/risk`, `@RolesAllowed({moderator, super-admin})`

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/` | — | `RiskBoardDto` |
| POST | `/{id}/review` | — | `RiskSignalDto` — **does not change status** (stays `open`) |
| POST | `/{id}/clear` | — | `RiskSignalDto` (`open→cleared`) |
| POST | `/{id}/ban` | `{ reason }` **`@NotBlank`** | `RiskSignalDto` (`open→banned`) |

- `RiskBoardDto = { kpis: { chargebackRate, suspiciousSignups, fraudFlags, botStreams }, signals: RiskSignalDto[] }`.
- `RiskSignalDto = { id, subject, type, detail, level, time, status }` — all strings; `level`/`status` via `wireValue()`; **`time` is `Instant.toString()`** (ISO), where the mock showed `"20m ago"`.
- **Only `fraudFlags` is real.** `chargebackRate`, `suspiciousSignups`, and `botStreams` are hardcoded `"0%"` / `0` / `"0%"` — the service says verbatim: *"Category B (honest-empty): no fraud-detection/analytics subsystem backs these yet."*
- **Ban also bans the subject's account** (`AccountAdminPort#ban`) and records the reason in the audit entry. Every mutation is audited.

### `AdminComplianceResource` — `/v1/admin/compliance`, `@RolesAllowed("super-admin")`

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/?type=` | — | `List<ComplianceRequestDto>` (bare array); 422 on an unknown type |
| POST | `/{id}/start` | — | `ComplianceRequestDto` (`new\|overdue → in_progress`) |
| POST | `/{id}/complete` | — | `ComplianceRequestDto` (`→ completed`) |
| POST | `/{id}/export` | — | **202** `{ jobId, status }` |
| POST | `/{id}/notice` | — | `ComplianceRequestDto` (audit only, no status change) |

- `ComplianceRequestDto = { id, type, subject, detail, due, status }` — `type`/`status` via `wireValue()`; **`due` is `Instant.toString()` or null**, where the mock showed `"in 12 days"` / `"overdue 1 day"` / `"completed"`.
- **`/export` is a documented stub**: *"a Category-B honest stub (mints a job id + audits; no DSAR worker exists)"* — it returns `status: "queued"` and changes nothing.
- The UI's `DSAR` filter chip covers **two** wire types (`DSAR-export` + `DSAR-delete`), so it cannot be expressed as one `?type=` value.

### `AdminSettingsResource` — `/v1/admin/settings`, `@RolesAllowed("super-admin")` on both verbs

- `GET` → `PlatformSettingsView { platformFeePct: int, payoutDay: String, payoutMinimum: BigDecimal, defaultCurrency: String, maintenanceMode: boolean, providers: {momo, vodafone, airteltigo, card, bank}, flags: {artistSignups, podcasts, events, tipping, fanMessaging} }`.
- **`payoutMinimum` is bare `BigDecimal` cedis on the wire.** The domain stores `payoutMinimumMinor` (long pesewas); the service converts both ways. The frontend therefore sends and receives plain cedis and must **not** do any minor-unit arithmetic itself.
- **`platformFeePct` is an integer percent** (`30`, not `0.30`).
- `PUT` body `SettingsRequest`: `@Min(0) @Max(100) int platformFeePct`, `@NotBlank payoutDay`, `@NotNull @PositiveOrZero @DecimalMax("1000000") BigDecimal payoutMinimum`, `@NotBlank defaultCurrency`, `boolean maintenanceMode`, `@NotNull providers`, `@NotNull flags`. **It is a full replace — a partial body is a 422, not a merge.**
- Saving `platformFeePct` keeps `creatorSharePct` complementary (`100 - fee`). The other money constants — **tip fee, bundle discount, service fee — are not on this contract at all** and are preserved server-side; this page cannot change them.
- **One combined audit entry per save** (`AuditType.SETTINGS`), whose `reason` is populated only when `platformFeePct` changed.

## The unbacked surface — and how it is handled

Per the product decision, controls with nothing behind them are marked unavailable rather than left looking functional.

1. **Payment-provider toggles** (MoMo / Vodafone / AirtelTigo / Card / Bank). The `PUT` accepts them and **silently discards** them; the `GET` hardcodes all five to `true`. Toggling one today would appear to save and then silently revert on reload. They render **disabled** with a short "not yet configurable" note.
2. **The entire "Admin team & roles" section** (invite, change role, remove). **No endpoint exists anywhere** — confirmed across the whole `admin/adapter/in/rest` package. It sits beside controls that now genuinely persist, so it is labelled local-only. Its local behaviour is unchanged.
3. **Trust KPI strip** — three of the four figures are hardcoded zeros server-side. They render as the real (zero) values with the honest-empty treatment already used for MoMo float, rather than the mock's plausible-looking numbers.
4. **Compliance "Download data"** — wired to `POST /export`, which queues a job id and nothing more. The existing toast copy ("Preparing data export") already matches queue semantics, so it stays.

## Deliberate UI additions

Two, both because the API demands something the mock never collected:

- **A ban-reason modal.** `POST /{id}/ban` rejects a blank reason, and a ban both suspends the subject's account and writes an audit entry. Reusing the takedown-modal pattern (reason chips + free-text) keeps every ban audit entry meaningful; a canned default would make them uniformly useless.
- **Relative-time and due-date rendering.** `time` and `due` arrive as ISO instants where the mock supplied prose. `time` uses the existing `relativeTimeAgo`. `due` needs a small helper that renders a future date as `"in N days"`, a past one as `"overdue N days"`, and a completed request as `"completed"` — reproducing the mock's wording from real data.

## Architecture

- **New `Frontend/src/lib/api/queries/admin-trust.ts`:** `riskBoardQuery()`, `apiReviewSignal(id)`, `apiClearSignal(id)`, `apiBanSignal(id, reason)`.
- **New `Frontend/src/lib/api/queries/admin-compliance.ts`:** `complianceQuery()` (unfiltered — the `DSAR` chip spans two types, so filtering stays client-side), `apiStartRequest(id)`, `apiCompleteRequest(id)`, `apiExportRequest(id)`, `apiNoticeRequest(id)`.
- **New `Frontend/src/lib/api/queries/admin-settings.ts`:** `platformSettingsQuery()`, `apiSaveSettings(settings)` sending the **complete** object.
- **Mappers** in `lib/api/mappers.ts` for the three response shapes, plus `dueLabel(iso, status, now?)` in `lib/format.ts`.
- **Routes** swap to `useQuery` with `AdminLoadError` + `isPending` branches (the pattern now standard across the console), and the mutations `await` then invalidate.

## Out of scope / Category B

- The admin-team section and the provider toggles (above) — marked, not wired.
- `defaultCurrency` stays read-only; the backend supports only `GHS`.
- Tip fee, bundle discount, and service fee are not on the settings contract.
- `POST /export`'s returned `jobId` is not surfaced — there is no job-status UI and no worker behind it.

## Testing & gate

- Co-located Vitest: one test file per query module (URLs, keys, bodies — including that the settings `PUT` sends the **complete** object and that the ban sends its reason), plus mapper cases and `dueLabel` boundary cases (future / today / overdue / completed / null).
- A test named for a behavior must assert that behavior.
- Gate (from `Frontend/`, Node 22 via nvm): `npm run build` (`tsc -b`) + the full `npx vitest run` green.
- **Live QA needs `super-admin`** — Compliance and Settings are super-admin only; Risk also accepts `moderator`. Verify a settings save round-trips (change the fee percent, reload), that a provider toggle is visibly disabled, that a ban requires a reason, and that the Trust KPI zeros read as honest rather than broken.
- One PR: `feat/frontend-admin-trust-compliance-settings`, targeting `master`. This completes the admin console.
