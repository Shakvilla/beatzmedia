# Frontend Admin Overview, Audit & Health Wiring — Design

**Date:** 2026-07-25
**Slice:** Admin console — slice 5 of ~6 (dashboard overview, audit log, system health).
**Branch:** `feat/frontend-admin-overview-audit`, **stacked on `feat/frontend-admin-finance`** (PR #167) because it reuses that branch's `useServerPaged` hook. #167 must merge first.
**Backend:** `AdminOverviewResource` (admin module) + `AdminAuditResource` (**audit** module) — already merged. No backend change.

## Goal

Wire the admin dashboard (`admin.index`), audit log (`admin.audit`), and system health (`admin.health`) from the `admin-data` mock to the live endpoints. Unlike the earlier slices this one **cannot be purely "no visual change"** — three of these panels are backed by data the backend does not have, and one chart's data shape genuinely differs. Every deviation is enumerated below.

## Verified backend surface

Every fact below was read from the source, including the placeholder disclosures.

**`GET /v1/admin/overview?range=24h|7d|30d`** — `@RolesAllowed` all five admin roles; blank range → `7d`; an unknown value → **422 `INVALID_RANGE`**.
```
AdminOverviewDto(rangeLabel, kpis, gmvByDay: BigDecimal[], needsAttention[], topArtists[], paymentMethods[])
  KpisDto(activeUsers: int, streams: long, gmv: BigDecimal, newArtists: int, deltas)
  DeltasDto(users: int, streams: int, gmv: int)
  AttentionItemDto(id, label, sub, to) · TopArtistDto(name, revenue: BigDecimal) · PaymentMethodDto(name, value: BigDecimal)
```
- **Money is bare `BigDecimal` cedis** (`Money.ofMinor(...).toCedis()`), matching the mock's plain-number convention. No `{amount,currency}` envelope here.
- **Real (Category A):** `activeUsers`, `streams`, `gmv`, `newArtists`, `gmvByDay`, `topArtists`, `deltas.streams`, `deltas.gmv`.
- **Always empty / fixed (Category B), per the service's own comments:** `needsAttention` = `List.of()` ("no moderation/compliance/risk queue counts in this WU's scope"), `paymentMethods` = `List.of()` ("no payment-method dimension in analytics' facts"), `deltas.users` = `0` ("activeUsers is not time-boxed").
- **`gmvByDay` is raw cedis, bucketed `Grain.DAILY`, length = `range.days()` → 1 / 7 / 30 bars.** The mock is 0–1 *normalized fractions* at 24 / 22 / 30 bars.

**`GET /v1/admin/health`** — same five roles, no params.
```
HealthDto(status: String, metrics: MetricDto[], listeners: Double[], incidents: IncidentDto[])
```
**The entire payload is a hardcoded placeholder:** `new HealthView("normal", List.of(), List.of(), List.of())`. Its javadoc states there is no APM, incident tracking, gateway monitor, or listener telemetry anywhere in the codebase, and *"no failure-detection logic to ever honestly return `degraded`"*.

**`GET /v1/admin/audit?type=&actor=&q=&page=1&size=20`** — **`@RolesAllowed("super-admin")` only** (an RBAC asymmetry: a `support`/`finance` admin can see Overview and Health but **not** Audit). Returns `Page<AuditEntryDto>` = `{items, page, size, total}`; `size` clamps at 100.
```
AuditEntryDto(id, actor, action, target, type, time)
```
- `actor` = display name when known, else the raw actor id.
- **`target` is compound `targetType + ":" + targetId`** (e.g. `AdminMember:acc-123`), where the mock used free-form descriptive text.
- `type` = `enum.name().toLowerCase()`, which happens to equal the frontend's `AuditType` literals — but via no dedicated mapper, so the mapper casts defensively.
- **`time` is `Instant.toString()`** (ISO-8601); the mock supplied pre-formatted `"12m ago"`.
- Server-side filtering is real: `q` matches **action / targetType / targetId**; `actor` is a **separate** param that `q` does not cover.

## Architecture

- **New `Frontend/src/lib/api/queries/admin-overview.ts`:** `overviewQuery(range)` (key `['admin','overview', range]`), `healthQuery()` (key `['admin','health']`), `auditQuery(type, q, page)` (key `['admin','audit', type, q, page]`, sends `size=8` to match the paginator). All reads; this slice has **no mutations**.
- **Mappers** in `lib/api/mappers.ts`: `toAdminOverview`, `toHealth`, `toAuditEntry`/`toAuditPage`. Audit `time` → `relativeTimeAgo` (already on master); `type` cast to `AuditType`.
- **Routes** swap `useState(mock)` for `useQuery`, with `AdminLoadError` on `isError` and a `Loading…` branch — the pattern from #165/#166/#167.
- **Audit pagination** uses `useServerPaged` (from the stacked finance branch), with `page` reset to 1 on both the type-chip change and the debounced search change — the caller-owns-page contract that hook documents.

## Deviations from "no visual change" — all deliberate, all because the data is not there

1. **"Needs attention" and "Payment methods" panels render empty states.** The endpoint always returns `[]` for both. `PaymentBars` also computes `Math.max(...[])` on an empty array → `-Infinity` → `NaN` bar widths, so it needs an empty guard regardless. (Identical to the `ProviderBars` bug fixed in the finance slice.)
2. **The health page becomes honest-empty.** Status renders "All systems normal" (the only value the backend can produce), and metrics / listeners / incidents render empty states. `ListenersChart` must guard the empty series — `Math.max(...[])`/`Math.min(...[])` and `series.length - 1 === -1` currently produce a `NaN` SVG path. Rationale: fabricated health figures are the most dangerous kind on an admin console — an operator could conclude the platform is healthy from an invented "142ms p95 / 99.98% uptime".
3. **The GMV bar chart changes shape and its tooltip becomes truthful.** Live data is raw cedis at 1 / 7 / 30 buckets (so the 24h view is a single bar, where the mock drew 24). Bar heights are normalized client-side against the window max; the tooltip currently *invents* a number (`₵${Math.round(b*12)}k` derived from a 0–1 fraction) and will instead show the actual cedis amount.
4. **The KPI delta arrow gets a sign.** Today it is hardcoded `<ArrowUp/>` + green regardless of value, so a real negative delta would render "▲ -18%" in green. Negative deltas are genuinely produced by the backend's `pctChange`. Fixed to show a down-arrow in red when negative.
5. **Audit `target` and `time` read differently** — `AdminMember:acc-123` instead of descriptive prose, and `"12m ago"` derived from a real timestamp rather than a canned string.
6. **The audit search box now matches action/target only, not actor.** Server-side paging makes client-side search incorrect (it would only search the current page), and one box can drive only one server param; `q` covers the most ground. Typing an actor name no longer matches — a documented functional regression, chosen over silently truncating a compliance artifact to 100 rows.

## Out of scope / Category B (stays unwired)

- Audit **Export CSV** — no endpoint; stays a toast.
- The overview's `deltas.users` is always `0` server-side; the Active-users tile shows a 0% delta rather than the mock's fabricated `+22%`.
- No `actor` filter UI is added (see deviation 6) — that would be new UI.

## Testing & gate

- Co-located Vitest: `admin-overview.test.ts` (query URLs + keys; `range` param present; audit `type`/`q` present when set and **absent** when not; `size=8`; the `Page` projection) and mapper cases in `mappers.test.ts` (bare-cedis passthrough, empty Category-B arrays, audit `time` → relative, `type` narrowing, `target` passthrough).
- A test named for a behavior must assert that behavior — the recurring defect in this project.
- Gate (from `Frontend/`, Node 22 via nvm): `npm run build` (`tsc -b`) + the full `npx vitest run` green.
- **Live QA needs a `super-admin` account for the audit page specifically** (overview and health accept any of the five admin roles). Also verify the 422 path: an unknown `range` must surface as the error affordance, not a blank dashboard.
- One PR: `feat/frontend-admin-overview-audit`, **opened against `feat/frontend-admin-finance`** (or retargeted to `master` once #167 merges).
