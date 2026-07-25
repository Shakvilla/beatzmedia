# Frontend Admin Finance Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the admin finance overview, ledger, and dispute detail from the `admin-data` mock to the live `payments`-module endpoints — including the first money-moving admin actions (payout send / run-weekly) — with no visual change beyond the documented micro-changes.

**Architecture:** Same idiom as the merged Users/Catalog slices: one `queries/admin-finance.ts` (TanStack `queryOptions` reads + free `api*` mutation fns), wire types + `toX` mappers in the shared `lib/api/mappers.ts`, routes swapped to `useQuery` + `invalidateQueries`, reusing `AdminLoadError`. Two new primitives: a `toCedis` money normalizer (the backend serves money in two different shapes) and a `useServerPaged` hook that satisfies the existing `Paged<T>` interface so the shared `Pagination` component needs no change.

**Tech Stack:** React 18, TanStack Query v5, TanStack Router, Vitest + RTL, TypeScript (`tsc -b`), Tailwind.

## Global Constraints

- **No visual change** beyond the three documented micro-changes listed below. JSX, `className`, copy, and layout preserved verbatim otherwise.
- **Node 22 via nvm** for all `npm`/`npx`: prefix with `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null &&`.
- **Real typecheck gate is `npm run build`** (`tsc -b`), not vitest. Every task runs both.
- **NEVER stage** `backend/src/main/resources/application.properties` or `backend/docker-compose.yml`. Frontend-only branch — do not touch backend.
- **Money arrives in TWO shapes** and must be normalized: bare JSON number (cedis) on the overview + ledger; `{ amount, currency }` (`MoneyView`) on disputes + payouts. Never assume one shape.
- **The three money POSTs REQUIRE an `Idempotency-Key`** (refund, run-weekly, send) — a missing/blank header is a 400. Generate with `crypto.randomUUID()` (the idiom in `queries/payouts.ts`); `apiFetch` sets the header from its `idempotencyKey` option.
- **Query keys:** `['admin','finance','overview']`, `['admin','finance','ledger', type, q, page]`, `['admin','finance','dispute', id]`, `['admin','finance','payouts']`.
- **A test named for a behavior must assert that behavior** — an "omits the param" test asserts its absence; an "idempotent" test asserts the header. (Two tests earlier in this project were named for something they never checked.)
- **Mapper outputs match `Frontend/src/lib/admin-data.ts` types exactly:** `Finance`, `PendingPayout`, `ProviderMix`, `Dispute`, `LedgerTxn`, `LedgerType`, `TimelineEntry`.
- **New imports in the TOP import block; `import type`** for type-only imports (`verbatimModuleSyntax`).
- **Toast tones** are `'success' | 'error' | 'info'`; preserve every existing toast's tone and copy verbatim. Failures toast `'error'`.
- **Category B (do NOT wire):** ledger **Export CSV** (no endpoint, keeps its toast); the overview `?range=` selector (no UI control — omit the param, server defaults to `7d`); partial refunds (the modal collects no amount); the dispute overview list's `amount`/`opened` (mapped but never rendered — `DisputeRow` shows only `kind · subject · detail`); `PayoutBatchView`/`PayoutTxnView` response bodies (actions only toast + refetch).

### The three documented micro-changes

1. **Ledger "Net in view" is now the net of the visible page.** It previously summed every filtered row (across all pages) because the whole mock array was in memory. With server-side paging the client holds one page, and the endpoint returns `total` as a *count*, not a sum — there is no server-provided net. Summing the visible page matches the label and is the only honest option.
2. **A sent payout disappears from the list** instead of showing a greyed "sent" pill. The endpoint returns only payable withdrawals (`ready | kyc_pending`), so a paid one leaves the result set on refetch. The `sent` pill branch in `PayoutRow` becomes unreachable — leave it (the field stays optional and is simply never set).
3. **Dispute timeline wording** shifts from the mock's `"3 days ago"` to `"3d ago"`, reusing the existing `relativeTimeAgo`, matching the action logs already shipped on the Users and Catalog detail screens.

---

### Task 1: `monthDay` date helper + `toCedis` money normalizer

**Files:**
- Modify: `Frontend/src/lib/format.ts`
- Test: `Frontend/src/lib/format.test.ts`

**Interfaces:**
- Produces (used by Tasks 2, 3, 6, 7, 8): `monthDay(iso: string): string`, `toCedis(wire: number | { amount: number } | null | undefined): number`.

- [ ] **Step 1: Write the failing tests**

Append to `Frontend/src/lib/format.test.ts` (extend the existing top import from `./format` with `monthDay, toCedis`):

```ts
describe('monthDay', () => {
  it('formats an ISO timestamp as short month + day', () => {
    expect(monthDay('2026-04-22T10:00:00Z')).toBe('Apr 22')
    expect(monthDay('2026-05-02T23:59:00Z')).toBe('May 02')
  })

  it('returns an empty string for an unparseable value', () => {
    expect(monthDay('not-a-date')).toBe('')
    expect(monthDay('')).toBe('')
  })
})

describe('toCedis', () => {
  it('passes a bare number through (overview + ledger shape)', () => {
    expect(toCedis(842000)).toBe(842000)
    expect(toCedis(-42.5)).toBe(-42.5)
  })

  it('unwraps the { amount, currency } MoneyView shape (disputes + payouts)', () => {
    expect(toCedis({ amount: 18.99, currency: 'GHS' })).toBe(18.99)
  })

  it('treats null/undefined as 0 so money never renders NaN', () => {
    expect(toCedis(null)).toBe(0)
    expect(toCedis(undefined)).toBe(0)
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/format.test.ts`
Expected: FAIL — `monthDay` / `toCedis` are not exported.

- [ ] **Step 3: Implement both helpers at the end of `Frontend/src/lib/format.ts`**

