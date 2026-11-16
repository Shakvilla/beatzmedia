# Frontend Store / Events / Podcasts Browse Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Swap the Store, Events, and Podcasts browse/detail pages from static mock data (`lib/store-data.ts`, `lib/event-data.ts`, `lib/podcast-data.ts`) to their already-shipped backend endpoints, so those pages render live data with no visual change.

**Architecture:** Mirror the existing catalog/commerce/search wiring exactly — one TanStack Query module per domain under `lib/api/queries/`, wire→domain mapper functions in `lib/api/mappers.ts`, and routes that fetch via `loader: ensureQueryData(query())` + `useSuspenseQuery(query())` and pass plain data down to now-presentational components. No new backend work: `GET /v1/store`, `GET /v1/events`, `GET /v1/podcasts` (+ detail/episodes) already exist.

**Tech Stack:** React 19, TanStack Router (file-based routes) + TanStack Query v5 (`queryOptions`/`useSuspenseQuery`), TypeScript, Vitest + React Testing Library, Vite dev server proxying `/v1` → `127.0.0.1:8080`.

## Global Constraints

- **No visual change.** The frontend is the functional spec; wiring must produce byte-identical UI to the mock render. Any field the component reads must be populated by the mapper.
- **Contract is the source of truth:** every mapper maps a backend `*View` DTO to the exact type in `Frontend/src/types/index.ts` (`StoreItem`, `Event`, `TicketTier`, `Podcast`, `PodcastEpisode`). Never invent fields — align against the DTO **and** the type.
- **Money** is `{ amount: number, currency: 'GHS' }` on the wire (already handled by existing mappers — copy that idiom; do not treat prices as bare numbers).
- **List endpoints return the `Page<T>` envelope** `{ items: T[], page: number, size: number, total: number }` (`backend/.../platform/domain/Page.java`). Unwrap `.items`; keep `total` only if a route needs it.
- **Node 18+** required for Vitest/Vite. If the default `node` is older (v10), run frontend commands under the project Node (e.g. `nvm use 22` first). Verify with `node -v` before running tests.
- **All frontend commands run from `Frontend/`.** Test runner: `npx vitest run <path>`. Lint: `npm run lint`.
- **`/v1` reaches the backend only through the Vite proxy** (`vite.config.ts`), so manual QA uses `http://localhost:5173`, and the backend must be up on `127.0.0.1:8080` with seed data.
- Branch per phase: `feat/frontend-store-wiring`, `feat/frontend-events-wiring`, `feat/frontend-podcasts-wiring` — each is an independently shippable slice with its own PR (matches the repo's slice convention).

---

## File Structure

**Shared (Phase 0):**
- `Frontend/src/lib/api/pagination.ts` (new) — `PageWire<T>` interface + `unwrapPage` helper. One responsibility: the paginated-envelope type used by all three list queries.

**Store (Phase A):**
- `Frontend/src/lib/api/queries/store.ts` (new) — `storeListQuery`, `storeItemQuery`.
- `Frontend/src/lib/api/mappers.ts` (modify) — add `StoreItemWire`, `toStoreItem`.
- `Frontend/src/routes/store.index.tsx`, `store.hifi.tsx`, `store.beats.tsx`, `store.merch.tsx`, `store.exclusives.tsx`, `store.$itemId.tsx` (modify) — fetch via query instead of importing `store-data`.
- `Frontend/src/features/store/components/premium-product-card.tsx` (modify) — accept a `StoreItem` prop; drop the `store-data` import.

**Events (Phase B):**
- `Frontend/src/lib/api/queries/events.ts` (new) — `eventsListQuery`, `eventQuery`.
- `Frontend/src/lib/api/mappers.ts` (modify) — add `EventWire`, `TicketTierWire`, `toEvent`, `toTicketTier`.
- `Frontend/src/routes/events.tsx`, `event.$eventId.tsx` (modify).
- `Frontend/src/features/events/components/event-card.tsx`, `event-list-row.tsx` (modify) — accept an `Event` prop.

**Podcasts (Phase C):**
- `Frontend/src/lib/api/queries/podcasts.ts` (new) — `podcastsListQuery`, `podcastQuery`, `podcastEpisodesQuery`.
- `Frontend/src/lib/api/mappers.ts` (modify) — add `PodcastWire`, `PodcastEpisodeWire`, `toPodcast`, `toPodcastEpisode`.
- `Frontend/src/routes/podcasts.tsx`, `podcast.$podcastId.tsx` (modify).
- `Frontend/src/features/podcasts/components/episode-row.tsx` (modify) — accept a `PodcastEpisode` prop.

**Tests (co-located, one per module):** `lib/api/queries/store.test.ts`, `events.test.ts`, `podcasts.test.ts`; new cases in `lib/api/mappers.test.ts`.

**Out of scope (leave on mock):** `routes/artist/$artistId.tsx` also imports `event-data` for its "upcoming events" strip — do NOT delete `lib/event-data.ts` until that strip is wired separately; just stop the *events browse* pages from importing it. Studio/admin/playlist/notifications/player stay on mocks.

---

## Phase 0 — Shared pagination helper

### Task 0: `PageWire<T>` + `unwrapPage`

**Files:**
- Create: `Frontend/src/lib/api/pagination.ts`
- Test: `Frontend/src/lib/api/pagination.test.ts`

**Interfaces:**
- Produces: `interface PageWire<T> { items: T[]; page: number; size: number; total: number }` and `function unwrapPage<T, U>(page: PageWire<T>, map: (t: T) => U): U[]`.

- [ ] **Step 1: Write the failing test**

```ts
// Frontend/src/lib/api/pagination.test.ts
import { describe, it, expect } from 'vitest'
import { unwrapPage, type PageWire } from './pagination'

describe('unwrapPage', () => {
  it('maps each item and drops the envelope', () => {
    const page: PageWire<{ n: number }> = { items: [{ n: 1 }, { n: 2 }], page: 1, size: 20, total: 2 }
    expect(unwrapPage(page, (x) => x.n * 10)).toEqual([10, 20])
  })
  it('returns [] for an empty page', () => {
    expect(unwrapPage({ items: [], page: 1, size: 20, total: 0 }, (x) => x)).toEqual([])
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Frontend && npx vitest run src/lib/api/pagination.test.ts`
Expected: FAIL — `Failed to resolve import './pagination'`.

- [ ] **Step 3: Write minimal implementation**

```ts
// Frontend/src/lib/api/pagination.ts
export interface PageWire<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export function unwrapPage<T, U>(page: PageWire<T>, map: (t: T) => U): U[] {
  return page.items.map(map)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd Frontend && npx vitest run src/lib/api/pagination.test.ts`
Expected: PASS (2 passed).

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/lib/api/pagination.ts Frontend/src/lib/api/pagination.test.ts
git commit -m "feat(frontend): PageWire envelope helper for list queries"
```

---

## Phase A — Store

**Prep (do once, before Task A1):** Open `backend/.../store/adapter/in/rest/StoreResource.java` and the `StoreItemView` record; open `Frontend/src/types/index.ts` (`StoreItem` at line 178, `StoreItemType` at 153); open `lib/store-data.ts` `getStoreItem`. Write down each field `premium-product-card.tsx`, `store.index.tsx` and `store.$itemId.tsx` read from a `StoreItem`, and confirm `StoreItemView` carries each one (price as `{amount,currency}`). This diff is the acceptance basis for `toStoreItem` — it is not a code step, it is the field list the mapper must satisfy.

### Task A1: `StoreItemWire` + `toStoreItem` mapper

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Consumes: the `StoreItem` / `StoreItemType` types from `../../types`.
- Produces: `export interface StoreItemWire { ... }` and `export function toStoreItem(wire: StoreItemWire): StoreItem`.

- [ ] **Step 1: Write the failing test** (fill the fixture from the real `StoreItemView` field-by-field per Prep; example uses the confirmed money idiom)

```ts
// add to Frontend/src/lib/api/mappers.test.ts
import { toStoreItem, type StoreItemWire } from './mappers'

describe('toStoreItem', () => {
  it('maps a merch item, unwrapping money to a number', () => {
    const wire: StoreItemWire = {
      id: 'merch-bsherif-tee', type: 'MERCH', title: 'Iron Boy Tour Tee',
      artistName: 'Black Sherif', artistId: 'black-sherif',
      image: 'https://img/tee.jpg', price: { amount: 120.0, currency: 'GHS' },
      stockRemaining: 42,
    }
    const item = toStoreItem(wire)
    expect(item.id).toBe('merch-bsherif-tee')
    expect(item.type).toBe('MERCH')
    expect(item.price).toBe(120)
    expect(item.stockRemaining).toBe(42)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Frontend && npx vitest run src/lib/api/mappers.test.ts -t toStoreItem`
Expected: FAIL — `toStoreItem` is not exported.

- [ ] **Step 3: Write minimal implementation** (extend `StoreItemWire`/`toStoreItem` to every field the Prep diff listed; keep the money-unwrap idiom used by `toTrack`)

```ts
// add to Frontend/src/lib/api/mappers.ts
import type { StoreItem, StoreItemType } from '../../types'

export interface StoreItemWire {
  id: string
  type: StoreItemType
  title: string
  artistName: string
  artistId: string | null
  image: string
  price: { amount: number; currency: string }
  stockRemaining?: number | null
  // + every other field StoreItemView serves that StoreItem declares (genre, badges,
  //   description, quality, dropsAt, licenseOptions, …) — see Prep diff.
}

export function toStoreItem(wire: StoreItemWire): StoreItem {
  return {
    id: wire.id,
    type: wire.type,
    title: wire.title,
    artistName: wire.artistName,
    artistId: wire.artistId ?? undefined,
    image: wire.image,
    price: wire.price.amount,
    stockRemaining: wire.stockRemaining ?? undefined,
    // + the remaining StoreItem fields, mapped 1:1
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd Frontend && npx vitest run src/lib/api/mappers.test.ts -t toStoreItem`
Expected: PASS.

- [ ] **Step 5: Typecheck the mapper against the real type**

Run: `cd Frontend && npx tsc --noEmit`
Expected: no errors (proves `toStoreItem`'s return object satisfies `StoreItem` exactly — a missing field fails here).

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/lib/api/mappers.ts Frontend/src/lib/api/mappers.test.ts
git commit -m "feat(frontend): StoreItemWire + toStoreItem mapper"
```

### Task A2: Store query module

**Files:**
- Create: `Frontend/src/lib/api/queries/store.ts`
- Test: `Frontend/src/lib/api/queries/store.test.ts`

**Interfaces:**
- Consumes: `apiFetch` (`../client`), `unwrapPage`/`PageWire` (`../pagination`), `toStoreItem`/`StoreItemWire` (`../mappers`).
- Produces: `storeListQuery(params: { type?: StoreItemType; sort?: string })` and `storeItemQuery(id: string)`, each returning `queryOptions`.

**Store tab→`type` mapping** (frontend tabs → backend `type` query param): `hifi → TRACK,ALBUM` (Hi-Fi masters; if the endpoint takes one `type`, issue no `type` filter for Hi-Fi and filter client-side by `type in {TRACK,ALBUM}`, OR confirm the endpoint accepts CSV — check `StoreResource.list` `type` handling in Prep), `beats → BEAT_LICENSE`, `merch → MERCH`, `exclusives → EXCLUSIVE`, `overview → no type filter`. **Sort** passes through verbatim (`popular|newest|price-asc|price-desc`) — confirm `StoreResource` accepts these exact tokens; if it uses different tokens, map here.

- [ ] **Step 1: Write the failing test** (mock `apiFetch`)

```ts
// Frontend/src/lib/api/queries/store.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as client from '../client'
import { storeListQuery, storeItemQuery } from './store'

vi.mock('../client')

const wireItem = {
  id: 'merch-bsherif-tee', type: 'MERCH', title: 'Iron Boy Tour Tee',
  artistName: 'Black Sherif', artistId: 'black-sherif', image: 'x',
  price: { amount: 120, currency: 'GHS' }, stockRemaining: 42,
}

beforeEach(() => vi.resetAllMocks())

describe('storeListQuery', () => {
  it('requests the merch tab with the sort param and unwraps the page', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({ items: [wireItem], page: 1, size: 20, total: 1 })
    const items = await storeListQuery({ type: 'MERCH', sort: 'newest' }).queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/store?type=MERCH&sort=newest&page=1&size=48')
    expect(items).toHaveLength(1)
    expect(items[0].price).toBe(120)
  })
})

describe('storeItemQuery', () => {
  it('fetches a single item by id', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(wireItem)
    const item = await storeItemQuery('merch-bsherif-tee').queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/store/merch-bsherif-tee')
    expect(item.id).toBe('merch-bsherif-tee')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Frontend && npx vitest run src/lib/api/queries/store.test.ts`
Expected: FAIL — `./store` not found.

- [ ] **Step 3: Write minimal implementation**

```ts
// Frontend/src/lib/api/queries/store.ts
import { queryOptions } from '@tanstack/react-query'
import type { StoreItemType } from '../../../types'
import { apiFetch } from '../client'
import { unwrapPage, type PageWire } from '../pagination'
import { toStoreItem, type StoreItemWire } from '../mappers'

export function storeListQuery(params: { type?: StoreItemType; sort?: string }) {
  const q = new URLSearchParams()
  if (params.type) q.set('type', params.type)
  if (params.sort) q.set('sort', params.sort)
  q.set('page', '1')
  q.set('size', '48')
  return queryOptions({
    queryKey: ['store', 'list', params.type ?? 'all', params.sort ?? 'popular'],
    queryFn: async () =>
      unwrapPage(await apiFetch<PageWire<StoreItemWire>>(`/store?${q.toString()}`), toStoreItem),
  })
}

export function storeItemQuery(id: string) {
  return queryOptions({
    queryKey: ['store', 'item', id],
    queryFn: async () => toStoreItem(await apiFetch<StoreItemWire>(`/store/${id}`)),
  })
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd Frontend && npx vitest run src/lib/api/queries/store.test.ts`
Expected: PASS (2 passed). If the asserted URL differs from your `size`/param choice, reconcile the test and code to one value.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/lib/api/queries/store.ts Frontend/src/lib/api/queries/store.test.ts
git commit -m "feat(frontend): store list + item query module"
```

### Task A3: Make `premium-product-card` presentational

**Files:**
- Modify: `Frontend/src/features/store/components/premium-product-card.tsx`
- Test: `Frontend/src/features/store/components/premium-product-card.test.tsx`

**Interfaces:**
- Produces: component prop `{ item: StoreItem }` (was: internal `getStoreItem`/`store-data` lookup).

- [ ] **Step 1: Write the failing test**

```tsx
// Frontend/src/features/store/components/premium-product-card.test.tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { PremiumProductCard } from './premium-product-card'
import type { StoreItem } from '../../../types'

const item: StoreItem = {
  id: 'merch-bsherif-tee', type: 'MERCH', title: 'Iron Boy Tour Tee',
  artistName: 'Black Sherif', artistId: 'black-sherif', image: 'x', price: 120,
} as StoreItem

describe('PremiumProductCard', () => {
  it('renders the passed item without touching store-data', () => {
    render(<PremiumProductCard item={item} />)
    expect(screen.getByText('Iron Boy Tour Tee')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Frontend && npx vitest run src/features/store/components/premium-product-card.test.tsx`
Expected: FAIL — component still expects an id / imports `store-data` (compile or assertion error).

- [ ] **Step 3: Edit the component** — change its props to accept `item: StoreItem` directly, delete the `import ... from '../../../lib/store-data'` line and any internal lookup, and render from `item`. (Preserve all existing JSX/classes — no visual change.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd Frontend && npx vitest run src/features/store/components/premium-product-card.test.tsx`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/features/store/components/premium-product-card.tsx Frontend/src/features/store/components/premium-product-card.test.tsx
git commit -m "refactor(frontend): premium-product-card takes a StoreItem prop"
```

### Task A4: Wire the store routes to the query

**Files:**
- Modify: `Frontend/src/routes/store.index.tsx`, `store.hifi.tsx`, `store.beats.tsx`, `store.merch.tsx`, `store.exclusives.tsx`, `store.$itemId.tsx`

**Interfaces:**
- Consumes: `storeListQuery`, `storeItemQuery` (Task A2); presentational `PremiumProductCard` (Task A3).

- [ ] **Step 1: Wire each tab route** — pattern (shown for `store.merch.tsx`; apply the same to `store.index` with no `type`, `store.hifi` with the Hi-Fi filter, `store.beats` → `BEAT_LICENSE`, `store.exclusives` → `EXCLUSIVE`). Delete the `store-data` import; add a loader + suspense query; read `sort` from the parent route's search params (`store.tsx` validates `sort`).

```tsx
// Frontend/src/routes/store.merch.tsx (shape — keep the file's existing JSX/layout)
import { createFileRoute, getRouteApi } from '@tanstack/react-router'
import { useSuspenseQuery } from '@tanstack/react-query'
import { storeListQuery } from '../lib/api/queries/store'
import { PremiumProductCard } from '../features/store/components/premium-product-card'

const storeRoute = getRouteApi('/store')

export const Route = createFileRoute('/store/merch')({
  loader: ({ context: { queryClient } }) =>
    queryClient.ensureQueryData(storeListQuery({ type: 'MERCH' })),
  component: MerchTab,
})

function MerchTab() {
  const { sort } = storeRoute.useSearch()
  const { data: items } = useSuspenseQuery(storeListQuery({ type: 'MERCH', sort }))
  return (
    <div className="/* keep existing grid classes */">
      {items.map((item) => (
        <PremiumProductCard key={item.id} item={item} />
      ))}
    </div>
  )
}
```

- [ ] **Step 2: Wire `store.$itemId.tsx`** — replace `getStoreItem(itemId)` with `storeItemQuery`:

```tsx
// Frontend/src/routes/store.$itemId.tsx (loader + component core; keep existing detail JSX)
export const Route = createFileRoute('/store/$itemId')({
  loader: ({ context: { queryClient }, params: { itemId } }) =>
    queryClient.ensureQueryData(storeItemQuery(itemId)),
  component: StoreItemPage,
})
function StoreItemPage() {
  const { itemId } = Route.useParams()
  const { data: item } = useSuspenseQuery(storeItemQuery(itemId))
  // ...existing detail render using `item`
}
```

- [ ] **Step 3: Typecheck + lint**

Run: `cd Frontend && npx tsc --noEmit && npm run lint`
Expected: no errors; no remaining `store-data` import under `routes/store*.tsx` (`grep -rl store-data src/routes/store*` returns nothing).

- [ ] **Step 4: Manual QA against the live backend** (backend up on `127.0.0.1:8080`, `npm run dev`)
  - Visit `http://localhost:5173/store` → Merch/Beats/Hi-Fi/Exclusives tabs render items; the network tab shows `GET /v1/store?...` (not a static bundle).
  - Change `?sort=price-asc` → order changes (proves the param reaches the backend).
  - Open a product → `GET /v1/store/{id}`; the detail matches the seed (e.g. Iron Boy Tour Tee, ₵120, stock reads from backend now).

- [ ] **Step 5: Commit + open PR**

```bash
git add Frontend/src/routes/store.index.tsx Frontend/src/routes/store.hifi.tsx Frontend/src/routes/store.beats.tsx Frontend/src/routes/store.merch.tsx Frontend/src/routes/store.exclusives.tsx Frontend/src/routes/store.\$itemId.tsx
git commit -m "feat(frontend): wire store browse + detail to /v1/store"
```
Open PR `feat/frontend-store-wiring` → `master`, label `area:frontend` (per repo convention), run the frontend gate in CI.

---

## Phase B — Events

**Prep:** Diff `EventView` (`backend/.../events/adapter/in/rest/EventResource.java` + the view record) and its `TicketTierView` against `Event` (types/index.ts:282), `TicketTier` (274), `EventStatus` (270), `EventCategory` (272). **Confirm the list `EventView` includes `tiers`** — `events.tsx` cards may show "from ₵X" (needs cheapest tier) and `event.$eventId.tsx` needs the full tier list. If the list DTO omits tiers, the events-list cards must derive "from" price from a `fromPriceMinor` field or the detail query — decide in this Prep and reflect it in `toEvent`.

### Task B1: `EventWire` + `TicketTierWire` + `toEvent`/`toTicketTier`

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Produces: `EventWire`, `TicketTierWire`, `toEvent(wire): Event`, `toTicketTier(wire): TicketTier`.

- [ ] **Step 1: Write the failing test** (fill fixture from real `EventView` per Prep)

```ts
// add to Frontend/src/lib/api/mappers.test.ts
import { toEvent, type EventWire } from './mappers'

describe('toEvent', () => {
  it('maps an event and its tiers, unwrapping tier money', () => {
    const wire: EventWire = {
      id: 'iron-boy-live', title: 'Iron Boy Live', artistId: 'black-sherif',
      artistName: 'Black Sherif', category: 'Concert', status: 'selling-fast',
      venue: 'Independence Square, Accra', city: 'Accra', dateTime: '2026-07-09T19:00:00Z',
      image: 'x',
      tiers: [{ id: 't1', name: 'Regular', price: { amount: 150, currency: 'GHS' }, perks: ['General standing'] }],
    }
    const ev = toEvent(wire)
    expect(ev.id).toBe('iron-boy-live')
    expect(ev.tiers[0].name).toBe('Regular')
    expect(ev.tiers[0].price).toBe(150)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Frontend && npx vitest run src/lib/api/mappers.test.ts -t toEvent`
Expected: FAIL — `toEvent` not exported.

- [ ] **Step 3: Write minimal implementation** (map every `Event`/`TicketTier` field per Prep)

```ts
// add to Frontend/src/lib/api/mappers.ts
import type { Event, TicketTier, EventStatus, EventCategory } from '../../types'

export interface TicketTierWire {
  id: string
  name: string
  price: { amount: number; currency: string }
  perks: string[]
}
export interface EventWire {
  id: string
  title: string
  artistId: string
  artistName: string
  category: EventCategory
  status: EventStatus
  venue: string
  city: string
  dateTime: string
  image: string
  tiers: TicketTierWire[]
  // + any other Event fields (about, lineup, doorsOpen, agePolicy) present in EventView
}

export function toTicketTier(wire: TicketTierWire): TicketTier {
  return { id: wire.id, name: wire.name, price: wire.price.amount, perks: wire.perks }
}
export function toEvent(wire: EventWire): Event {
  return {
    id: wire.id, title: wire.title, artistId: wire.artistId, artistName: wire.artistName,
    category: wire.category, status: wire.status, venue: wire.venue, city: wire.city,
    dateTime: wire.dateTime, image: wire.image, tiers: wire.tiers.map(toTicketTier),
    // + remaining Event fields
  }
}
```

- [ ] **Step 4: Run test + typecheck**

Run: `cd Frontend && npx vitest run src/lib/api/mappers.test.ts -t toEvent && npx tsc --noEmit`
Expected: PASS + no type errors.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/lib/api/mappers.ts Frontend/src/lib/api/mappers.test.ts
git commit -m "feat(frontend): EventWire/TicketTierWire + toEvent mapper"
```

### Task B2: Events query module

**Files:**
- Create: `Frontend/src/lib/api/queries/events.ts`
- Test: `Frontend/src/lib/api/queries/events.test.ts`

**Interfaces:**
- Produces: `eventsListQuery(params?: { city?: string; category?: string })`, `eventQuery(id: string)`.

- [ ] **Step 1: Write the failing test**

```ts
// Frontend/src/lib/api/queries/events.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as client from '../client'
import { eventsListQuery, eventQuery } from './events'
vi.mock('../client')
const wire = {
  id: 'iron-boy-live', title: 'Iron Boy Live', artistId: 'black-sherif', artistName: 'Black Sherif',
  category: 'Concert', status: 'selling-fast', venue: 'Independence Square, Accra', city: 'Accra',
  dateTime: '2026-07-09T19:00:00Z', image: 'x',
  tiers: [{ id: 't1', name: 'Regular', price: { amount: 150, currency: 'GHS' }, perks: [] }],
}
beforeEach(() => vi.resetAllMocks())
describe('events queries', () => {
  it('lists events and unwraps the page', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({ items: [wire], page: 1, size: 20, total: 1 })
    const evs = await eventsListQuery().queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/events?page=1&size=48')
    expect(evs[0].id).toBe('iron-boy-live')
  })
  it('fetches one event by id', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(wire)
    const ev = await eventQuery('iron-boy-live').queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/events/iron-boy-live')
    expect(ev.tiers[0].price).toBe(150)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Frontend && npx vitest run src/lib/api/queries/events.test.ts`
Expected: FAIL — `./events` not found.

- [ ] **Step 3: Write minimal implementation**

```ts
// Frontend/src/lib/api/queries/events.ts
import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { unwrapPage, type PageWire } from '../pagination'
import { toEvent, type EventWire } from '../mappers'

export function eventsListQuery(params: { city?: string; category?: string } = {}) {
  const q = new URLSearchParams()
  if (params.city) q.set('city', params.city)
  if (params.category) q.set('category', params.category)
  q.set('page', '1')
  q.set('size', '48')
  return queryOptions({
    queryKey: ['events', 'list', params.city ?? 'all', params.category ?? 'all'],
    queryFn: async () =>
      unwrapPage(await apiFetch<PageWire<EventWire>>(`/events?${q.toString()}`), toEvent),
  })
}

export function eventQuery(id: string) {
  return queryOptions({
    queryKey: ['events', 'item', id],
    queryFn: async () => toEvent(await apiFetch<EventWire>(`/events/${id}`)),
  })
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd Frontend && npx vitest run src/lib/api/queries/events.test.ts`
Expected: PASS (2 passed).

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/lib/api/queries/events.ts Frontend/src/lib/api/queries/events.test.ts
git commit -m "feat(frontend): events list + detail query module"
```

### Task B3: Make event card + list-row presentational

**Files:**
- Modify: `Frontend/src/features/events/components/event-card.tsx`, `event-list-row.tsx`
- Test: `Frontend/src/features/events/components/event-card.test.tsx`

**Interfaces:**
- Produces: `event-card` prop `{ event: Event }`; `event-list-row` prop `{ event: Event }` (drop the `event-data` imports).

- [ ] **Step 1: Write the failing test**

```tsx
// Frontend/src/features/events/components/event-card.test.tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { EventCard } from './event-card'
import type { Event } from '../../../types'
const event = {
  id: 'iron-boy-live', title: 'Iron Boy Live', artistName: 'Black Sherif', city: 'Accra',
  category: 'Concert', status: 'selling-fast', image: 'x', dateTime: '2026-07-09T19:00:00Z',
  venue: 'Independence Square, Accra', artistId: 'black-sherif',
  tiers: [{ id: 't1', name: 'Regular', price: 150, perks: [] }],
} as Event
describe('EventCard', () => {
  it('renders the passed event', () => {
    render(<EventCard event={event} />)
    expect(screen.getByText('Iron Boy Live')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Frontend && npx vitest run src/features/events/components/event-card.test.tsx`
Expected: FAIL — component takes an id / imports `event-data`.

- [ ] **Step 3: Edit both components** to accept `{ event: Event }` and render from the prop; delete their `event-data` imports. Preserve all JSX/classes.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd Frontend && npx vitest run src/features/events/components/event-card.test.tsx`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/features/events/components/event-card.tsx Frontend/src/features/events/components/event-list-row.tsx Frontend/src/features/events/components/event-card.test.tsx
git commit -m "refactor(frontend): event card + row take an Event prop"
```

### Task B4: Wire the events routes

**Files:**
- Modify: `Frontend/src/routes/events.tsx`, `event.$eventId.tsx`

- [ ] **Step 1: Wire `events.tsx`** (list) — loader + suspense on `eventsListQuery()`, map to `EventCard`/`EventListRow`; delete `event-data` import.

```tsx
// Frontend/src/routes/events.tsx (loader + component core; keep existing layout/filters)
export const Route = createFileRoute('/events')({
  loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(eventsListQuery()),
  component: EventsPage,
})
function EventsPage() {
  const { data: events } = useSuspenseQuery(eventsListQuery())
  // ...existing render: events.map((e) => <EventCard key={e.id} event={e} />)
}
```

- [ ] **Step 2: Wire `event.$eventId.tsx`** — replace `getEvent(eventId)` with `eventQuery`. Keep `getArtist` from `mock-data` ONLY if the page shows artist bio and no artist query is desired here; prefer `artistQuery` from `lib/api/queries/catalog` (already real) to also drop that mock. Delete the `event-data` import.

```tsx
export const Route = createFileRoute('/event/$eventId')({
  loader: ({ context: { queryClient }, params: { eventId } }) =>
    queryClient.ensureQueryData(eventQuery(eventId)),
  component: EventDetail,
})
function EventDetail() {
  const { eventId } = Route.useParams()
  const { data: event } = useSuspenseQuery(eventQuery(eventId))
  // ...existing tier selector + "Buy with MoMo" (cart wiring already real); render from `event`
}
```

- [ ] **Step 3: Typecheck + lint**

Run: `cd Frontend && npx tsc --noEmit && npm run lint`
Expected: no errors; `grep -rl event-data src/routes/events.tsx src/routes/event.\$eventId.tsx` returns nothing.

- [ ] **Step 4: Manual QA**
  - `/events` renders cards from `GET /v1/events`.
  - Open Iron Boy Live → `GET /v1/events/iron-boy-live`; tiers Regular ₵150 / VIP ₵400 / VVIP ₵2500 render from backend.
  - Add a Regular ticket → cart shows `iron-boy-live:Regular` (the cart/checkout path is already real, so this end-to-ends).

- [ ] **Step 5: Commit + open PR**

```bash
git add Frontend/src/routes/events.tsx Frontend/src/routes/event.\$eventId.tsx
git commit -m "feat(frontend): wire events browse + detail to /v1/events"
```
Open PR `feat/frontend-events-wiring`. (Do NOT delete `lib/event-data.ts` — `routes/artist/$artistId.tsx` still imports it; note that as a follow-up.)

---

## Phase C — Podcasts

**Prep:** Diff `PodcastView`/`PodcastEpisodeView` (`backend/.../podcasts/adapter/in/rest/*Resource*.java`) against `Podcast` (types/index.ts:225), `PodcastEpisode` (241), `PodcastCategory` (215). Note the episode **stream URL** is a separate call (`GET /v1/podcasts/episodes/{id}/stream` → `StreamUrlResponse`) — episodes list carries metadata; the player fetches the signed URL on play. Confirm whether `episode-row`/player needs the stream URL eagerly (it should be lazy, on play).

### Task C1: `PodcastWire`/`PodcastEpisodeWire` + `toPodcast`/`toPodcastEpisode`

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Produces: `PodcastWire`, `PodcastEpisodeWire`, `toPodcast(wire): Podcast`, `toPodcastEpisode(wire): PodcastEpisode`.

- [ ] **Step 1: Write the failing test** (fill from real `PodcastView` per Prep)

```ts
// add to Frontend/src/lib/api/mappers.test.ts
import { toPodcast, type PodcastWire } from './mappers'
describe('toPodcast', () => {
  it('maps a show', () => {
    const wire: PodcastWire = {
      id: 'the-233-pod', title: 'The 233 Podcast', creatorName: 'Ama Serwaa',
      creatorAccountId: 'ama-serwaa', category: 'Culture', image: 'x', episodeCount: 12,
    }
    const p = toPodcast(wire)
    expect(p.id).toBe('the-233-pod')
    expect(p.title).toBe('The 233 Podcast')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Frontend && npx vitest run src/lib/api/mappers.test.ts -t toPodcast`
Expected: FAIL — `toPodcast` not exported.

- [ ] **Step 3: Write minimal implementation** (map every field per Prep; money fields — episode/season-pass price — use the `{amount,currency}` unwrap idiom)

```ts
// add to Frontend/src/lib/api/mappers.ts
import type { Podcast, PodcastEpisode, PodcastCategory } from '../../types'

export interface PodcastWire {
  id: string
  title: string
  creatorName: string
  creatorAccountId: string | null
  category: PodcastCategory
  image: string
  episodeCount: number
  // + remaining Podcast fields present in PodcastView (description, …)
}
export interface PodcastEpisodeWire {
  id: string
  podcastId: string
  title: string
  durationSeconds: number
  publishedAt: string
  forSale?: boolean
  price?: { amount: number; currency: string } | null
  // + remaining PodcastEpisode fields
}
export function toPodcast(wire: PodcastWire): Podcast {
  return {
    id: wire.id, title: wire.title, creatorName: wire.creatorName,
    creatorAccountId: wire.creatorAccountId ?? undefined, category: wire.category,
    image: wire.image, episodeCount: wire.episodeCount,
  }
}
export function toPodcastEpisode(wire: PodcastEpisodeWire): PodcastEpisode {
  return {
    id: wire.id, podcastId: wire.podcastId, title: wire.title,
    durationSeconds: wire.durationSeconds, publishedAt: wire.publishedAt,
    forSale: wire.forSale ?? false, price: wire.price?.amount,
  }
}
```

- [ ] **Step 4: Run test + typecheck**

Run: `cd Frontend && npx vitest run src/lib/api/mappers.test.ts -t toPodcast && npx tsc --noEmit`
Expected: PASS + no type errors.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/lib/api/mappers.ts Frontend/src/lib/api/mappers.test.ts
git commit -m "feat(frontend): PodcastWire/PodcastEpisodeWire + mappers"
```

### Task C2: Podcasts query module

**Files:**
- Create: `Frontend/src/lib/api/queries/podcasts.ts`
- Test: `Frontend/src/lib/api/queries/podcasts.test.ts`

**Interfaces:**
- Produces: `podcastsListQuery(params?: { category?: string })`, `podcastQuery(id)`, `podcastEpisodesQuery(id)`.

- [ ] **Step 1: Write the failing test**

```ts
// Frontend/src/lib/api/queries/podcasts.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as client from '../client'
import { podcastsListQuery, podcastQuery, podcastEpisodesQuery } from './podcasts'
vi.mock('../client')
const show = { id: 'the-233-pod', title: 'The 233 Podcast', creatorName: 'Ama Serwaa', creatorAccountId: 'ama-serwaa', category: 'Culture', image: 'x', episodeCount: 1 }
const ep = { id: 'ep-1', podcastId: 'the-233-pod', title: 'Ep 1', durationSeconds: 1800, publishedAt: '2026-07-01T00:00:00Z' }
beforeEach(() => vi.resetAllMocks())
describe('podcasts queries', () => {
  it('lists shows (page)', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({ items: [show], page: 1, size: 20, total: 1 })
    const shows = await podcastsListQuery().queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/podcasts?page=1&size=48')
    expect(shows[0].id).toBe('the-233-pod')
  })
  it('fetches a show + its episodes', async () => {
    vi.mocked(client.apiFetch).mockResolvedValueOnce(show)
    const p = await podcastQuery('the-233-pod').queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/podcasts/the-233-pod')
    expect(p.title).toBe('The 233 Podcast')
    vi.mocked(client.apiFetch).mockResolvedValueOnce([ep])
    const eps = await podcastEpisodesQuery('the-233-pod').queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/podcasts/the-233-pod/episodes')
    expect(eps[0].id).toBe('ep-1')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd Frontend && npx vitest run src/lib/api/queries/podcasts.test.ts`
Expected: FAIL — `./podcasts` not found.

- [ ] **Step 3: Write minimal implementation**

```ts
// Frontend/src/lib/api/queries/podcasts.ts
import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { unwrapPage, type PageWire } from '../pagination'
import { toPodcast, toPodcastEpisode, type PodcastWire, type PodcastEpisodeWire } from '../mappers'

export function podcastsListQuery(params: { category?: string } = {}) {
  const q = new URLSearchParams()
  if (params.category) q.set('category', params.category)
  q.set('page', '1')
  q.set('size', '48')
  return queryOptions({
    queryKey: ['podcasts', 'list', params.category ?? 'all'],
    queryFn: async () =>
      unwrapPage(await apiFetch<PageWire<PodcastWire>>(`/podcasts?${q.toString()}`), toPodcast),
  })
}
export function podcastQuery(id: string) {
  return queryOptions({
    queryKey: ['podcasts', 'item', id],
    queryFn: async () => toPodcast(await apiFetch<PodcastWire>(`/podcasts/${id}`)),
  })
}
export function podcastEpisodesQuery(id: string) {
  return queryOptions({
    queryKey: ['podcasts', 'item', id, 'episodes'],
    queryFn: async () => (await apiFetch<PodcastEpisodeWire[]>(`/podcasts/${id}/episodes`)).map(toPodcastEpisode),
  })
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd Frontend && npx vitest run src/lib/api/queries/podcasts.test.ts`
Expected: PASS (2 passed).

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/lib/api/queries/podcasts.ts Frontend/src/lib/api/queries/podcasts.test.ts
git commit -m "feat(frontend): podcasts list + show + episodes query module"
```

### Task C3: Make `episode-row` presentational + wire the podcasts routes

**Files:**
- Modify: `Frontend/src/features/podcasts/components/episode-row.tsx`, `Frontend/src/routes/podcasts.tsx`, `Frontend/src/routes/podcast.$podcastId.tsx`
- Test: `Frontend/src/features/podcasts/components/episode-row.test.tsx`

- [ ] **Step 1: Write the failing test for `episode-row`**

```tsx
// Frontend/src/features/podcasts/components/episode-row.test.tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { EpisodeRow } from './episode-row'
import type { PodcastEpisode } from '../../../types'
const ep = { id: 'ep-1', podcastId: 'the-233-pod', title: 'Ep 1', durationSeconds: 1800, publishedAt: '2026-07-01T00:00:00Z', forSale: false } as PodcastEpisode
describe('EpisodeRow', () => {
  it('renders the passed episode', () => {
    render(<EpisodeRow episode={ep} />)
    expect(screen.getByText('Ep 1')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd Frontend && npx vitest run src/features/podcasts/components/episode-row.test.tsx`
Expected: FAIL — component imports `podcast-data` / takes an id.

- [ ] **Step 3: Edit `episode-row.tsx`** to accept `{ episode: PodcastEpisode }` and render from the prop; drop the `podcast-data` import.

- [ ] **Step 4: Wire `podcasts.tsx`** (list) and `podcast.$podcastId.tsx` (show + episodes):

```tsx
// Frontend/src/routes/podcasts.tsx (core)
export const Route = createFileRoute('/podcasts')({
  loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(podcastsListQuery()),
  component: PodcastsPage,
})
function PodcastsPage() {
  const { data: shows } = useSuspenseQuery(podcastsListQuery())
  // ...existing rails render from `shows`
}
```
```tsx
// Frontend/src/routes/podcast.$podcastId.tsx (core)
export const Route = createFileRoute('/podcast/$podcastId')({
  loader: async ({ context: { queryClient }, params: { podcastId } }) => {
    await queryClient.ensureQueryData(podcastQuery(podcastId))
    await queryClient.ensureQueryData(podcastEpisodesQuery(podcastId))
  },
  component: PodcastDetail,
})
function PodcastDetail() {
  const { podcastId } = Route.useParams()
  const { data: show } = useSuspenseQuery(podcastQuery(podcastId))
  const { data: episodes } = useSuspenseQuery(podcastEpisodesQuery(podcastId))
  // ...existing render: episodes.map((e) => <EpisodeRow key={e.id} episode={e} />)
}
```

- [ ] **Step 5: Typecheck + lint + tests**

Run: `cd Frontend && npx tsc --noEmit && npm run lint && npx vitest run src/features/podcasts src/lib/api/queries/podcasts.test.ts`
Expected: green; no `podcast-data` import under `src/routes/podcasts.tsx` / `podcast.$podcastId.tsx`.

- [ ] **Step 6: Manual QA**
  - `/podcasts` renders shows from `GET /v1/podcasts`.
  - Open a show → `GET /v1/podcasts/{id}` + `GET /v1/podcasts/{id}/episodes`; episode list renders.
  - Play an episode → the player fetches `GET /v1/podcasts/episodes/{id}/stream` on play (lazy), audio starts. (If a for-sale episode is preview-gated, that is the backend's INV-3 behavior — expected.)

- [ ] **Step 7: Commit + open PR**

```bash
git add Frontend/src/features/podcasts/components/episode-row.tsx Frontend/src/features/podcasts/components/episode-row.test.tsx Frontend/src/routes/podcasts.tsx Frontend/src/routes/podcast.\$podcastId.tsx
git commit -m "feat(frontend): wire podcasts browse + show + episodes to /v1/podcasts"
```
Open PR `feat/frontend-podcasts-wiring`. (Do NOT delete `lib/podcast-data.ts` if studio podcast routes still import it — confirm with `grep -rl podcast-data src`.)

---

## Cross-cutting acceptance (run per phase before its PR)

- **No mock import remains in the wired routes:** `grep -rl store-data src/routes/store*` (Phase A), `grep -rl event-data src/routes/events.tsx src/routes/event.\$eventId.tsx` (Phase B), `grep -rl podcast-data src/routes/podcasts.tsx src/routes/podcast.\$podcastId.tsx` (Phase C) each return nothing.
- **Full frontend suite green:** `cd Frontend && npx vitest run` and `npm run lint`.
- **Visual parity:** compare the wired page to `git stash`-ed mock render — identical layout/labels (the whole point: "no visual change").
- **Network proof:** browser Network tab shows the real `/v1/...` request for each wired page (not a static bundle read).

---

## Self-Review

**Spec coverage:** the ask — "wire store, events, podcasts browse pages to existing backend endpoints" — is covered by Phase A (store list + tabs + detail), Phase B (events list + detail), Phase C (podcasts list + show + episodes). Each maps to a live endpoint confirmed present (`GET /v1/store`, `/v1/store/{id}`, `/v1/events`, `/v1/events/{id}`, `/v1/podcasts`, `/v1/podcasts/{id}`, `/v1/podcasts/{id}/episodes`). Detail/tier concerns (EventView tiers, store tab→type mapping, lazy stream URL) are each pinned to a Prep step with a decision, not left open.

**Placeholder scan:** no "TBD"/"handle edge cases". Where an exact field list can only come from the live `*View` DTO, the plan names the DTO file and makes the field diff an explicit Prep step + a `tsc --noEmit` gate that fails on any missing field — that is a verification instruction, not a placeholder.

**Type consistency:** `toStoreItem`/`toEvent`/`toTicketTier`/`toPodcast`/`toPodcastEpisode`, the `*Wire` interfaces, `PageWire`/`unwrapPage`, and the query names (`storeListQuery`/`storeItemQuery`, `eventsListQuery`/`eventQuery`, `podcastsListQuery`/`podcastQuery`/`podcastEpisodesQuery`) are used consistently across every task that references them.

**Known risk (flag at execution):** the current dev JWT expires quickly with no silent refresh (observed during live testing) — long manual-QA sessions may get bounced to login. Not caused by this work; note it if QA is interrupted.
