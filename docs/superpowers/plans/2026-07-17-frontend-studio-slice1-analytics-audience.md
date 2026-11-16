# Frontend Studio — Slice 1 (Analytics + Audience) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the Studio **Analytics** and **Audience** pages from `lib/studio-analytics.ts` (mock) to `GET /v1/studio/analytics` and `GET /v1/studio/audience`, with no visual change.

**Architecture:** Same idiom as the merged store/events/podcasts + quick-wins wiring: TanStack `queryOptions` + `apiFetch<Wire>` + `mappers.toX`, consumed via route `loader` (`ensureQueryData`) + `useSuspenseQuery`. Both endpoints are **range-aware** (`?range=`), and the pages already own a range selector, so the range becomes part of the query key.

**Tech Stack:** React 19 + TanStack Query v5 + TanStack Router, TypeScript, Vitest, Vite proxy `/v1` → `127.0.0.1:8080`.

**Why this slice first:** `studio.analytics.tsx` and `studio.audience.tsx` are the ONLY Studio routes that do **not** import `studio-context` — they read `lib/studio-analytics` directly. So this slice needs zero god-context refactoring and is fully isolated (see "Studio roadmap" below).

## Global Constraints

- **No visual change.** Data-source swap only; JSX/classes preserved.
- Mapper output must match the exact types in `Frontend/src/lib/studio-analytics.ts` (`Analytics`, `Audience`, `MetricSeries`, `CountryStat`, `TopTrackStat`, `AgeBucket`, `SourceStat`, `Superfan`). **Do not** move these types unless a circular import forces it (see Task 1 Prep).
- `apiFetch` (`lib/api/client.ts`) prepends `BASE_URL='/v1'` — write paths WITHOUT `/v1` (e.g. `/studio/analytics`). Established idiom.
- **This repo runs the React Compiler** — do NOT add manual `useMemo`/`useCallback` in components; the compiler errors on memoization it can't preserve ("Compilation Skipped"). Let it memoize.
- No `@testing-library/jest-dom` → RTL assertions use `toBeTruthy()` / `queryByText`.
- All commands from `Frontend/`. Test: `npx vitest run <path>`. Typecheck: `npx tsc --noEmit`. Lint: `npm run lint` — repo baseline is **214** problems; add no NEW ones (`react-refresh/only-export-components` on route files is pre-existing).
- Branch: `feat/frontend-studio-analytics` off master.

## File Structure

- `Frontend/src/lib/api/queries/studio.ts` (new) — `studioAnalyticsQuery(range)`, `studioAudienceQuery(range)`.
- `Frontend/src/lib/api/mappers.ts` (modify) — add `AnalyticsWire`/`AudienceWire` + `toAnalytics`/`toAudience`.
- `Frontend/src/routes/studio.analytics.tsx` (modify) — fetch via query; drop the mock data import (keep `ANALYTICS_RANGES`/type imports, which are UI config, not data).
- `Frontend/src/routes/studio.audience.tsx` (modify) — same for audience.
- Tests: `Frontend/src/lib/api/queries/studio.test.ts` (new), cases in `Frontend/src/lib/api/mappers.test.ts`.

**Do NOT delete `lib/studio-analytics.ts`** — it also exports UI config (`ANALYTICS_RANGES`, `AUDIENCE_RANGES`, `RANGES`) and the domain types the mappers target; only its *mock data* exports become unused. Other Studio routes (`studio.index`, `studio.releases`, `studio.podcasts.index`) still import it until later slices.

---

## Task 1: `toAnalytics` mapper

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Produces: `export interface AnalyticsWire` and `export function toAnalytics(wire: AnalyticsWire): Analytics`.

**Prep:** Read the backend `AnalyticsView` (`backend/.../studio/application/port/in/AnalyticsView.java`) and its nested DTOs (`MetricsView`, `MetricSeriesView`, `NamedValue`, `TopTrackStat`, `AgeBucket`, `RevenueBreakdown`, `EngagementStats`, `SourceStat`), and the frontend `Analytics` type (`lib/studio-analytics.ts:49`). They were built to match field-for-field:
`{ rangeLabel, axisLabel, labels[], metrics, fans, countries[], topTracks[], ages[], revenue, engagement, sources[] }`.
Confirm two things: (1) `MetricsView`'s field names vs the frontend's `Record<MetricKey, MetricSeries>` (keys `streams|sales|followers|tips`) — if `MetricsView` is a record of four named fields, the mapper maps them into that Record; (2) `NamedValue`'s fields vs `CountryStat { name, value }`. **Importing `Analytics` from `lib/studio-analytics` into `mappers.ts` is fine ONLY if it creates no cycle** — `studio-analytics.ts` must not import mappers. Verify; if it would cycle, move the types to `types/index.ts` and re-export (precedent: `AppNotification`, commit `a58689c`).