```ts
/**
 * Formats an ISO-8601 timestamp as the admin screens' short date label, e.g. `"Apr 22"`.
 * Returns `''` for an unparseable value so a bad timestamp never renders "Invalid Date".
 * (`monthYear` above yields `"Apr 2026"`; the finance ledger and dispute screens want day precision.)
 */
export function monthDay(iso: string): string {
  const ms = Date.parse(iso)
  if (!Number.isFinite(ms)) return ''
  return new Date(ms).toLocaleDateString('en-US', { month: 'short', day: '2-digit', timeZone: 'UTC' })
}

/**
 * Normalizes a wire money value to a plain number of cedis.
 *
 * The finance endpoints serve money in TWO shapes: the overview and ledger send a bare
 * `BigDecimal` (a plain JSON number of cedis), while the disputes and payouts endpoints send the
 * `MoneyView` envelope `{ amount, currency }`. Normalizing here lets each screen keep its own
 * existing display helper unchanged. Missing values become 0 rather than NaN.
 */
export function toCedis(wire: number | { amount: number } | null | undefined): number {
  if (wire == null) return 0
  const value = typeof wire === 'number' ? wire : wire.amount
  return Number.isFinite(value) ? value : 0
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/format.test.ts`
Expected: PASS.

- [ ] **Step 5: Typecheck**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build`
Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/lib/format.ts Frontend/src/lib/format.test.ts
git commit -m "feat(admin): monthDay date helper + toCedis money normalizer"
```

---

### Task 2: Finance overview mappers

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Consumes: `Finance`, `PendingPayout`, `ProviderMix`, `Dispute` from `../admin-data`; `toCedis` from `../format`.
- Produces (used by Task 5/6): types `FinanceKpisWire`, `PendingPayoutSummaryWire`, `ProviderMixWire`, `DisputeSummaryWire`, `FinanceOverviewWire`; function `toFinanceOverview(w: FinanceOverviewWire): Finance`.

- [ ] **Step 1: Write the failing tests**

Extend the top import block of `Frontend/src/lib/api/mappers.test.ts` with `toFinanceOverview, type FinanceOverviewWire` from `./mappers`, then append:

```ts
describe('finance overview mapper', () => {
  const wire: FinanceOverviewWire = {
    kpis: { gmvMtd: 842000.0, gmvDelta: 12, platformFee: 252600.0, feeTakePct: 30, payoutsDue: 42180.5, payoutsArtists: 318, momoFloat: 96000.0 },
    pendingPayouts: [{ id: 'p1', artist: 'Black Sherif', amount: 12400.0, method: 'MoMo · MTN', status: 'ready' }],
    providerMix: [{ name: 'MTN', value: 62 }, { name: 'Voda', value: 24 }],
    disputes: [{ id: 'd1', kind: 'Refund request', subject: '@ama_b', detail: 'Album not delivered', amount: 18.99, opened: '2026-04-22T10:00:00Z' }],
  }

  it('maps kpis as plain cedis numbers', () => {
    const f = toFinanceOverview(wire)
    expect(f.kpis).toEqual({ gmvMtd: 842000, gmvDelta: 12, platformFee: 252600, feeTakePct: 30, payoutsDue: 42180.5, payoutsArtists: 318, momoFloat: 96000 })
  })

  it('maps pending payouts and narrows the status union', () => {
    const f = toFinanceOverview(wire)
    expect(f.pendingPayouts).toEqual([{ id: 'p1', artist: 'Black Sherif', amount: 12400, method: 'MoMo · MTN', status: 'ready' }])
  })

  it('maps provider mix 1:1 and converts each dispute opened date to a short label', () => {
    const f = toFinanceOverview(wire)
    expect(f.providerMix).toEqual([{ name: 'MTN', value: 62 }, { name: 'Voda', value: 24 }])
    expect(f.disputes[0]).toEqual({ id: 'd1', kind: 'Refund request', subject: '@ama_b', detail: 'Album not delivered', amount: 18.99, opened: 'Apr 22' })
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/mappers.test.ts`
Expected: FAIL — `toFinanceOverview` is not exported.

- [ ] **Step 3: Extend the existing import lines at the top of `mappers.ts`**

Add the finance types to the existing `../admin-data` type import, and `toCedis` + `monthDay` to the existing `../format` import:

```ts
import type { Finance, PendingPayout, ProviderMix, Dispute } from '../admin-data'
```
(extend in place — do not add a second `../admin-data` or `../format` import line)

- [ ] **Step 4: Append the wire types + mapper at the END of `mappers.ts`**

```ts
// ── Admin finance overview (AdminFinanceOverviewResource) ─────────────────────
// Money on THIS endpoint is a bare BigDecimal of cedis (a plain JSON number), unlike the
// disputes/payouts endpoints which use the { amount, currency } MoneyView envelope.
export interface FinanceKpisWire {
  gmvMtd: number
  gmvDelta: number
  platformFee: number
  feeTakePct: number
  payoutsDue: number
  payoutsArtists: number
  momoFloat: number
}
export interface PendingPayoutSummaryWire { id: string; artist: string; amount: number; method: string; status: string }
export interface ProviderMixWire { name: string; value: number }
export interface DisputeSummaryWire {
  id: string
  kind: string
  subject: string
  detail: string
  amount: number | null
  opened: string | null
}
export interface FinanceOverviewWire {
  kpis: FinanceKpisWire
  pendingPayouts: PendingPayoutSummaryWire[]
  providerMix: ProviderMixWire[]
  disputes: DisputeSummaryWire[]
}

export function toFinanceOverview(w: FinanceOverviewWire): Finance {
  return {
    kpis: {
      gmvMtd: toCedis(w.kpis.gmvMtd),
      gmvDelta: w.kpis.gmvDelta,
      platformFee: toCedis(w.kpis.platformFee),
      feeTakePct: w.kpis.feeTakePct,
      payoutsDue: toCedis(w.kpis.payoutsDue),
      payoutsArtists: w.kpis.payoutsArtists,
      momoFloat: toCedis(w.kpis.momoFloat),
    },
    pendingPayouts: w.pendingPayouts.map(
      (p): PendingPayout => ({
        id: p.id,
        artist: p.artist,
        amount: toCedis(p.amount),
        method: p.method,
        status: p.status as PendingPayout['status'],
      }),
    ),
    providerMix: w.providerMix.map((m): ProviderMix => ({ name: m.name, value: m.value })),
    disputes: w.disputes.map(
      (d): Dispute => ({
        id: d.id,
        kind: d.kind,
        subject: d.subject,
        detail: d.detail,
        amount: d.amount == null ? undefined : toCedis(d.amount),
        opened: d.opened ? monthDay(d.opened) : undefined,
      }),
    ),
  }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/mappers.test.ts`
Expected: PASS.

