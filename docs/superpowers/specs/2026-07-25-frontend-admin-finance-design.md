# Frontend Admin Finance Wiring — Design

**Date:** 2026-07-25
**Slice:** Admin console — slice 4 of ~6 (finance overview, ledger, dispute detail, payout actions).
**Branch:** `feat/frontend-admin-finance` off `master` (post-#166).
**Backend:** the four finance resources live in the **`payments`** module (`org.shakvilla.beatzmedia.payments.adapter.in.rest`), not `admin` — already merged. No backend change.

## Goal

Wire the admin finance screens — overview (`admin.finance.index`), ledger (`admin.finance.ledger`), and dispute detail (`admin.finance.dispute.$disputeId`) — from the `admin-data` mock to the live endpoints, with **no visual change** beyond the documented micro-changes below. `admin.finance.tsx` is a bare `<Outlet />` layout with no data; nothing to wire there.

This is the first **money-moving** admin slice: the payout Send and Run-weekly buttons become real transfers.

## Verified backend surface

Every fact below I verified by reading the resources and view records directly — including the money representations, which are **not** uniform.

**`AdminFinanceOverviewResource`** — `GET /v1/admin/finance?range=24h|7d|30d` (default `7d`), `@RolesAllowed({finance, super-admin})` → `FinanceOverviewView`:
- `kpis: { gmvMtd, gmvDelta: int, platformFee, feeTakePct: int, payoutsDue, payoutsArtists: int, momoFloat }`
- `pendingPayouts: [{ id, artist, amount, method, status }]`, `providerMix: [{ name, value: int }]`, `disputes: [{ id, kind, subject, detail, amount, opened }]`
- **Money here is a bare `BigDecimal` in cedis** (serializes as a plain JSON number, e.g. `842000.00`) — a deliberate divergence from the module's usual envelope, documented in the view's own javadoc.
- `disputes[].opened` is `Instant.toString()` — **ISO-8601**, where the mock shows `"Apr 22"`.

**`AdminFinanceLedgerResource`** — `GET /v1/admin/finance/ledger?type=&q=&page=1&size=20`, `@RolesAllowed({finance, super-admin})` → `Page<LedgerEntryView>` = `{ items, page, size, total }`:
- `LedgerEntryView = { id, date, type, party, ref, amount }`.
- `amount` is a bare **signed** `BigDecimal` cedis (`amountMinor` shifted 2 places).
- `type` serializes via `LedgerType.display()`, which returns **exactly** the frontend's tokens (`Sale|Royalty|Tip|Payout|Refund|Fee`) — safe to cast.
- `date` is `Instant.toString()` (nullable) — ISO, where the mock shows `"May 02"`.
- `type` is parsed server-side with `fromDisplayOrNull`, so a blank/unknown value means "no filter" rather than a 422.

**`AdminFinanceDisputesResource`** — `@RolesAllowed({finance, super-admin})`:
| Method | Path | Body | Idempotency-Key |
|---|---|---|---|
| GET | `/disputes/{id}` | — | — |
| POST | `/disputes/{id}/refund` | `{ amount?: {amount, currency}, reason }` — omit `amount` ⇒ full refund | **REQUIRED** |
| POST | `/disputes/{id}/reject` | `{ reason }` (`@NotBlank`) | — |
| POST | `/disputes/{id}/escalate` | — | — |
- `DisputeView = { id, kind, subject, detail, amount: MoneyView, status, opened, timeline: [{id, text, time}] }`.
- **`amount` here is the `MoneyView` wrapper `{amount: BigDecimal, currency: String}`** — the same conceptual field the overview serves as a bare number.
- `status` ∈ `open | refunded | rejected | escalated` (lowercase enum constants).
- `opened` and `timeline[].time` are ISO-8601.

**`AdminFinancePayoutsResource`** — `@RolesAllowed({finance, super-admin})`:
| Method | Path | Returns | Idempotency-Key |
|---|---|---|---|
| GET | `/payouts` | `List<PendingPayoutView>` (ready + kyc_pending) | — |
| POST | `/payouts/run-weekly` | `PayoutBatchView` | **REQUIRED** |
| POST | `/payouts/{id}/send` | `PayoutTxnView` | **REQUIRED** |
- `PendingPayoutView = { id, artist, amount: MoneyView, method, status }` — **`MoneyView`**, unlike the overview's bare-number `pendingPayouts`.
- A missing/blank `Idempotency-Key` throws `MissingIdempotencyKeyException` (400). A single send **blocks on KYC with 409**; the weekly run has a per-withdrawal exactly-once guard, so a retry cannot double-pay.

## Architecture

- **New `Frontend/src/lib/api/queries/admin-finance.ts`:**
  - `financeOverviewQuery()` → `GET /admin/finance` (no `range` param — the UI has no range control, so the server's `7d` default applies). Key `['admin','finance','overview']`.
  - `ledgerQuery(type, q, page)` → `GET /admin/finance/ledger?page=&size=8` plus `type`/`q` when set. Key `['admin','finance','ledger', type, q, page]`.
  - `disputeQuery(id)` → `GET /admin/finance/disputes/{id}`. Key `['admin','finance','dispute', id]`.
  - `pendingPayoutsQuery()` → `GET /admin/finance/payouts`. Key `['admin','finance','payouts']`.
  - Mutations: `apiRefundDispute(id, reason)`, `apiRejectDispute(id, reason)`, `apiEscalateDispute(id)`, `apiRunWeeklyPayouts()`, `apiSendPayout(id)`. The three money POSTs (refund, run-weekly, send) each generate `idempotencyKey: crypto.randomUUID()` — the idiom already used by `queries/payouts.ts`. `apiFetch` turns that into the `Idempotency-Key` header.
- **Money normalization in the mappers.** Because the same field is a bare number on one endpoint and a `{amount,currency}` object on another, every mapper normalizes to a plain **`number` of cedis**, so the four existing per-screen display helpers (`compactCedis`, `full`, `signed`, `cedis`) keep working untouched and the rendered output is unchanged. A single shared `toCedis(wire: number | { amount: number })` handles both shapes.
- **Server-driven pagination without touching the shared component.** `Pagination` consumes a `Paged<T>` interface (`{page, setPage, pageCount, pageItems, total, size}`). Rather than modify `usePaged` or `Pagination` — both shipped and used by Users/Catalog/Moderation — add a sibling hook `useServerPaged<T>({ items, total, page, setPage, size })` in the same module that returns the identical `Paged<T>` shape with `pageItems: items` (the server already sliced) and `pageCount: ceil(total / size)`. `Pagination` needs **zero** changes, so no shipped screen can regress. The ledger keeps `size = 8` so its rows-per-page and paginator look identical to today.
- **New `format.ts` helper:** `monthDay(iso)` → `"Apr 22"` / `"May 02"`, reproducing the mock's ledger `date` and dispute `opened` labels. (`monthYear` already exists but yields `"Apr 2026"`.)
- **Routes:** replace each `useState(mock)` with `useQuery`, default to `[]`/zeroed shapes while loading, make the actions async (`await` mutation → `await invalidateQueries` → toast success/`'error'`), and render `AdminLoadError` on `isError` with an `isLoading` branch — matching the pattern established in #165/#166.

### Dispute status mapping

The mock has **no** status field; the route fakes a local `'open' | 'resolved'`. The backend has four. Mapping:

| Wire | Mapped | Why |
|---|---|---|
| `open` | `open` | — |
| `escalated` | `open` | Escalation is a sub-state of open, not a resolution. This also matches today's behavior, where Escalate deliberately does not change the status. |
| `refunded` | `resolved` | — |
| `rejected` | `resolved` | — |

Mapping `escalated` to `resolved` would be actively wrong (an escalated dispute is still open work), so it maps to `open`.

## Documented micro-changes (the only deviations from "no visual change")

1. **Dispute timeline wording:** the mock hardcodes `"3 days ago"` / `"1 day ago"`; the server sends ISO timestamps, which the existing `relativeTimeAgo` renders as `"3d ago"` / `"1d ago"`. Adopting the existing helper keeps the finance timeline consistent with the action logs already shipped on the Users and Catalog detail screens, at the cost of this wording shift. No new helper.
2. **Real data replaces fabricated data**, so counts, amounts, and dates reflect the live book rather than the mock's fixtures — expected, and the point of the slice.

## Out of scope / Category B (stays unwired)

- Ledger **Export CSV** — no endpoint; stays a toast.
- The overview's `?range=` selector — the UI has no control for it; the server default (`7d`) applies.
- **Partial refunds.** `RefundModal` is confirm-only ("Refund ₵X to the fan and close this dispute") and collects no amount, so refund omits `amount`, which the backend treats as a full refund. Adding a partial-amount field would be new UI.
- **Refund/Reject reasons.** Neither has an input field. Reject sends the string the mock already logs (`Dispute rejected · evidence sufficient`); refund sends an equivalent default. Both are persisted as the audit reason, so they are worded to read sensibly to an auditor.
- `PayoutTxnView` / `PayoutBatchView` response bodies are not rendered — the actions only toast and refetch, as today.
- The overview's per-row local `sent` flag disappears: after a successful send the row's real `status` comes from the server on refetch.

## Testing & gate

- Co-located Vitest: `admin-finance.test.ts` (query URLs + keys; the ledger's `type`/`q`/`page` params present when set and **absent** when not; each mutation's URL, body, and — for the three money POSTs — that an `Idempotency-Key` header is actually sent), plus mapper cases in `mappers.test.ts` (the `toCedis` dual-shape normalization, `monthDay`, the four-to-two dispute-status mapping, and the ledger page projection).
- A test asserting a named behavior must assert **that** behavior — e.g. an "omits the param" test must assert its absence, and an "idempotent" test must assert the header. (Two tests earlier in this project were named for a behavior they never checked.)
- Mapper outputs match `Frontend/src/lib/admin-data.ts` types exactly (`Finance`, `PendingPayout`, `ProviderMix`, `Dispute`, `LedgerTxn`, `LedgerType`, `TimelineEntry`).
- Gate (from `Frontend/`, Node 22 via nvm): `npm run build` (`tsc -b`) + the full `npx vitest run` green; no NEW lint errors.
- **Live QA requires a `finance` or `super-admin` account** — every endpoint in this slice is `@RolesAllowed({finance, super-admin})`, so the existing `support`-role QA account cannot even read these screens. QA runs against the **local dev DB only**; the payout actions move real money and must never be exercised against production.
- One PR: `feat/frontend-admin-finance`.