- [ ] **Step 1: Write the failing test** (fill the fixture from the real `AnalyticsView` per Prep)

```ts
// add to Frontend/src/lib/api/mappers.test.ts
import { toAnalytics, type AnalyticsWire } from './mappers'

describe('toAnalytics', () => {
  it('maps the analytics view including the metric series', () => {
    const wire: AnalyticsWire = {
      rangeLabel: 'Last 28 days',
      axisLabel: 'Day',
      labels: ['Jul 1', 'Jul 2'],
      metrics: {
        streams: { total: 1200, delta: 12, current: [600, 600], previous: [500, 500] },
        sales: { total: 30, delta: -2, current: [15, 15], previous: [16, 16] },
        followers: { total: 90, delta: 5, current: [45, 45], previous: [40, 40] },
        tips: { total: 8, delta: 0, current: [4, 4], previous: [4, 4] },
      },
      fans: 2400,
      countries: [{ name: 'Ghana', value: 70 }],
      topTracks: [{ title: 'Soja', streams: 500, revenue: 12.5 }],
      ages: [{ label: '18-24', value: 40 }],
      revenue: { sales: 100, streaming: 20, tips: 8 },
      engagement: { completion: 80, save: 12, skip: 8 },
      sources: [{ name: 'Search', pct: 35 }],
    }

    const a = toAnalytics(wire)
    expect(a.rangeLabel).toBe('Last 28 days')
    expect(a.metrics.streams.total).toBe(1200)
    expect(a.metrics.streams.current).toEqual([600, 600])
    expect(a.countries[0]).toEqual({ name: 'Ghana', value: 70 })
    expect(a.topTracks[0].revenue).toBe(12.5)
    expect(a.revenue.sales).toBe(100)
    expect(a.engagement.completion).toBe(80)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Frontend && npx vitest run src/lib/api/mappers.test.ts -t toAnalytics`
Expected: FAIL — `toAnalytics` is not exported.

- [ ] **Step 3: Write the implementation** (enumerate every `Analytics` field per Prep — no `// + ...`, no `as any`)

```ts
// add to Frontend/src/lib/api/mappers.ts
import type {
  Analytics, MetricSeries, CountryStat, TopTrackStat, AgeBucket, SourceStat,
} from '../studio-analytics'

interface MetricSeriesWire { total: number; delta: number; current: number[]; previous: number[] }

export interface AnalyticsWire {
  rangeLabel: string
  axisLabel: string
  labels: string[]
  metrics: { streams: MetricSeriesWire; sales: MetricSeriesWire; followers: MetricSeriesWire; tips: MetricSeriesWire }
  fans: number
  countries: { name: string; value: number }[]
  topTracks: { title: string; streams: number; revenue: number }[]
  ages: { label: string; value: number }[]
  revenue: { sales: number; streaming: number; tips: number }
  engagement: { completion: number; save: number; skip: number }
  sources: { name: string; pct: number }[]
}

const toMetricSeries = (w: MetricSeriesWire): MetricSeries => ({
  total: w.total, delta: w.delta, current: w.current, previous: w.previous,
})

export function toAnalytics(wire: AnalyticsWire): Analytics {
  return {
    rangeLabel: wire.rangeLabel,
    axisLabel: wire.axisLabel,
    labels: wire.labels,
    metrics: {
      streams: toMetricSeries(wire.metrics.streams),
      sales: toMetricSeries(wire.metrics.sales),
      followers: toMetricSeries(wire.metrics.followers),
      tips: toMetricSeries(wire.metrics.tips),
    },
    fans: wire.fans,
    countries: wire.countries.map((c): CountryStat => ({ name: c.name, value: c.value })),
    topTracks: wire.topTracks.map((t): TopTrackStat => ({ title: t.title, streams: t.streams, revenue: t.revenue })),
    ages: wire.ages.map((a): AgeBucket => ({ label: a.label, value: a.value })),
    revenue: wire.revenue,
    engagement: wire.engagement,
    sources: wire.sources.map((s): SourceStat => ({ name: s.name, pct: s.pct })),
    // + any remaining `Analytics` field the Prep diff listed
  }
}
```