- [ ] **Step 6: Typecheck**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build`
Expected: 0 errors.

- [ ] **Step 7: Commit**

```bash
git add Frontend/src/lib/api/mappers.ts Frontend/src/lib/api/mappers.test.ts
git commit -m "feat(admin): finance overview wire mappers"
```

---

### Task 3: Ledger, pending-payout, and dispute mappers

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Consumes: `LedgerTxn`, `LedgerType`, `PendingPayout`, `TimelineEntry` from `../admin-data`; `toCedis`, `monthDay`, `relativeTimeAgo` from `../format`.
- Produces (used by Task 5/6/7/8): types `MoneyWire`, `LedgerEntryWire`, `LedgerPageWire`, `LedgerPage`, `PendingPayoutWire`, `DisputeTimelineWire`, `DisputeDetailWire`, `DisputeDetail`; functions `toLedgerEntry`, `toLedgerPage`, `toPendingPayout`, `toDisputeStatus`, `toDisputeDetail`.

- [ ] **Step 1: Write the failing tests**

Extend the top import from `./mappers` in `mappers.test.ts` with `toLedgerPage, toPendingPayout, toDisputeStatus, toDisputeDetail, type LedgerPageWire, type DisputeDetailWire`, then append:

```ts
describe('ledger mappers', () => {
  it('maps a page: signed amounts, short dates, display-token types, and the server total', () => {
    const wire: LedgerPageWire = {
      items: [
        { id: 'l1', date: '2026-05-02T08:00:00Z', type: 'Sale', party: 'Black Sherif', ref: 'BZ-1', amount: 2.5 },
        { id: 'l2', date: null, type: 'Payout', party: 'DJ Kojo', ref: 'BZ-2', amount: -42180 },
      ],
      page: 2, size: 8, total: 137,
    }
    const p = toLedgerPage(wire)
    expect(p.total).toBe(137)
    expect(p.page).toBe(2)
    expect(p.items[0]).toEqual({ id: 'l1', date: 'May 02', type: 'Sale', party: 'Black Sherif', ref: 'BZ-1', amount: 2.5 })
    expect(p.items[1].amount).toBe(-42180)
    expect(p.items[1].date).toBe('')
  })
})

describe('pending payout mapper', () => {
  it('unwraps the MoneyView envelope (this endpoint differs from the overview)', () => {
    const p = toPendingPayout({ id: 'p1', artist: 'Fido', amount: { amount: 9400.5, currency: 'GHS' }, method: 'MoMo · MTN', status: 'kyc_pending' })
    expect(p).toEqual({ id: 'p1', artist: 'Fido', amount: 9400.5, method: 'MoMo · MTN', status: 'kyc_pending' })
  })
})

