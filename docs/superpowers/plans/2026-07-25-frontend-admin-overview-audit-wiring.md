# Frontend Admin Overview, Audit & Health Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the admin dashboard, audit log, and system health from the `admin-data` mock to the live `AdminOverviewResource` / `AdminAuditResource` endpoints, replacing every fabricated figure with either real data or an honest empty state.

**Architecture:** Same idiom as the merged admin slices: one `queries/admin-overview.ts` (three `queryOptions` reads, **no mutations**), wire types + `toX` mappers in the shared `lib/api/mappers.ts`, routes swapped to `useQuery` with `AdminLoadError` + loading branches. Audit uses `useServerPaged` (from the stacked finance branch) for real server-side paging.

**Tech Stack:** React 18, TanStack Query v5, TanStack Router, Vitest, TypeScript (`tsc -b`), Tailwind.

## Global Constraints

- **Branch is STACKED on `feat/frontend-admin-finance`** (PR #167) for `useServerPaged`. Do not re-implement that hook.
- **Node 22 via nvm** for all `npm`/`npx`: prefix with `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null &&`.
- **Real typecheck gate is `npm run build`** (`tsc -b`), not vitest. Every task runs both.
- **NEVER stage** `backend/src/main/resources/application.properties` or `backend/docker-compose.yml`. Frontend-only — do not touch backend.
- **Money on these endpoints is a bare `BigDecimal` of cedis** (a plain JSON number), matching the mock's convention. No `{amount,currency}` envelope. Pass through as numbers.
- **Query keys:** `['admin','overview', range]`, `['admin','health']`, `['admin','audit', type, q, page]`.
- **This slice has NO mutations** — three reads only. No idempotency concerns.
- **New imports in the TOP import block; `import type`** for type-only imports.
- **A test named for a behavior must assert that behavior** (the recurring defect in this project — an "omits the param" test must assert absence).
- **Category B — always empty/fixed server-side, must render honest empty states, never fabricated values:** `needsAttention` (`List.of()`), `paymentMethods` (`List.of()`), `deltas.users` (always `0`), and the ENTIRE health payload (`status:"normal"` + three empty arrays).
- **Audit is `super-admin` only**; overview and health accept all five admin roles.
- Audit **Export CSV** stays a toast (no endpoint).

### The six deliberate deviations (from the spec — implement exactly these, no more)

1. "Needs attention" and "Payment methods" render empty states.
2. Health renders honest-empty (status pill + three empty sections).
3. GMV bars normalize client-side; the tooltip shows the **real** cedis amount instead of an invented one.
4. The KPI delta arrow gains a sign (down-arrow + red when negative).
5. Audit `target` reads `Type:id` and `time` becomes relative from a real timestamp.
6. The audit search box drives the server's `q` (action/target), no longer matching actor.

---

### Task 1: Overview + health + audit mappers

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Consumes: `AdminOverview`, `AdminRange`, `AttentionItem`, `RevenueArtist`, `PayMethod`, `Health`, `HealthMetric`, `Incident`, `AuditEntry`, `AuditType` from `../admin-data`; `relativeTimeAgo` from `../format`.
- Produces (used by Tasks 2–5): types `AdminOverviewWire`, `HealthWire`, `AuditEntryWire`, `AuditPageWire`, `AuditPage`; functions `toAdminOverview`, `toHealth`, `toAuditType`, `toAuditEntry`, `toAuditPage`.

- [ ] **Step 1: Write the failing tests**

Extend the top import from `./mappers` in `mappers.test.ts` with `toAdminOverview, toHealth, toAuditType, toAuditPage, type AdminOverviewWire, type HealthWire, type AuditPageWire`, then append:

```ts
describe('admin overview mapper', () => {
  const wire: AdminOverviewWire = {
    rangeLabel: 'last 7 days',
    kpis: { activeUsers: 1260, streams: 842000, gmv: 51580.5, newArtists: 12, deltas: { users: 0, streams: 15, gmv: -18 } },
    gmvByDay: [1200.5, 800, 0],
    needsAttention: [],
    topArtists: [{ name: 'Black Sherif', revenue: 42180 }],
    paymentMethods: [],
  }

  it('passes bare-cedis money through as plain numbers', () => {
    const o = toAdminOverview(wire)
    expect(o.kpis.gmv).toBe(51580.5)
    expect(o.topArtists).toEqual([{ name: 'Black Sherif', revenue: 42180 }])
    expect(o.gmvByDay).toEqual([1200.5, 800, 0])
  })

  it('preserves a NEGATIVE delta (the backend really produces these)', () => {
    expect(toAdminOverview(wire).kpis.deltas.gmv).toBe(-18)
  })

  it('maps the always-empty Category-B arrays as empty, not fabricated', () => {
    const o = toAdminOverview(wire)
    expect(o.needsAttention).toEqual([])
    expect(o.paymentMethods).toEqual([])
  })
})

describe('health mapper', () => {
  it('maps the honest-empty payload the backend always returns', () => {
    const h = toHealth({ status: 'normal', metrics: [], listeners: [], incidents: [] })
    expect(h).toEqual({ status: 'normal', metrics: [], listeners: [], incidents: [] })
  })

  it('narrows an unknown status to degraded rather than trusting it', () => {
    expect(toHealth({ status: 'something-else', metrics: [], listeners: [], incidents: [] }).status).toBe('degraded')
  })
})

describe('audit mappers', () => {
  it('narrows a known type and falls back to settings for an unknown one', () => {
    expect(toAuditType('finance')).toBe('finance')
    expect(toAuditType('brand-new-type')).toBe('settings')
  })

  it('maps a page: relative time, compound target, and the server total', () => {
    const wire: AuditPageWire = {
      items: [{ id: 'a1', actor: 'Admin · Yaa', action: 'Suspended account', target: 'AdminMember:acc-123', type: 'user', time: '2026-07-25T10:00:00Z' }],
      page: 2, size: 8, total: 91,
    }
    const p = toAuditPage(wire, Date.parse('2026-07-25T12:00:00Z'))
    expect(p.total).toBe(91)
    expect(p.page).toBe(2)
    expect(p.items[0]).toEqual({
      id: 'a1', actor: 'Admin · Yaa', action: 'Suspended account',
      target: 'AdminMember:acc-123', type: 'user', time: '2h ago',
    })
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/mappers.test.ts`
Expected: FAIL — the new functions are not exported.

- [ ] **Step 3: Extend the existing top import lines of `mappers.ts`**

Add the new admin-data types to the existing `../admin-data` type import (do NOT add a second import line from that module); `relativeTimeAgo` is already imported from `../format`.

- [ ] **Step 4: Append at the END of `mappers.ts`**

```ts
// ── Admin overview / health / audit ───────────────────────────────────────────
// Money on these endpoints is a bare BigDecimal of cedis (a plain JSON number),
// matching admin-data's convention — there is no { amount, currency } envelope here.
export interface AdminOverviewWire {
  rangeLabel: string
  kpis: {
    activeUsers: number
    streams: number
    gmv: number
    newArtists: number
    deltas: { users: number; streams: number; gmv: number }
  }
  gmvByDay: number[]
  needsAttention: { id: string; label: string; sub: string; to: string }[]
  topArtists: { name: string; revenue: number }[]
  paymentMethods: { name: string; value: number }[]
}

export function toAdminOverview(w: AdminOverviewWire): AdminOverview {
  return {
    rangeLabel: w.rangeLabel,
    kpis: {
      activeUsers: w.kpis.activeUsers,
      streams: w.kpis.streams,
      gmv: w.kpis.gmv,
      newArtists: w.kpis.newArtists,
      // Deltas are signed percentages — a negative is real, never clamp it.
      deltas: { users: w.kpis.deltas.users, streams: w.kpis.deltas.streams, gmv: w.kpis.deltas.gmv },
    },
    gmvByDay: w.gmvByDay,
    // needsAttention and paymentMethods are Category B: the service returns List.of()
    // unconditionally, so these are expected to be empty until a future WU backs them.
    needsAttention: w.needsAttention.map((a): AttentionItem => ({ id: a.id, label: a.label, sub: a.sub, to: a.to })),
    topArtists: w.topArtists.map((a): RevenueArtist => ({ name: a.name, revenue: a.revenue })),
    paymentMethods: w.paymentMethods.map((m): PayMethod => ({ name: m.name, value: m.value })),
  }
}

export interface HealthWire {
  status: string
  metrics: { label: string; value: string; sub: string }[]
  listeners: number[]
  incidents: { id: string; title: string; date: string; status: string }[]
}

/**
 * The backend currently returns a hardcoded honest-empty payload (`status:"normal"` and three
 * empty arrays) — there is no APM, incident tracker, or listener telemetry behind it yet. An
 * unrecognised status maps to `degraded` rather than `normal`, so a future real status can never
 * be silently reported as healthy.
 */
export function toHealth(w: HealthWire): Health {
  return {
    status: w.status === 'normal' ? 'normal' : 'degraded',
    metrics: w.metrics.map((m): HealthMetric => ({ label: m.label, value: m.value, sub: m.sub })),
    listeners: w.listeners,
    incidents: w.incidents.map(
      (i): Incident => ({ id: i.id, title: i.title, date: i.date, status: i.status === 'resolved' ? 'resolved' : 'open' }),
    ),
  }
}

export interface AuditEntryWire {
  id: string
  actor: string
  action: string
  target: string
  type: string
  time: string
}
export interface AuditPageWire { items: AuditEntryWire[]; page: number; size: number; total: number }
export interface AuditPage { items: AuditEntry[]; page: number; size: number; total: number }

const AUDIT_TYPES: AuditType[] = ['user', 'catalog', 'finance', 'moderation', 'settings', 'editorial']

/**
 * The wire value is the backend enum's `name().toLowerCase()`, which happens to equal these
 * literals — but no dedicated mapper guarantees that, and the audit row looks its icon up by type
 * (`TYPE_META[type]`), so an unrecognised value would crash the row. Fall back to `settings`.
 */
export function toAuditType(wire: string): AuditType {
  return (AUDIT_TYPES as string[]).includes(wire) ? (wire as AuditType) : 'settings'
}

export function toAuditEntry(w: AuditEntryWire, now?: number): AuditEntry {
  return {
    id: w.id,
    actor: w.actor,
    action: w.action,
    // Compound `targetType:targetId` from the backend, e.g. "AdminMember:acc-123".
    target: w.target,
    type: toAuditType(w.type),
    time: w.time ? relativeTimeAgo(w.time, now) : '',
  }
}

export function toAuditPage(w: AuditPageWire, now?: number): AuditPage {
  return { items: w.items.map((e) => toAuditEntry(e, now)), page: w.page, size: w.size, total: w.total }
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
git commit -m "feat(admin): overview, health, and audit wire mappers"
```

---

### Task 2: Overview / health / audit query layer

**Files:**
- Create: `Frontend/src/lib/api/queries/admin-overview.ts`
- Test: `Frontend/src/lib/api/queries/admin-overview.test.ts`

**Interfaces:**
- Consumes: `toAdminOverview`, `toHealth`, `toAuditPage` + wire types from `../mappers`; `apiFetch` from `../client`.
- Produces (used by Tasks 3–5): `overviewQuery(range)`, `healthQuery()`, `auditQuery(type, q, page)`, and `AUDIT_PAGE_SIZE`.

- [ ] **Step 1: Write the failing test**

Create `Frontend/src/lib/api/queries/admin-overview.test.ts`:

```ts
import { afterEach, describe, expect, it, vi } from 'vitest'
import { overviewQuery, healthQuery, auditQuery } from './admin-overview'

const overviewWire = {
  rangeLabel: 'last 7 days',
  kpis: { activeUsers: 1, streams: 2, gmv: 3, newArtists: 4, deltas: { users: 0, streams: 1, gmv: -2 } },
  gmvByDay: [1], needsAttention: [], topArtists: [], paymentMethods: [],
}
const healthWire = { status: 'normal', metrics: [], listeners: [], incidents: [] }
const auditWire = { items: [], page: 1, size: 8, total: 0 }

function mockFetch(status: number, json: unknown) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300, status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => json, text: async () => JSON.stringify(json),
  } as Response)
}
afterEach(() => vi.restoreAllMocks())

describe('admin overview queries', () => {
  it('overviewQuery sends the range and keys by it', async () => {
    const f = mockFetch(200, overviewWire); vi.stubGlobal('fetch', f)
    const r = await overviewQuery('30d').queryFn!({} as never)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/overview?range=30d')
    expect(r.rangeLabel).toBe('last 7 days')
    expect(overviewQuery('30d').queryKey).toEqual(['admin', 'overview', '30d'])
  })

  it('healthQuery hits /v1/admin/health', async () => {
    const f = mockFetch(200, healthWire); vi.stubGlobal('fetch', f)
    const r = await healthQuery().queryFn!({} as never)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/health')
    expect(r.status).toBe('normal')
    expect(healthQuery().queryKey).toEqual(['admin', 'health'])
  })

  it('auditQuery sends page + size and OMITS type/q when unset', async () => {
    const f = mockFetch(200, auditWire); vi.stubGlobal('fetch', f)
    await auditQuery('all', '', 2).queryFn!({} as never)
    const url = f.mock.calls[0][0] as string
    expect(url).toContain('page=2')
    expect(url).toContain('size=8')
    expect(url).not.toContain('type=')
    expect(url).not.toContain('q=')
    expect(auditQuery('all', '', 2).queryKey).toEqual(['admin', 'audit', 'all', '', 2])
  })

  it('auditQuery sends type and q when set', async () => {
    const f = mockFetch(200, auditWire); vi.stubGlobal('fetch', f)
    await auditQuery('finance', 'refund', 1).queryFn!({} as never)
    const url = f.mock.calls[0][0] as string
    expect(url).toContain('type=finance')
    expect(url).toContain('q=refund')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/queries/admin-overview.test.ts`
Expected: FAIL — `./admin-overview` does not exist.

- [ ] **Step 3: Write the query module**

Create `Frontend/src/lib/api/queries/admin-overview.ts`:

```ts
import { keepPreviousData, queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import {
  toAdminOverview, toHealth, toAuditPage,
  type AdminOverviewWire, type HealthWire, type AuditPageWire,
} from '../mappers'

/** Rows per audit page — matches the shared paginator's default so the control looks unchanged. */
export const AUDIT_PAGE_SIZE = 8

/**
 * `GET /v1/admin/overview?range=` — dashboard KPIs, GMV series, and top artists.
 * An unrecognised range is a 422 server-side, so only the three UI range keys are ever sent.
 */
export function overviewQuery(range: string) {
  return queryOptions({
    queryKey: ['admin', 'overview', range],
    queryFn: async () => toAdminOverview(await apiFetch<AdminOverviewWire>(`/admin/overview?range=${range}`)),
    placeholderData: keepPreviousData,
  })
}

/**
 * `GET /v1/admin/health` — currently a hardcoded honest-empty payload (no APM/incident/telemetry
 * subsystem exists yet), so this reliably returns `normal` with three empty arrays.
 */
export function healthQuery() {
  return queryOptions({
    queryKey: ['admin', 'health'],
    queryFn: async () => toHealth(await apiFetch<HealthWire>('/admin/health')),
  })
}

/**
 * `GET /v1/admin/audit` — one server-paged slice of the append-only audit log.
 * `super-admin` only. `type`/`q` are sent only when set; `q` matches action/target server-side
 * (NOT actor — the single search box drives `q`).
 */
export function auditQuery(type: string, q: string, page: number) {
  const params = new URLSearchParams({ page: String(page), size: String(AUDIT_PAGE_SIZE) })
  if (type && type !== 'all') params.set('type', type)
  if (q) params.set('q', q)
  return queryOptions({
    queryKey: ['admin', 'audit', type, q, page],
    queryFn: async () => toAuditPage(await apiFetch<AuditPageWire>(`/admin/audit?${params}`)),
    placeholderData: keepPreviousData,
  })
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/queries/admin-overview.test.ts`
Expected: PASS (4 tests).

- [ ] **Step 5: Typecheck**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build`
Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/lib/api/queries/admin-overview.ts Frontend/src/lib/api/queries/admin-overview.test.ts
git commit -m "feat(admin): overview, health, and audit query layer"
```

---

### Task 3: Wire the dashboard overview

**Files:**
- Modify: `Frontend/src/routes/admin.index.tsx`

**Interfaces:**
- Consumes: `overviewQuery` from `../lib/api/queries/admin-overview`; `AdminLoadError` from `../components/admin/load-error`; `useQuery`.

- [ ] **Step 1: Replace the imports and the mock seed**

Replace the mock import line

```ts
import { getAdminOverview, ADMIN_RANGES, type AdminRange, type AttentionItem, type RevenueArtist, type PayMethod } from '../lib/admin-data'
```

with (`ADMIN_RANGES` stays — it drives the range toggle; the three item types stay — they type the sub-components):

```ts
import { useQuery } from '@tanstack/react-query'
import { ArrowUp, ArrowDown, ChevronRight } from 'lucide-react'
import { ADMIN_RANGES, type AdminRange, type AttentionItem, type RevenueArtist, type PayMethod } from '../lib/admin-data'
import { overviewQuery } from '../lib/api/queries/admin-overview'
import { AdminLoadError } from '../components/admin/load-error'
```

(the existing `import { ArrowUp, ChevronRight } from 'lucide-react'` line is replaced by the one above — `ArrowDown` is new, for the signed delta; `useMemo` is no longer needed, so the react import becomes `import { useState } from 'react'`.)

- [ ] **Step 2: Swap the state for the query**

Replace

```ts
  const [range, setRange] = useState<AdminRange>('7d')
  const data = useMemo(() => getAdminOverview(range), [range])
  const k = data.kpis
```

with:

```ts
  const [range, setRange] = useState<AdminRange>('7d')
  const { data, isLoading, isError, refetch } = useQuery(overviewQuery(range))
  const k = data?.kpis ?? {
    activeUsers: 0, streams: 0, gmv: 0, newArtists: 0, deltas: { users: 0, streams: 0, gmv: 0 },
  }
```

- [ ] **Step 3: Gate the whole body on error/loading**

The KPI grid and both chart rows all come from the one query, so guard them together. Immediately after the header block's closing `</div>` (the one that closes the range-toggle row), wrap the remaining sections. Replace everything from the `{/* KPIs */}` comment through the closing `</div>` of the "Top artists + payment methods" grid with:

```tsx
      {isError ? (
        <AdminLoadError label="Couldn't load the overview." onRetry={() => refetch()} />
      ) : isLoading ? (
        <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
      ) : (
        <>
          {/* KPIs */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <Kpi label="Active users" value={k.activeUsers.toLocaleString()} delta={k.deltas.users} />
            <Kpi label="Streams (24h)" value={formatCompact(k.streams)} delta={k.deltas.streams} />
            <Kpi label="GMV" value={cedisK(k.gmv)} delta={k.deltas.gmv} accent />
            <Kpi label="New artists" value={k.newArtists.toLocaleString()} sub="this week" />
          </div>

          {/* GMV chart + needs attention */}
          <div className="grid grid-cols-1 lg:grid-cols-[1.6fr_1fr] gap-6 items-start">
            <section className={cn(CARD, 'flex flex-col gap-5 min-w-0')}>
              <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">GMV by day (₵)</h2>
              <GmvBars bars={data?.gmvByDay ?? []} />
            </section>

            <section className={cn(CARD, 'flex flex-col gap-2')}>
              <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white mb-2">Needs attention</h2>
              {(data?.needsAttention ?? []).length === 0 ? (
                <p className="py-6 text-sm text-gray-400 dark:text-gray-500">Nothing needs attention.</p>
              ) : (
                (data?.needsAttention ?? []).map((a) => <AttentionRow key={a.id} item={a} />)
              )}
            </section>
          </div>

          {/* Top artists + payment methods */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <section className={cn(CARD, 'flex flex-col gap-4')}>
              <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Top artists by revenue</h2>
              <div className="flex flex-col">
                {(data?.topArtists ?? []).length === 0 ? (
                  <p className="py-6 text-sm text-gray-400 dark:text-gray-500">No artist revenue yet.</p>
                ) : (
                  (data?.topArtists ?? []).map((a, i) => <ArtistRow key={a.name} rank={i + 1} artist={a} />)
                )}
              </div>
            </section>

            <section className={cn(CARD, 'flex flex-col gap-4')}>
              <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Payment methods (today)</h2>
              <PaymentBars methods={data?.paymentMethods ?? []} />
            </section>
          </div>
        </>
      )}
```

Also change the header's range label line to tolerate a missing `data`:

```tsx
          <span className="text-sm text-gray-500 dark:text-gray-300">Real-time · {data?.rangeLabel ?? ''}</span>
```

- [ ] **Step 4: Give the KPI delta a sign (deviation 4)**

Replace the `Kpi` component's delta branch. Today it hardcodes an up-arrow and green, so a real negative renders "▲ -18%" in green:

```tsx
      {delta != null ? (
        <span className={cn('flex items-center gap-1 text-xs font-bold', delta < 0 ? 'text-beatz-red' : 'text-beatz-green')}>
          {delta < 0 ? <ArrowDown size={12} /> : <ArrowUp size={12} />} {delta}%
        </span>
      ) : (
        <span className="text-xs text-gray-400 dark:text-gray-500">{sub}</span>
      )}
```

- [ ] **Step 5: Normalize the GMV bars and make the tooltip truthful (deviation 3)**

Live `gmvByDay` values are raw cedis (1 / 7 / 30 of them), not the mock's 0–1 fractions, so the bar height must be computed against the window max. The tooltip currently invents `₵{Math.round(b * 12)}k` from a fraction; show the real amount instead. Replace `GmvBars` with:

```tsx
function GmvBars({ bars }: { bars: number[] }) {
  const [hover, setHover] = useState<number | null>(null)
  // Values are raw cedis; scale against the window max so heights stay comparable.
  // Guard the empty/all-zero case — Math.max(...[]) is -Infinity and would yield NaN heights.
  const max = bars.length > 0 ? Math.max(...bars) : 0
  if (bars.length === 0) {
    return <div className="h-56 flex items-center justify-center text-sm text-gray-400 dark:text-gray-500">No sales in this range.</div>
  }
  return (
    <div className="flex items-end gap-1.5 h-56">
      {bars.map((b, i) => (
        <div key={i} className="flex-1 h-full flex items-end relative" onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}>
          {hover === i && (
            <span className="absolute -top-1 left-1/2 -translate-x-1/2 text-[10px] font-bold text-beatz-dark-bg dark:text-white whitespace-nowrap">
              {cedisK(Math.round(b))}
            </span>
          )}
          <div className={cn('w-full rounded-t-md transition-colors', hover === i ? 'bg-beatz-green' : 'bg-beatz-green/75')} style={{ height: `${max > 0 ? (b / max) * 100 : 0}%` }} />
        </div>
      ))}
    </div>
  )
}
```

- [ ] **Step 6: Guard `PaymentBars` against the always-empty array (deviation 1)**

`paymentMethods` is always `[]` server-side, and `Math.max(...[])` is `-Infinity`, which yields `NaN` widths. Add an empty guard at the top of `PaymentBars`, leaving the existing bar markup untouched:

```tsx
function PaymentBars({ methods }: { methods: PayMethod[] }) {
  if (methods.length === 0) {
    return <p className="py-6 text-sm text-gray-400 dark:text-gray-500">No payment-method data yet.</p>
  }
  const max = Math.max(...methods.map((m) => m.value))
```

(the rest of the component is unchanged.)

- [ ] **Step 7: Typecheck + full suite**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; vitest green.

- [ ] **Step 8: Commit**

```bash
git add Frontend/src/routes/admin.index.tsx
git commit -m "feat(admin): wire dashboard overview (signed deltas, real GMV bars, honest empties)"
```

---

### Task 4: Wire the audit log with server-side paging

**Files:**
- Modify: `Frontend/src/routes/admin.audit.tsx`

**Interfaces:**
- Consumes: `auditQuery`, `AUDIT_PAGE_SIZE` from `../lib/api/queries/admin-overview`; `useServerPaged`, `Pagination` from `../components/admin/pagination`; `AdminLoadError`; `useQuery`.

- [ ] **Step 1: Replace the imports**

Replace

```ts
import { getAuditLog, type AuditEntry, type AuditType } from '../lib/admin-data'
import { usePaged, Pagination } from '../components/admin/pagination'
```

with:

```ts
import { useQuery } from '@tanstack/react-query'
import type { AuditEntry, AuditType } from '../lib/admin-data'
import { auditQuery, AUDIT_PAGE_SIZE } from '../lib/api/queries/admin-overview'
import { useServerPaged, Pagination } from '../components/admin/pagination'
import { AdminLoadError } from '../components/admin/load-error'
```

and change the react import to `import { useEffect, useState } from 'react'` (`useMemo` is no longer used; `useEffect` drives the search debounce).

- [ ] **Step 2: Replace the component's data block**

Replace

```ts
  const entries = useMemo(() => getAuditLog(), [])
  const [type, setType] = useState<AuditType | 'all'>('all')
  const [query, setQuery] = useState('')

  const q = query.trim().toLowerCase()
  const rows = entries.filter((e) => (type === 'all' || e.type === type) && (!q || `${e.actor} ${e.action} ${e.target}`.toLowerCase().includes(q)))
  const paged = usePaged(rows)
```

with:

```ts
  const [type, setType] = useState<AuditType | 'all'>('all')
  const [query, setQuery] = useState('')
  const [debouncedQ, setDebouncedQ] = useState('')
  const [page, setPage] = useState(1)

  // Search is a server param now, so debounce it: one request per pause, not per keystroke.
  useEffect(() => {
    const t = setTimeout(() => {
      setDebouncedQ(query.trim())
      setPage(1)
    }, 300)
    return () => clearTimeout(t)
  }, [query])

  const { data, isLoading, isError, refetch } = useQuery(auditQuery(type, debouncedQ, page))
  const rows: AuditEntry[] = data?.items ?? []
  const paged = useServerPaged({ items: rows, total: data?.total ?? 0, page, setPage, size: AUDIT_PAGE_SIZE })
```

**`useServerPaged` does NOT clamp `page` — the caller owns it.** Both filter paths above must reset it, or a user on page 5 who narrows the filter sees a garbage "N–M of T" label, no highlighted page button, an always-enabled Next, and an empty table while page 1 has rows.

- [ ] **Step 3: Reset to page 1 on the type-chip change**

```tsx
            <button key={t} onClick={() => { setType(t); setPage(1) }}
```

(the rest of that button — classes, label expression — is unchanged.)

- [ ] **Step 4: Add the error and loading branches**

Replace the card body's `rows.length === 0 ? (...) : (...)` expression with:

```tsx
        {isError ? (
          <AdminLoadError label="Couldn't load the audit log." onRetry={() => refetch()} />
        ) : isLoading ? (
          <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
        ) : rows.length === 0 ? (
          <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">No matching entries.</div>
        ) : (
          paged.pageItems.map((e) => <AuditRow key={e.id} entry={e} />)
        )}
```

The `Pagination` line, the Export button (Category B), the type chips, the search input, and `AuditRow` are all unchanged.

- [ ] **Step 5: Typecheck + full suite**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; vitest green.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/routes/admin.audit.tsx
git commit -m "feat(admin): wire audit log with server-side paging and filters"
```

---

### Task 5: Wire the health page (honest-empty)

**Files:**
- Modify: `Frontend/src/routes/admin.health.tsx`

**Interfaces:**
- Consumes: `healthQuery` from `../lib/api/queries/admin-overview`; `AdminLoadError`; `useQuery`.

**Context:** the backend returns a hardcoded `status:"normal"` with three empty arrays — there is no observability subsystem behind it. Wiring this replaces the mock's invented latency/uptime figures with honest empty states.

- [ ] **Step 1: Replace the imports and the mock seed**

Replace

```ts
import { getHealth, type HealthMetric, type Incident } from '../lib/admin-data'
```

with:

```ts
import { useQuery } from '@tanstack/react-query'
import type { HealthMetric, Incident } from '../lib/admin-data'
import { healthQuery } from '../lib/api/queries/admin-overview'
import { AdminLoadError } from '../components/admin/load-error'
```

and **delete the `import { useMemo } from 'react'` line entirely** — this component uses no React hooks of its own once the query replaces the memo.

Replace

```ts
  const data = useMemo(() => getHealth(), [])
  const normal = data.status === 'normal'
```

with:

```ts
  const { data, isLoading, isError, refetch } = useQuery(healthQuery())
  const normal = data?.status !== 'degraded'
  const metrics = data?.metrics ?? []
  const incidents = data?.incidents ?? []
  const listeners = data?.listeners ?? []
```

- [ ] **Step 2: Add the error/loading gate and the empty states**

Immediately after the header block, wrap the metrics grid and the chart/incidents row:

```tsx
      {isError ? (
        <AdminLoadError label="Couldn't load system health." onRetry={() => refetch()} />
      ) : isLoading ? (
        <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
      ) : (
        <>
          {/* Metrics */}
          {metrics.length === 0 ? (
            <p className="text-sm text-gray-400 dark:text-gray-500">No service metrics yet.</p>
          ) : (
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              {metrics.map((m) => <Metric key={m.label} metric={m} />)}
            </div>
          )}

          {/* Chart + incidents */}
          <div className="grid grid-cols-1 lg:grid-cols-[1.6fr_1fr] gap-6 items-start">
            <section className={cn(CARD, 'flex flex-col gap-5 min-w-0')}>
              <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white">Concurrent listeners (24h)</h2>
              <ListenersChart series={listeners} />
              <div className="flex items-center justify-between text-[11px] font-mono uppercase tracking-wider text-gray-400 dark:text-gray-500">
                <span>00:00</span><span>12:00</span><span>Now</span>
              </div>
            </section>

            <section className={cn(CARD, 'flex flex-col gap-2')}>
              <h2 className="text-lg font-bold text-beatz-dark-bg dark:text-white mb-2">Recent incidents</h2>
              {incidents.length === 0 ? (
                <p className="py-6 text-sm text-gray-400 dark:text-gray-500">No incidents recorded.</p>
              ) : (
                incidents.map((i) => <IncidentRow key={i.id} incident={i} />)
              )}
            </section>
          </div>
        </>
      )}
```

- [ ] **Step 3: Guard `ListenersChart` against the empty series**

With an empty array, `Math.max(...[])` is `-Infinity`, `Math.min(...[])` is `Infinity`, and `series.length - 1` is `-1`, so every coordinate becomes `NaN` and the SVG path is invalid. Add a guard at the top of `ListenersChart`, leaving the existing SVG untouched:

```tsx
function ListenersChart({ series }: { series: number[] }) {
  if (series.length < 2) {
    return <div className="w-full h-56 flex items-center justify-center text-sm text-gray-400 dark:text-gray-500">No listener telemetry yet.</div>
  }
  const w = 600, h = 220, padTop = 12, padBot = 12
```

(`< 2` rather than `=== 0`: a single point also divides by `series.length - 1 === 0`.)

- [ ] **Step 4: Typecheck + full suite**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; vitest green.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/routes/admin.health.tsx
git commit -m "feat(admin): wire system health to the live (honest-empty) endpoint"
```

---

### Task 6: Live QA + PR (USER-run gate)

**Files:** none (verification only). The controller does NOT run `verify.sh`; CI is authoritative.

- [ ] **Step 1: Final full gate**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; full suite green.

- [ ] **Step 2: Live QA** (backend on :18080, Vite proxy → :18080)

**Audit requires a `super-admin` account**; overview and health accept any of the five admin roles. Revert the Vite proxy only at the very END of the session — reverting mid-session makes Vite hot-reload it back to `:8080` (a different app) and every call starts failing with `TENANT_MISSING`.

  - Overview: KPIs show live figures; switching the range refetches and the label updates; the GMV chart shows `range.days()` bars (so **24h is a single bar**) with a tooltip showing a real cedis amount; a negative delta renders a red down-arrow; "Needs attention" and "Payment methods" show their empty states (never `NaN` bars).
  - Audit: entries render with relative times and `Type:id` targets; the type chips and the debounced search both refetch server-side (confirm in the network tab that typing N characters issues ONE request); the paginator reflects the server `total` and paging fetches new rows; page resets to 1 on both filter changes.
  - Health: the status pill reads "All systems normal" and all three sections show empty states — no `NaN` SVG path.
  - Force a load error (stop the backend, refetch) → the distinct "Couldn't load …" + Retry affordance on each screen.
  - Verify the 422 path: an unknown range can't be produced from the UI, but confirm the error affordance appears rather than a blank dashboard if the overview query fails.

- [ ] **Step 3: Open the PR**

```bash
git push -u origin feat/frontend-admin-overview-audit
gh pr create --base feat/frontend-admin-finance --title "feat(admin): wire Overview, Audit, and Health to live endpoints" --body "<DoD checklist; the six deliberate deviations; the Category-B honest-empty list; the stacking note>"
```

Retarget the PR to `master` once #167 merges.

---

## Notes for the executor

- **Branch:** `feat/frontend-admin-overview-audit`, stacked on `feat/frontend-admin-finance` (spec `d46feac`). BASE for the first review package is the plan commit.
- **Do NOT** touch backend or stage backend secrets. Frontend-only branch.
- **Already available — import, do not recreate:** `AdminLoadError`, `useServerPaged` (from the stacked branch), and `format.ts`'s `relativeTimeAgo`.
- **This slice has no mutations** — three reads. Nothing here moves money or changes state.
- The six deviations in the Global Constraints are the *complete* list; anything else that changes visually is a defect.