- [ ] **Step 4: Run test + typecheck (the hard gate)**

Run: `cd Frontend && npx vitest run src/lib/api/mappers.test.ts -t toAnalytics && npx tsc --noEmit`
Expected: PASS + clean. `tsc` proves `toAnalytics` returns a complete `Analytics` — a missing field fails here.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/lib/api/mappers.ts Frontend/src/lib/api/mappers.test.ts
git commit -m "feat(frontend): AnalyticsWire + toAnalytics mapper"
```

---

## Task 2: `toAudience` mapper

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Produces: `export interface AudienceWire` and `export function toAudience(wire: AudienceWire): Audience`.

**Prep:** Read the backend `AudienceView` (`backend/.../studio/application/port/in/AudienceView.java`) — known fields include `rangeLabel, monthlyListeners, listenersDelta, followers, followersGained, followersPeriod, superfans, avgSessionSec, avgSessionDelta, cities[NamedValue], …` (read the full record; the list continues). Read the frontend `Audience` type (`lib/studio-analytics.ts:175`) and `Superfan` (`:173`). Map every `Audience` field; where the wire name differs from the domain name, map explicitly and note it in the report.

- [ ] **Step 1: Write the failing test** (fixture built from the REAL `AudienceView` fields — do not guess)

```ts
// add to Frontend/src/lib/api/mappers.test.ts
import { toAudience, type AudienceWire } from './mappers'

describe('toAudience', () => {
  it('maps the audience view', () => {
    // Build `wire` from the real AudienceView fields confirmed in Prep.
    const wire = { /* …every AudienceView field… */ } as AudienceWire
    const a = toAudience(wire)
    // Assert a representative field from each group: headline stat, a delta,
    // and one nested list entry (e.g. cities[0] / superfans[0]).
    expect(a.rangeLabel).toBe(wire.rangeLabel)
  })
})
```
Replace the placeholder fixture with the real fields during implementation — the assertions must check actual mapped values (headline stats, a delta, and one entry from each nested list).

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Frontend && npx vitest run src/lib/api/mappers.test.ts -t toAudience`
Expected: FAIL — `toAudience` is not exported.

- [ ] **Step 3: Implement `AudienceWire` + `toAudience`** covering every `Audience` field, mirroring `toAnalytics`'s structure (explicit field-by-field, nested lists mapped with `.map()`, `?? undefined` for nullables per the existing idiom).

- [ ] **Step 4: Run test + typecheck**

Run: `cd Frontend && npx vitest run src/lib/api/mappers.test.ts -t toAudience && npx tsc --noEmit`
Expected: PASS + clean.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/lib/api/mappers.ts Frontend/src/lib/api/mappers.test.ts
git commit -m "feat(frontend): AudienceWire + toAudience mapper"
```

---

## Task 3: Studio query module

**Files:**
- Create: `Frontend/src/lib/api/queries/studio.ts`
- Test: `Frontend/src/lib/api/queries/studio.test.ts`

**Interfaces:**
- Consumes: `apiFetch`; `toAnalytics`/`AnalyticsWire`, `toAudience`/`AudienceWire` (Tasks 1–2).
- Produces: `studioAnalyticsQuery(range: AnalyticsRange)`, `studioAudienceQuery(range: AudienceRange)`.

**Prep:** Confirm the exact `range` tokens `StudioAnalyticsResource.analytics(@QueryParam("range"))` accepts (read the resource + whatever parses it). The frontend ranges are `'7d' | '28d' | '90d' | '12m' | 'all'` (analytics) and `'7d' | '28d' | '90d' | '12m'` (audience). If the backend expects different tokens, map them in the query; if it accepts these verbatim, pass through.

- [ ] **Step 1: Write the failing test**

```ts
// Frontend/src/lib/api/queries/studio.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as client from '../client'
import { studioAnalyticsQuery, studioAudienceQuery } from './studio'

vi.mock('../client')
beforeEach(() => vi.resetAllMocks())