describe('dispute detail mapper', () => {
  it('maps the four wire statuses onto the UI two, with escalated still open', () => {
    expect(toDisputeStatus('open')).toBe('open')
    expect(toDisputeStatus('escalated')).toBe('open')
    expect(toDisputeStatus('refunded')).toBe('resolved')
    expect(toDisputeStatus('rejected')).toBe('resolved')
    expect(toDisputeStatus('something-new')).toBe('open')
  })

  it('unwraps MoneyView, shortens opened, and renders timeline times as relative', () => {
    const wire: DisputeDetailWire = {
      id: 'd1', kind: 'Refund request', subject: '@ama_b', detail: 'Album not delivered',
      amount: { amount: 18.99, currency: 'GHS' }, status: 'open', opened: '2026-04-22T10:00:00Z',
      timeline: [{ id: 't1', text: 'Dispute opened by fan', time: '2026-04-22T10:00:00Z' }],
    }
    const d = toDisputeDetail(wire, Date.parse('2026-04-25T10:00:00Z'))
    expect(d.kind).toBe('Refund request')
    expect(d.amount).toBe(18.99)
    expect(d.opened).toBe('Apr 22')
    expect(d.status).toBe('open')
    expect(d.timeline).toEqual([{ id: 't1', text: 'Dispute opened by fan', time: '3d ago' }])
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/mappers.test.ts`
Expected: FAIL — the four new functions are not exported.

- [ ] **Step 3: Extend the top import lines of `mappers.ts`**

Add `LedgerTxn`, `LedgerType`, `TimelineEntry` to the existing `../admin-data` type import, and `relativeTimeAgo` to the existing `../format` import if not already present.

- [ ] **Step 4: Append at the END of `mappers.ts`**

```ts
// ── Admin finance ledger / payouts / disputes ─────────────────────────────────
/** The `MoneyView` envelope used by the disputes + payouts endpoints. */
export interface MoneyWire { amount: number; currency: string }

export interface LedgerEntryWire {
  id: string
  date: string | null
  type: string
  party: string
  ref: string
  amount: number
}
export interface LedgerPageWire { items: LedgerEntryWire[]; page: number; size: number; total: number }
export interface LedgerPage { items: LedgerTxn[]; page: number; size: number; total: number }

export function toLedgerEntry(w: LedgerEntryWire): LedgerTxn {
  return {
    id: w.id,
    // `type` arrives via LedgerType.display(), which returns the UI's exact tokens — a cast is right.
    type: w.type as LedgerType,
    party: w.party,
    ref: w.ref,
    date: w.date ? monthDay(w.date) : '',
    amount: toCedis(w.amount),
  }
}

export function toLedgerPage(w: LedgerPageWire): LedgerPage {
  return { items: w.items.map(toLedgerEntry), page: w.page, size: w.size, total: w.total }
}

export interface PendingPayoutWire {
  id: string
  artist: string
  amount: MoneyWire
  method: string
  status: string
}

export function toPendingPayout(w: PendingPayoutWire): PendingPayout {
  return {
    id: w.id,
    artist: w.artist,
    amount: toCedis(w.amount),
    method: w.method,
    status: w.status as PendingPayout['status'],
  }
}

export interface DisputeTimelineWire { id: string; text: string; time: string | null }
export interface DisputeDetailWire {
  id: string
  kind: string
  subject: string
  detail: string
  amount: MoneyWire | null
  status: string
  opened: string | null
  timeline: DisputeTimelineWire[]
}
export interface DisputeDetail {
  id: string
  kind: string
  subject: string
  detail: string
  amount?: number
  status: 'open' | 'resolved'
  opened?: string
  timeline: TimelineEntry[]
}

/**
 * The wire carries four statuses (`open|refunded|rejected|escalated`); this screen shows two.
 * `escalated` maps to `open` on purpose — an escalated dispute is still open work, and this
 * matches the existing behaviour where Escalate does not resolve the dispute. Unknown values
 * fall back to `open` so a new backend status never reads as resolved.
 */
export function toDisputeStatus(wire: string): 'open' | 'resolved' {
  return wire === 'refunded' || wire === 'rejected' ? 'resolved' : 'open'
}

export function toDisputeDetail(w: DisputeDetailWire, now?: number): DisputeDetail {
  return {
    id: w.id,
    kind: w.kind,
    subject: w.subject,
    detail: w.detail,
    amount: w.amount == null ? undefined : toCedis(w.amount),
    status: toDisputeStatus(w.status),
    opened: w.opened ? monthDay(w.opened) : undefined,
    timeline: w.timeline.map(
      (t): TimelineEntry => ({ id: t.id, text: t.text, time: t.time ? relativeTimeAgo(t.time, now) : '' }),
    ),
  }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/mappers.test.ts`
Expected: PASS.

- [ ] **Step 6: Typecheck**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build`
Expected: 0 errors.

- [ ] **Step 7: Commit**

```bash
git add Frontend/src/lib/api/mappers.ts Frontend/src/lib/api/mappers.test.ts
git commit -m "feat(admin): ledger, pending-payout, and dispute wire mappers"
```

---

### Task 4: `useServerPaged` hook

**Files:**
- Modify: `Frontend/src/components/admin/pagination.tsx`
- Test: `Frontend/src/components/admin/pagination.test.ts`

**Interfaces:**
- Produces (used by Task 7): `useServerPaged<T>(args: { items: T[]; total: number; page: number; setPage: (p: number) => void; size: number }): Paged<T>`.

**Why:** `Pagination` renders from the `Paged<T>` interface. Producing that same shape from server data means `Pagination` and the existing `usePaged` need **zero** changes, so the three shipped screens using them cannot regress.

- [ ] **Step 1: Write the failing test**

Create `Frontend/src/components/admin/pagination.test.ts`:

```ts
import { describe, expect, it, vi } from 'vitest'
import { useServerPaged } from './pagination'

describe('useServerPaged', () => {
  const setPage = vi.fn()

  it('derives pageCount from the server total, not the item count', () => {
    const p = useServerPaged({ items: [1, 2, 3, 4, 5, 6, 7, 8], total: 137, page: 2, setPage, size: 8 })
    expect(p.pageCount).toBe(18)
    expect(p.total).toBe(137)
    expect(p.page).toBe(2)
  })

  it('passes the server-sliced items straight through as the page items', () => {
    const p = useServerPaged({ items: ['a', 'b'], total: 2, page: 1, setPage, size: 8 })
    expect(p.pageItems).toEqual(['a', 'b'])
  })

  it('never reports fewer than one page, even when empty', () => {
    const p = useServerPaged({ items: [], total: 0, page: 1, setPage, size: 8 })
    expect(p.pageCount).toBe(1)
    expect(p.pageItems).toEqual([])
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/components/admin/pagination.test.ts`
Expected: FAIL — `useServerPaged` is not exported.

- [ ] **Step 3: Add the hook to `pagination.tsx`, directly below the existing `usePaged`**

```tsx
/**
 * The server-driven twin of {@link usePaged}: the endpoint already sliced the page, so `items` IS
 * the page and `pageCount` comes from the server's `total`. Returns the same {@link Paged} shape,
 * so `Pagination` renders it without any change. The caller owns `page` state (and resets it to 1
 * when its filters change).
 */
export function useServerPaged<T>({
  items,
  total,
  page,
  setPage,
  size,
}: {
  items: T[]
  total: number
  page: number
  setPage: (p: number) => void
  size: number
}): Paged<T> {
  const pageCount = Math.max(1, Math.ceil(total / size))
  return { page, setPage, pageCount, pageItems: items, total, size }
}
```

- [ ] **Step 4: Run the test to verify it passes, plus the full suite (shared component)**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run`
Expected: PASS — the new file green and every existing test still green.

- [ ] **Step 5: Typecheck**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build`
Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/components/admin/pagination.tsx Frontend/src/components/admin/pagination.test.ts
git commit -m "feat(admin): useServerPaged hook for server-driven pagination"
```

---

### Task 5: Finance query + mutation layer

**Files:**
- Create: `Frontend/src/lib/api/queries/admin-finance.ts`
- Test: `Frontend/src/lib/api/queries/admin-finance.test.ts`

**Interfaces:**
- Consumes: `toFinanceOverview`, `toLedgerPage`, `toPendingPayout`, `toDisputeDetail` + their wire types from `../mappers`; `apiFetch` from `../client`.
- Produces (used by Tasks 6/7/8): `financeOverviewQuery()`, `ledgerQuery(type, q, page)`, `disputeQuery(id)`, `pendingPayoutsQuery()`, `apiRefundDispute(id, reason)`, `apiRejectDispute(id, reason)`, `apiEscalateDispute(id)`, `apiRunWeeklyPayouts()`, `apiSendPayout(id)`.

- [ ] **Step 1: Write the failing test**

Create `Frontend/src/lib/api/queries/admin-finance.test.ts`:

```ts
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  financeOverviewQuery, ledgerQuery, disputeQuery, pendingPayoutsQuery,
  apiRefundDispute, apiRejectDispute, apiEscalateDispute, apiRunWeeklyPayouts, apiSendPayout,
} from './admin-finance'

const overviewWire = {
  kpis: { gmvMtd: 1, gmvDelta: 2, platformFee: 3, feeTakePct: 4, payoutsDue: 5, payoutsArtists: 6, momoFloat: 7 },
  pendingPayouts: [], providerMix: [], disputes: [],
}
const ledgerWire = { items: [], page: 1, size: 8, total: 0 }
const disputeWire = {
  id: 'd1', kind: 'Refund request', subject: '@a', detail: 'x',
  amount: { amount: 1, currency: 'GHS' }, status: 'open', opened: null, timeline: [],
}

function mockFetch(status: number, json: unknown) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300, status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => json, text: async () => JSON.stringify(json),
  } as Response)
}
afterEach(() => vi.restoreAllMocks())

describe('admin-finance reads', () => {
  it('financeOverviewQuery hits /v1/admin/finance with no range param', async () => {
    const f = mockFetch(200, overviewWire); vi.stubGlobal('fetch', f)
    const r = await financeOverviewQuery().queryFn!({} as never)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/finance')
    expect(r.kpis.gmvMtd).toBe(1)
    expect(financeOverviewQuery().queryKey).toEqual(['admin', 'finance', 'overview'])
  })

  it('ledgerQuery sends page + size and OMITS type/q when unset', async () => {
    const f = mockFetch(200, ledgerWire); vi.stubGlobal('fetch', f)
    await ledgerQuery('all', '', 3).queryFn!({} as never)
    const url = f.mock.calls[0][0] as string
    expect(url).toContain('page=3')
    expect(url).toContain('size=8')
    expect(url).not.toContain('type=')
    expect(url).not.toContain('q=')
    expect(ledgerQuery('all', '', 3).queryKey).toEqual(['admin', 'finance', 'ledger', 'all', '', 3])
  })

  it('ledgerQuery sends type and q when set', async () => {
    const f = mockFetch(200, ledgerWire); vi.stubGlobal('fetch', f)
    await ledgerQuery('Payout', 'kojo', 1).queryFn!({} as never)
    const url = f.mock.calls[0][0] as string
    expect(url).toContain('type=Payout')
    expect(url).toContain('q=kojo')
  })

  it('disputeQuery hits /disputes/:id and keys by id', async () => {
    const f = mockFetch(200, disputeWire); vi.stubGlobal('fetch', f)
    const r = await disputeQuery('d1').queryFn!({} as never)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/finance/disputes/d1')
    expect(r.status).toBe('open')
    expect(disputeQuery('d1').queryKey).toEqual(['admin', 'finance', 'dispute', 'd1'])
  })

  it('pendingPayoutsQuery hits /payouts and maps the MoneyView envelope', async () => {
    const f = mockFetch(200, [{ id: 'p1', artist: 'A', amount: { amount: 12.5, currency: 'GHS' }, method: 'MoMo', status: 'ready' }])
    vi.stubGlobal('fetch', f)
    const r = await pendingPayoutsQuery().queryFn!({} as never)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/finance/payouts')
    expect(r[0].amount).toBe(12.5)
    expect(pendingPayoutsQuery().queryKey).toEqual(['admin', 'finance', 'payouts'])
  })
})

describe('admin-finance mutations', () => {
  it('apiRefundDispute POSTs a reason, no amount (full refund), WITH an Idempotency-Key', async () => {
    const f = mockFetch(200, disputeWire); vi.stubGlobal('fetch', f)
    await apiRefundDispute('d1', 'Refunded · dispute closed')
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/finance/disputes/d1/refund')
    expect(opts.method).toBe('POST')
    expect(JSON.parse(opts.body)).toEqual({ reason: 'Refunded · dispute closed' })
    expect(opts.headers['Idempotency-Key']).toBeTruthy()
  })

  it('apiRejectDispute POSTs a reason and needs no Idempotency-Key', async () => {
    const f = mockFetch(200, disputeWire); vi.stubGlobal('fetch', f)
    await apiRejectDispute('d1', 'evidence sufficient')
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/finance/disputes/d1/reject')
    expect(JSON.parse(opts.body)).toEqual({ reason: 'evidence sufficient' })
  })

  it('apiEscalateDispute POSTs to /escalate', async () => {
    const f = mockFetch(200, disputeWire); vi.stubGlobal('fetch', f)
    await apiEscalateDispute('d1')
    expect(f.mock.calls[0][0]).toBe('/v1/admin/finance/disputes/d1/escalate')
  })

  it('apiRunWeeklyPayouts POSTs WITH an Idempotency-Key', async () => {
    const f = mockFetch(200, {}); vi.stubGlobal('fetch', f)
    await apiRunWeeklyPayouts()
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/finance/payouts/run-weekly')
    expect(opts.method).toBe('POST')
    expect(opts.headers['Idempotency-Key']).toBeTruthy()
  })

  it('apiSendPayout POSTs WITH an Idempotency-Key', async () => {
    const f = mockFetch(200, {}); vi.stubGlobal('fetch', f)
    await apiSendPayout('w1')
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/finance/payouts/w1/send')
    expect(opts.headers['Idempotency-Key']).toBeTruthy()
  })

  it('generates a DIFFERENT Idempotency-Key per call so retries are not collapsed', async () => {
    const f = mockFetch(200, {}); vi.stubGlobal('fetch', f)
    await apiSendPayout('w1')
    await apiSendPayout('w1')
    expect(f.mock.calls[0][1].headers['Idempotency-Key']).not.toBe(f.mock.calls[1][1].headers['Idempotency-Key'])
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/queries/admin-finance.test.ts`
Expected: FAIL — `./admin-finance` does not exist.

- [ ] **Step 3: Write the query module**

Create `Frontend/src/lib/api/queries/admin-finance.ts`:

```ts
import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import {
  toFinanceOverview, toLedgerPage, toPendingPayout, toDisputeDetail,
  type FinanceOverviewWire, type LedgerPageWire, type PendingPayoutWire, type DisputeDetailWire,
} from '../mappers'

/** Rows per ledger page — matches the shared paginator's default so the control looks unchanged. */
export const LEDGER_PAGE_SIZE = 8

/**
 * `GET /v1/admin/finance` — KPIs, pending payouts, provider mix, and open disputes.
 * No `range` param is sent: this screen has no range control, so the server's `7d` default applies.
 */
export function financeOverviewQuery() {
  return queryOptions({
    queryKey: ['admin', 'finance', 'overview'],
    queryFn: async () => toFinanceOverview(await apiFetch<FinanceOverviewWire>('/admin/finance')),
  })
}

/**
 * `GET /v1/admin/finance/ledger` — one server-paged slice of the book. `type`/`q` are sent only
 * when set; the server treats a blank/unknown `type` as "no filter" rather than a 422.
 */
export function ledgerQuery(type: string, q: string, page: number) {
  const params = new URLSearchParams({ page: String(page), size: String(LEDGER_PAGE_SIZE) })
  if (type !== 'all') params.set('type', type)
  if (q) params.set('q', q)
  return queryOptions({
    queryKey: ['admin', 'finance', 'ledger', type, q, page],
    queryFn: async () => toLedgerPage(await apiFetch<LedgerPageWire>(`/admin/finance/ledger?${params}`)),
  })
}

/** `GET /v1/admin/finance/disputes/:id` — one dispute with its server-side timeline. */
export function disputeQuery(id: string) {
  return queryOptions({
    queryKey: ['admin', 'finance', 'dispute', id],
    queryFn: async () => toDisputeDetail(await apiFetch<DisputeDetailWire>(`/admin/finance/disputes/${id}`)),
  })
}

/** `GET /v1/admin/finance/payouts` — payable withdrawals (`ready` and `kyc_pending` only). */
export function pendingPayoutsQuery() {
  return queryOptions({
    queryKey: ['admin', 'finance', 'payouts'],
    queryFn: async () => (await apiFetch<PendingPayoutWire[]>('/admin/finance/payouts')).map(toPendingPayout),
  })
}

/**
 * `POST /v1/admin/finance/disputes/:id/refund` — a money POST, so an `Idempotency-Key` is REQUIRED
 * (a blank one is a 400). `amount` is omitted, which the backend treats as a FULL refund; the
 * confirm-only modal collects no partial amount.
 */
export function apiRefundDispute(id: string, reason: string): Promise<void> {
  return apiFetch<unknown>(`/admin/finance/disputes/${id}/refund`, {
    method: 'POST',
    body: { reason },
    idempotencyKey: crypto.randomUUID(),
  }).then(() => undefined)
}

/** `POST /v1/admin/finance/disputes/:id/reject` — `reason` is required (non-blank). */
export function apiRejectDispute(id: string, reason: string): Promise<void> {
  return apiFetch<unknown>(`/admin/finance/disputes/${id}/reject`, { method: 'POST', body: { reason } }).then(() => undefined)
}

/** `POST /v1/admin/finance/disputes/:id/escalate` — raises to senior finance; stays open. */
export function apiEscalateDispute(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/finance/disputes/${id}/escalate`, { method: 'POST' }).then(() => undefined)
}

/**
 * `POST /v1/admin/finance/payouts/run-weekly` — pays every ready, KYC-verified withdrawal.
 * Money POST: `Idempotency-Key` REQUIRED. The server's per-withdrawal exactly-once guard means a
 * retry cannot double-pay.
 */
export function apiRunWeeklyPayouts(): Promise<void> {
  return apiFetch<unknown>('/admin/finance/payouts/run-weekly', {
    method: 'POST',
    idempotencyKey: crypto.randomUUID(),
  }).then(() => undefined)
}

/**
 * `POST /v1/admin/finance/payouts/:id/send` — sends one payout (`id` is the withdrawal id).
 * Money POST: `Idempotency-Key` REQUIRED. Blocks with 409 when the artist's KYC is unverified.
 */
export function apiSendPayout(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/finance/payouts/${id}/send`, {
    method: 'POST',
    idempotencyKey: crypto.randomUUID(),
  }).then(() => undefined)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/queries/admin-finance.test.ts`
Expected: PASS (12 tests).

- [ ] **Step 5: Typecheck**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build`
Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/lib/api/queries/admin-finance.ts Frontend/src/lib/api/queries/admin-finance.test.ts
git commit -m "feat(admin): finance query + mutation layer (idempotent money POSTs)"
```

---

### Task 6: Wire the finance overview + payout actions

**Files:**
- Modify: `Frontend/src/routes/admin.finance.index.tsx`

**Interfaces:**
- Consumes: `financeOverviewQuery`, `pendingPayoutsQuery`, `apiSendPayout`, `apiRunWeeklyPayouts` from `../lib/api/queries/admin-finance`; `AdminLoadError` from `../components/admin/load-error`; `useQuery`, `useQueryClient` from `@tanstack/react-query`.

**Note on two sources:** the overview response carries a `pendingPayouts` summary, but the dedicated `GET /payouts` endpoint is the authoritative payable list (and the one the send/run actions mutate). Read the payout table from `pendingPayoutsQuery()` and everything else from `financeOverviewQuery()`.

- [ ] **Step 1: Replace the imports and the mock seed**

Replace the mock import line

```ts
import { getFinance, type PendingPayout, type ProviderMix, type Dispute } from '../lib/admin-data'
```

with:

```ts
import { useQuery, useQueryClient } from '@tanstack/react-query'
import type { PendingPayout, ProviderMix, Dispute } from '../lib/admin-data'
import { financeOverviewQuery, pendingPayoutsQuery, apiSendPayout, apiRunWeeklyPayouts } from '../lib/api/queries/admin-finance'
import { AdminLoadError } from '../components/admin/load-error'
```

Replace the component's state block (the `base` / `k` / `payouts` / `send` / `runWeekly` block) with:

```tsx
  const { toast } = useToast()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const overview = useQuery(financeOverviewQuery())
  const payoutsQ = useQuery(pendingPayoutsQuery())

  const k = overview.data?.kpis ?? {
    gmvMtd: 0, gmvDelta: 0, platformFee: 0, feeTakePct: 0, payoutsDue: 0, payoutsArtists: 0, momoFloat: 0,
  }
  const payouts: (PendingPayout & { sent?: boolean })[] = payoutsQ.data ?? []
  const providerMix = overview.data?.providerMix ?? []
  const disputes = overview.data?.disputes ?? []

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin', 'finance'] })

  const send = async (id: string) => {
    const p = payouts.find((x) => x.id === id)
    if (!p) return
    if (p.status === 'kyc_pending') { toast(`Resolve KYC for ${p.artist} before paying`, 'error'); return }
    try {
      await apiSendPayout(id)
      await refresh()
      toast(`Sent ${full(p.amount)} to ${p.artist}`, 'success')
    } catch { toast(`Could not send payout to ${p.artist}`, 'error') }
  }

  const runWeekly = async () => {
    const ready = payouts.filter((p) => p.status === 'ready')
    if (ready.length === 0) { toast('No ready payouts to run', 'info'); return }
    try {
      await apiRunWeeklyPayouts()
      await refresh()
      toast(`Weekly payout run · ${ready.length} artists paid`, 'success')
    } catch { toast('Could not run the weekly payout', 'error') }
  }
```

Notes: the client-side KYC pre-check is kept so the exact existing error toast still fires; the server's 409 is the real guard. `refresh()` uses the `['admin','finance']` prefix so both the overview and the payout list refetch after a money action.

- [ ] **Step 2: Point the JSX at the new variables and add the error states**

`ProviderBars` and the disputes section switch from `base.*`:

```tsx
            <ProviderBars mix={providerMix} />
```

```tsx
            <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Disputes · {disputes.length} open</h2>
            {disputes.map((d) => <DisputeRow key={d.id} dispute={d} onOpen={() => navigate({ to: '/admin/finance/dispute/$disputeId', params: { disputeId: d.id } })} />)}
```

In the pending-payouts card, replace the bare `{payouts.map(...)}` line with an error/loading/empty/rows ladder:

```tsx
              {payoutsQ.isError ? (
                <AdminLoadError label="Couldn't load pending payouts." onRetry={() => payoutsQ.refetch()} />
              ) : payoutsQ.isLoading ? (
                <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
              ) : payouts.length === 0 ? (
                <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">No pending payouts.</div>
              ) : (
                payouts.map((p) => <PayoutRow key={p.id} payout={p} onSend={() => send(p.id)} />)
              )}
```

Everything else — the KPI tiles, `Kpi`, `PayoutRow`, `ProviderBars`, `DisputeRow`, all classes and copy — is unchanged. `PayoutRow`'s `sent` branch stays (now unreachable; see the plan's micro-change 2).

- [ ] **Step 3: Typecheck + full suite**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; vitest green.

- [ ] **Step 4: Commit**

```bash
git add Frontend/src/routes/admin.finance.index.tsx
git commit -m "feat(admin): wire finance overview + idempotent payout send/run-weekly"
```

---

### Task 7: Wire the ledger with server-side paging

**Files:**
- Modify: `Frontend/src/routes/admin.finance.ledger.tsx`

**Interfaces:**
- Consumes: `ledgerQuery`, `LEDGER_PAGE_SIZE` from `../lib/api/queries/admin-finance`; `useServerPaged`, `Pagination` from `../components/admin/pagination`; `AdminLoadError`; `useQuery`.

- [ ] **Step 1: Replace the imports**

Replace

```ts
import { getLedger, type LedgerTxn, type LedgerType } from '../lib/admin-data'
import { usePaged, Pagination } from '../components/admin/pagination'
```

with (note `usePaged` → `useServerPaged`, and `useEffect` is added to the react import for the search debounce):

```ts
import { useQuery } from '@tanstack/react-query'
import type { LedgerTxn, LedgerType } from '../lib/admin-data'
import { ledgerQuery, LEDGER_PAGE_SIZE } from '../lib/api/queries/admin-finance'
import { useServerPaged, Pagination } from '../components/admin/pagination'
import { AdminLoadError } from '../components/admin/load-error'
```

Also change the react import to `import { useEffect, useState } from 'react'` (`useMemo` is no longer used).

- [ ] **Step 2: Replace the component's data block**

Replace the `all` / `type` / `query` / `rows` / `net` / `paged` block with:

```tsx
  const { toast } = useToast()
  const [type, setType] = useState<LedgerType | 'all'>('all')
  const [query, setQuery] = useState('')
  const [debouncedQ, setDebouncedQ] = useState('')
  const [page, setPage] = useState(1)

  // Search is a server param, so debounce it: one request per pause, not per keystroke.
  useEffect(() => {
    const t = setTimeout(() => {
      setDebouncedQ(query.trim())
      setPage(1)
    }, 300)
    return () => clearTimeout(t)
  }, [query])

  const { data, isError, isLoading, refetch } = useQuery(ledgerQuery(type, debouncedQ, page))
  const rows: LedgerTxn[] = data?.items ?? []
  // "Net in view" is the net of the rows on screen: with server paging the client holds one page,
  // and the endpoint's `total` is a count, not a sum.
  const net = rows.reduce((s, t) => s + t.amount, 0)
  const paged = useServerPaged({ items: rows, total: data?.total ?? 0, page, setPage, size: LEDGER_PAGE_SIZE })
```

- [ ] **Step 3: Reset to page 1 when the type filter changes**

In the type-chip button, add the page reset alongside the existing `setType`:

```tsx
            <button key={t} onClick={() => { setType(t); setPage(1) }}
```

(Everything else about that button — classes, label expression — is unchanged.)

- [ ] **Step 4: Add the error/loading states to the table body**

Replace the `rows.length === 0 ? (...) : paged.pageItems.map(...)` expression with:

```tsx
            {isError ? (
              <AdminLoadError label="Couldn't load the ledger." onRetry={() => refetch()} />
            ) : isLoading ? (
              <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
            ) : rows.length === 0 ? (
              <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">No matching entries.</div>
            ) : paged.pageItems.map((t) => <LedgerRow key={t.id} txn={t} />)}
```

The `Pagination paged={paged}` line, the Export button (Category B), the header, the net display, the search input, and `LedgerRow` are all unchanged.

- [ ] **Step 5: Typecheck + full suite**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; vitest green.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/routes/admin.finance.ledger.tsx
git commit -m "feat(admin): wire ledger to live endpoint with server-side paging"
```

---

### Task 8: Wire the dispute detail

**Files:**
- Modify: `Frontend/src/routes/admin.finance.dispute.$disputeId.tsx`

**Interfaces:**
- Consumes: `disputeQuery`, `apiRefundDispute`, `apiRejectDispute`, `apiEscalateDispute` from `../lib/api/queries/admin-finance`; `AdminLoadError`; `ApiError` from `../lib/api/errors`; `useQuery`, `useQueryClient`.

- [ ] **Step 1: Replace the imports**

Replace

```ts
import { getFinance, getDisputeTimeline, type Dispute } from '../lib/admin-data'
```

with:

```ts
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { disputeQuery, apiRefundDispute, apiRejectDispute, apiEscalateDispute } from '../lib/api/queries/admin-finance'
import { AdminLoadError } from '../components/admin/load-error'
import { ApiError } from '../lib/api/errors'
```

Also drop `useMemo` from the react import (only `useState` remains) and delete the now-unused local `interface Log`.

- [ ] **Step 2: Replace the component head (seed, guards, resolve)**

Replace everything from `const { disputeId } = Route.useParams()` down to and including the `resolve` definition with:

```tsx
  const { disputeId } = Route.useParams()
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const { data, isLoading, isError, error, refetch } = useQuery(disputeQuery(disputeId))
  const [refundOpen, setRefundOpen] = useState(false)

  if (isError) {
    const notFound = error instanceof ApiError && error.status === 404
    return notFound ? (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-24">
        <p className="text-sm text-gray-500 dark:text-gray-300">Dispute not found.</p>
        <Link to="/admin/finance" className="h-10 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center">Back to finance</Link>
      </div>
    ) : (
      <div className="py-24">
        <AdminLoadError label="Couldn't load this dispute." onRetry={() => refetch()} />
      </div>
    )
  }

  const d = data
  if (!d) {
    return <div className="py-24 text-center text-sm text-gray-400 dark:text-gray-500">{isLoading ? 'Loading…' : ''}</div>
  }

  const status = d.status
  const runAction = async (fn: () => Promise<void>, okMsg: string, errMsg: string, tone: 'success' | 'info' = 'success') => {
    try {
      await fn()
      await queryClient.invalidateQueries({ queryKey: ['admin', 'finance'] })
      toast(okMsg, tone)
    } catch { toast(errMsg, 'error') }
  }
  const reject = () => runAction(() => apiRejectDispute(d.id, 'Dispute rejected · evidence sufficient'), 'Dispute rejected · evidence sufficient', 'Could not reject the dispute')
  const escalate = () => runAction(() => apiEscalateDispute(d.id), 'Escalated to senior finance', 'Could not escalate the dispute', 'info')
  const refund = () => runAction(() => apiRefundDispute(d.id, `Refunded ${cedis(d.amount ?? 0)} · dispute closed`), `Refunded ${cedis(d.amount ?? 0)} · dispute closed`, 'Could not issue the refund')
```

Notes: `status` now comes from the server (`escalated` maps to `open`, so the action buttons stay visible after escalating — matching today's behavior). The local `setStatus`/`log` state and the `resolve` helper are gone; the timeline is server-authoritative. Invalidating the `['admin','finance']` prefix also refreshes the overview's dispute list.

- [ ] **Step 3: Point the JSX at `d` and the wired handlers**

The header/summary already read `d.*`, so only the action buttons and the timeline change. Buttons:

```tsx
              <button onClick={() => setRefundOpen(true)} className="h-10 px-4 rounded-full bg-beatz-green text-black text-sm font-bold flex items-center gap-2 hover:scale-105 transition-transform"><RotateCcw size={15} /> Refund</button>
              <button onClick={reject} className="h-10 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"><ShieldX size={15} /> Reject</button>
              <button onClick={escalate} className="h-10 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"><ArrowUpCircle size={15} /> Escalate</button>
```

Timeline — iterate the server timeline instead of `[...log, ...baseTimeline]`:

```tsx
            {d.timeline.map((t) => (
```

Refund modal — wire the confirm:

```tsx
      <RefundModal isOpen={refundOpen} amount={d.amount ?? 0} onClose={() => setRefundOpen(false)}
        onConfirm={() => { setRefundOpen(false); refund() }} />
```

`Meta`, `RefundModal`, the `cedis` helper, the funds-held notice, and every class/copy string are unchanged.

- [ ] **Step 4: Typecheck + full suite**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; vitest green.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/routes/admin.finance.dispute.\$disputeId.tsx
git commit -m "feat(admin): wire dispute detail (refund/reject/escalate + server timeline)"
```

---

### Task 9: Live QA + PR (USER-run gate)

**Files:** none (verification only). The controller does NOT run `verify.sh` (IntelliJ JPS races); CI is authoritative.

- [ ] **Step 1: Final full gate**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; full suite green.

- [ ] **Step 2: Live QA — requires a `finance` or `super-admin` account**

Every endpoint in this slice is `@RolesAllowed({finance, super-admin})`, so the existing `support`-role QA account cannot even read these screens. Promote the QA `admin_member` row to `finance` (or `super-admin`) and re-login for a fresh JWT.

**Run against the LOCAL dev DB only — the payout actions move real money and must never be exercised against production.**

  - Overview: KPI tiles show live figures; provider mix bars render; the disputes card lists open disputes and each row navigates to its detail.
  - Payouts: the table lists `ready` + `kyc_pending` rows. **Send** on a `ready` row succeeds and the row leaves the list on refetch; **Send** on a `kyc_pending` row shows the KYC error toast (client pre-check) — and if forced past it, the server answers 409.
  - **Run weekly payout** pays the ready set; re-running immediately must NOT double-pay (per-withdrawal exactly-once guard).
  - Ledger: rows show short dates + signed amounts; the type chips and the debounced search both refetch from the server; the paginator's page count reflects the server `total`, and paging fetches new rows. Confirm "Net in view" matches the visible page.
  - Dispute detail: summary + server timeline render; **Reject** and **Refund** both resolve the dispute; **Escalate** keeps it open with the action buttons still visible; each action's effect survives a refetch and the overview's dispute list updates.
  - Force a load error (stop the backend, refetch) → the distinct "Couldn't load …" + Retry affordance on each screen; a bad dispute id → "Dispute not found."

- [ ] **Step 3: Open the PR**

```bash
git push -u origin feat/frontend-admin-finance
gh pr create --base master --title "feat(admin): wire Finance overview, ledger, and dispute detail to live endpoints" --body "<DoD checklist; the two money-shape normalizations; the three documented micro-changes; the idempotency-key guarantee; Category-B list>"
```

---

## Notes for the executor

- **Branch:** `feat/frontend-admin-finance` (already created off `master` post-#166; spec `60d747b`). BASE for the first review package is the plan commit.
- **Do NOT** touch backend or stage backend secrets. Frontend-only branch.
- **Already on `master` — import, do not recreate:** `AdminLoadError`, `ApiError` (in `lib/api/errors.ts`), and the `format.ts` helpers `relativeTime` / `relativeTimeAgo` / `monthYear` / `formatDuration`.
- **The finance resources live in the `payments` module**, not `admin` — irrelevant to the frontend, but do not go looking for them under `admin/`.
- Money is normalized to plain cedis numbers by the mappers, so the four per-screen display helpers (`compactCedis`, `full`, `signed`, `cedis`) stay exactly as they are.