describe('studio queries', () => {
  it('fetches analytics for a range and maps it', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({
      rangeLabel: 'Last 28 days', axisLabel: 'Day', labels: ['Jul 1'],
      metrics: {
        streams: { total: 1200, delta: 12, current: [600], previous: [500] },
        sales: { total: 30, delta: -2, current: [15], previous: [16] },
        followers: { total: 90, delta: 5, current: [45], previous: [40] },
        tips: { total: 8, delta: 0, current: [4], previous: [4] },
      },
      fans: 2400, countries: [{ name: 'Ghana', value: 70 }],
      topTracks: [{ title: 'Soja', streams: 500, revenue: 12.5 }],
      ages: [{ label: '18-24', value: 40 }],
      revenue: { sales: 100, streaming: 20, tips: 8 },
      engagement: { completion: 80, save: 12, skip: 8 },
      sources: [{ name: 'Search', pct: 35 }],
    })

    const a = await studioAnalyticsQuery('28d').queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/studio/analytics?range=28d')
    expect(a.metrics.streams.total).toBe(1200)
  })

  it('uses a range-scoped query key so switching range refetches', () => {
    expect(studioAnalyticsQuery('7d').queryKey).not.toEqual(studioAnalyticsQuery('28d').queryKey)
  })

  it('fetches audience for a range', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({ rangeLabel: 'Last 7 days' })
    await studioAudienceQuery('7d').queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/studio/audience?range=7d')
  })
})
```
(For the audience test, extend the mocked payload to the real `AudienceWire` once Task 2 fixes its shape — the assertion that matters here is the URL + range-scoped key.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Frontend && npx vitest run src/lib/api/queries/studio.test.ts`
Expected: FAIL — `./studio` not found.

- [ ] **Step 3: Write the implementation**

```ts
// Frontend/src/lib/api/queries/studio.ts
import { queryOptions } from '@tanstack/react-query'
import type { AnalyticsRange, AudienceRange } from '../../studio-analytics'
import { apiFetch } from '../client'
import { toAnalytics, toAudience, type AnalyticsWire, type AudienceWire } from '../mappers'

/** `GET /v1/studio/analytics?range=` — the signed-in creator's analytics for a range. */
export function studioAnalyticsQuery(range: AnalyticsRange) {
  return queryOptions({
    queryKey: ['studio', 'analytics', range],
    queryFn: async () => toAnalytics(await apiFetch<AnalyticsWire>(`/studio/analytics?range=${range}`)),
  })
}

/** `GET /v1/studio/audience?range=` — the signed-in creator's audience for a range. */
export function studioAudienceQuery(range: AudienceRange) {
  return queryOptions({
    queryKey: ['studio', 'audience', range],
    queryFn: async () => toAudience(await apiFetch<AudienceWire>(`/studio/audience?range=${range}`)),
  })
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd Frontend && npx vitest run src/lib/api/queries/studio.test.ts && npx tsc --noEmit`
Expected: PASS + clean.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/lib/api/queries/studio.ts Frontend/src/lib/api/queries/studio.test.ts
git commit -m "feat(frontend): studio analytics + audience query module"
```

---

## Task 4: Wire the Analytics + Audience routes

**Files:**
- Modify: `Frontend/src/routes/studio.analytics.tsx`, `Frontend/src/routes/studio.audience.tsx`

**Interfaces:**
- Consumes: `studioAnalyticsQuery`, `studioAudienceQuery` (Task 3).

**Prep:** Read both routes. Each holds a range selector in local state (`AnalyticsRange`/`AudienceRange`) and reads mock data from `lib/studio-analytics`. Identify (a) the state variable holding the current range, (b) the mock accessor being replaced, (c) which imports from `lib/studio-analytics` are **UI config/types** (`ANALYTICS_RANGES`, `AUDIENCE_RANGES`, type imports) — those STAY.

- [ ] **Step 1: Wire `studio.analytics.tsx`** — the range lives in component state, so use `useSuspenseQuery` keyed on it (it refetches/suspends on change). Add a loader prefetch for the default range only.

```tsx
// core shape — keep the file's existing JSX/layout/range-selector markup
export const Route = createFileRoute('/studio/analytics')({
  loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(studioAnalyticsQuery('28d')),
  component: StudioAnalytics,
})

function StudioAnalytics() {
  const [range, setRange] = useState<AnalyticsRange>('28d')
  const { data: analytics } = useSuspenseQuery(studioAnalyticsQuery(range))
  // ...existing render, sourcing every stat from `analytics`
}
```
Use the route's existing default range if it isn't `'28d'`. Remove the mock-data import; KEEP `ANALYTICS_RANGES` + type imports.

- [ ] **Step 2: Wire `studio.audience.tsx`** the same way with `studioAudienceQuery(range)` and the audience route's own default range; keep `AUDIENCE_RANGES` + type imports.

- [ ] **Step 3: Typecheck + lint + grep gate**

Run: `cd Frontend && npx tsc --noEmit && npm run lint`
Expected: clean; lint still **214** (no new). Then confirm neither route imports mock *data* any more — they may still import `ANALYTICS_RANGES`/`AUDIENCE_RANGES`/types from `lib/studio-analytics`, which is expected:
`grep -nE "from '\.\./lib/studio-analytics'" src/routes/studio.analytics.tsx src/routes/studio.audience.tsx`
Every imported symbol on those lines must be a type or a `*_RANGES` config constant — no data accessors.

- [ ] **Step 4: Manual QA** (controller; backend up, logged in as a creator)
  - `/studio/analytics` → `GET /v1/studio/analytics?range=28d` in the network tab; chart + stats render.
  - Change the range → a new request with the new `range`, chart updates.
  - `/studio/audience` → `GET /v1/studio/audience?range=…`; stats render.
  - Zero console errors; layout identical to the mock render.

- [ ] **Step 5: Commit + open PR**

```bash
git add Frontend/src/routes/studio.analytics.tsx Frontend/src/routes/studio.audience.tsx
git commit -m "feat(frontend): wire studio analytics + audience to /v1/studio"
```
Open PR `feat/frontend-studio-analytics` → `master`.

---

## Cross-cutting acceptance (before PR)
- `npx tsc --noEmit` clean; `npm run lint` still 214; `npx vitest run` — new tests green (the pre-existing `token`/`client`/`cart-context` jsdom-`localStorage` failures are unrelated and red on master too).
- No mock **data** import remains in the two wired routes (config/type imports from `lib/studio-analytics` are fine and expected).
- Visual parity with the mock render; real `/v1/studio/analytics` + `/audience` calls visible in the network tab.

---

## Studio roadmap (context for later slices — not this plan's scope)

Studio is 18 routes + 2 contexts across 4 mock modules. Every screen has a backend:

| Slice | Screens | Endpoints | Notes |
|---|---|---|---|
| **1 (this plan)** | analytics, audience | `GET /v1/studio/analytics`, `/audience` | Context-free — the clean starting point |
| 2 | profile, settings | `GET/PUT /v1/studio/profile`, `/settings` | First slice to touch `studio-context` |
| 3 | releases list/detail + 5-step wizard | `GET/POST /v1/studio/releases`, `GET/PATCH/DELETE /{id}`, `POST /{id}/tracks` | Largest; also `release-draft-context` |
| 4 | podcasts index/new | `GET/POST /v1/studio/podcasts/shows`, `/episodes` (+PATCH/DELETE) | |
| 5 | payouts | `GET /v1/studio/payouts`, `POST /withdraw`, `/v1/studio/payout-methods` | **Money** — withdrawals; needs security review |

**The key decision for Slice 2+:** `features/studio/studio-context.tsx` is a monolithic god-context holding `profile`, `settings`, `releases[]`, `episodes[]`, `balance`, `transactions[]`, `methods[]` plus 11 mutators (including `withdraw`), hydrated from mocks. Two options:
- **(A) Keep the god-context, back each field with a query.** Minimal consumer churn (routes untouched), but every Studio route then loads all Studio data.
- **(B) Decompose into per-domain queries** used directly by each route (`studioProfileQuery`, `studioReleasesQuery`, …), deleting the context. Matches how store/events/podcasts were wired and avoids over-fetching, but touches every consumer.

**Recommendation: (B), incrementally** — peel one domain out per slice (profile/settings → releases → podcasts → payouts), shrinking the context each time until it's empty and can be deleted. Each slice stays shippable, and no single PR rewrites all 18 routes. Decide before starting Slice 2.
