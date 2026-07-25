# Frontend Admin Catalog & Moderation Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the admin Catalog list (`admin.catalog`), Catalog detail (`admin.catalog.$itemId`), and Moderation queue (`admin.moderation`) from the `admin-data` mock to the live `AdminCatalogResource` / `AdminModerationResource` endpoints, with no visual change.

**Architecture:** Same idiom as the merged Users slice (#165): per-domain `queries/admin-catalog.ts` + `queries/admin-moderation.ts` (TanStack `queryOptions` reads + free `api*` mutation fns), wire types + `toX` mappers in the shared `lib/api/mappers.ts`, routes swapped from `useState(mock)` to `useQuery(...)` + `useQueryClient().invalidateQueries`. Reuses the shared `AdminLoadError` (already on master) and `format.ts` helpers `relativeTime` / `relativeTimeAgo` / `formatDuration`.

**Tech Stack:** React 18, TanStack Query v5, TanStack Router, Vitest + RTL, TypeScript (`tsc -b`), Tailwind.

## Global Constraints

- **No visual change.** JSX, `className`, copy, and layout preserved verbatim; only the data source + wired actions change.
- **Node 22 via nvm** for all `npm`/`npx`: prefix with `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null &&`.
- **Real typecheck gate is `npm run build`** (`tsc -b`), not vitest. Every task runs both.
- **NEVER stage** `backend/src/main/resources/application.properties` or `backend/docker-compose.yml`. This is a frontend-only branch — do not touch backend.
- **Mapper outputs match `admin-data.ts` types exactly:** `CatalogItem`, `CatalogStatus`, `CatalogType`, `ModerationItem`, `ModStatus`, `ModSeverity`, `ModReason`. Backend enum wire values already match these unions verbatim (`pending/flagged/published/takedown`, `Album/Single/EP/Compilation/Mixtape`, `open/in_review/resolved`, `high/med/low`, and the ModReason display strings) — mappers cast, they do not translate.
- **Query-key convention:** `['admin','catalog','list']`, `['admin','catalog','detail', id]`, `['admin','moderation','queue']`.
- **New imports in the TOP import block; `import type` for type-only imports** (repo uses `verbatimModuleSyntax`).
- **Toast variants** are `'success' | 'error' | 'info'`. Failures toast `'error'`; keep every existing success/info toast's copy verbatim.
- **List/bulk default reason:** the list-row **Take down** and any reason-less takedown send the exact string `Taken down from catalog list` (the backend `takedown` reason is `@NotBlank`; the list UI collects none). Bulk approve = `Promise.all` of `apiApproveCatalog`.
- **Category B (do NOT wire; leave as existing toasts / unrendered):** catalog list header summary (`CATALOG_SUMMARY`), catalog detail track **Preview/Play**, detail Metadata **genre** ("Hiplife / Drill") and **label** (derived from artist — no backend field), the **Reinstate** endpoint (no button — do not add), track **price** (`priceMinor` served but the mock renders no price), moderation **Remove** reason (UI collects none). The moderation **Escalate** action now persists server-side but keeps its existing toast and has no visual indicator.

---

### Task 1: Catalog mappers + wire types

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts` (imports at top block; types + functions at end)
- Test: `Frontend/src/lib/api/mappers.test.ts` (add cases; imports at top block)

**Interfaces:**
- Consumes: `CatalogItem`, `CatalogStatus`, `CatalogType` from `../../admin-data`; `formatDuration`, `relativeTimeAgo` from `../../format`.
- Produces (used by Task 2/3/4): types `CatalogItemWire`, `CatalogCountsWire`, `PagedCatalogWire`, `CatalogTrackWire`, `CatalogSplitWire`, `CatalogActionLogWire`, `CatalogDetailWire`, `CatalogCounts`, `CatalogList`, `CatalogDetailTrack`, `CatalogSplit`, `CatalogLogEntry`, `CatalogDetail`; functions `toCatalogItem`, `toCatalogList`, `toCatalogDetail`.

- [ ] **Step 1: Write the failing tests**

Add to the top import block of `Frontend/src/lib/api/mappers.test.ts`:

```ts
import {
  toCatalogItem, toCatalogList, toCatalogDetail,
  type PagedCatalogWire, type CatalogDetailWire,
} from './mappers'
```

Append these tests:

```ts
describe('admin catalog mappers', () => {
  const rowWire = { id: 'c1', title: 'Iron Boy', note: 'submitted 2h ago', artist: 'Black Sherif', type: 'Album', tracks: 14, status: 'pending' }

  it('toCatalogItem maps 1:1 with narrowed unions and null note → undefined', () => {
    expect(toCatalogItem(rowWire)).toEqual({
      id: 'c1', title: 'Iron Boy', note: 'submitted 2h ago', artist: 'Black Sherif', type: 'Album', tracks: 14, status: 'pending',
    })
    expect(toCatalogItem({ ...rowWire, note: null }).note).toBeUndefined()
  })

  it('toCatalogList maps items + the three counts', () => {
    const wire: PagedCatalogWire = { items: [rowWire], page: 1, size: 100, total: 1, counts: { pending: 24, published: 18396, takedown: 8 } }
    const list = toCatalogList(wire)
    expect(list.items).toHaveLength(1)
    expect(list.items[0].title).toBe('Iron Boy')
    expect(list.counts).toEqual({ pending: 24, published: 18396, takedown: 8 })
  })

  it('toCatalogDetail formats duration + relative log time and projects splits', () => {
    const wire: CatalogDetailWire = {
      id: 'c1', title: 'Iron Boy', note: null, artist: 'Black Sherif', type: 'Album', status: 'pending', upc: 'BZ900123',
      tracklist: [{ position: 1, trackId: 't1', title: 'Intro', isrc: 'GHA-26-1001', durationSec: 132, priceMinor: 500 }],
      splits: [{ trackId: 't1', name: 'Black Sherif', role: 'Primary artist', percent: 70, confirmation: 'confirmed' }],
      actionLog: [{ id: 'l1', action: 'Submitted', by: 'system', time: '2026-07-24T10:00:00Z' }],
    }
    const d = toCatalogDetail(wire, 1721815200000) // now = 2024-07-24T10:00:00Z fixed; only checks it's a string
    expect(d.upc).toBe('BZ900123')
    expect(d.tracks).toEqual([{ position: 1, title: 'Intro', isrc: 'GHA-26-1001', duration: '2:12' }])
    expect(d.splits).toEqual([{ name: 'Black Sherif', role: 'Primary artist', pct: 70 }])
    expect(d.log[0].action).toBe('Submitted')
    expect(typeof d.log[0].time).toBe('string')
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/mappers.test.ts`
Expected: FAIL — the three functions are not exported.

- [ ] **Step 3: Add imports to the top block of `mappers.ts`**

```ts
import type { CatalogItem, CatalogStatus, CatalogType } from '../../admin-data'
import { formatDuration, relativeTimeAgo } from '../../format'
```

(If a prior task in this branch already added an `admin-data` import line, extend it rather than duplicating.)

- [ ] **Step 4: Append the wire types + result types + functions at the END of `mappers.ts`**

```ts
// ── Admin catalog (AdminCatalogResource) ──────────────────────────────────────
export interface CatalogItemWire {
  id: string
  title: string
  note: string | null
  artist: string
  type: string
  tracks: number
  status: string
}
export interface CatalogCountsWire { pending: number; published: number; takedown: number }
export interface PagedCatalogWire {
  items: CatalogItemWire[]
  page: number
  size: number
  total: number
  counts: CatalogCountsWire
}
export interface CatalogTrackWire {
  position: number
  trackId: string
  title: string
  isrc: string
  durationSec: number
  priceMinor: number
}
export interface CatalogSplitWire { trackId: string; name: string; role: string; percent: number; confirmation: string }
export interface CatalogActionLogWire { id: string; action: string; by: string; time: string }
export interface CatalogDetailWire {
  id: string
  title: string
  note: string | null
  artist: string
  type: string
  status: string
  upc: string
  tracklist: CatalogTrackWire[]
  splits: CatalogSplitWire[]
  actionLog: CatalogActionLogWire[]
}

export interface CatalogCounts { pending: number; published: number; takedown: number }
export interface CatalogList { items: CatalogItem[]; counts: CatalogCounts }
export interface CatalogDetailTrack { position: number; title: string; isrc: string; duration: string }
export interface CatalogSplit { name: string; role: string; pct: number }
export interface CatalogLogEntry { id: string; action: string; time: string }
export interface CatalogDetail {
  id: string
  title: string
  note?: string
  artist: string
  type: CatalogType
  status: CatalogStatus
  upc: string
  tracks: CatalogDetailTrack[]
  splits: CatalogSplit[]
  log: CatalogLogEntry[]
}

export function toCatalogItem(w: CatalogItemWire): CatalogItem {
  return {
    id: w.id,
    title: w.title,
    note: w.note ?? undefined,
    artist: w.artist,
    type: w.type as CatalogType,
    tracks: w.tracks,
    status: w.status as CatalogStatus,
  }
}

export function toCatalogList(w: PagedCatalogWire): CatalogList {
  return {
    items: w.items.map(toCatalogItem),
    counts: { pending: w.counts.pending, published: w.counts.published, takedown: w.counts.takedown },
  }
}

export function toCatalogDetail(w: CatalogDetailWire, now?: number): CatalogDetail {
  return {
    id: w.id,
    title: w.title,
    note: w.note ?? undefined,
    artist: w.artist,
    type: w.type as CatalogType,
    status: w.status as CatalogStatus,
    upc: w.upc,
    tracks: w.tracklist.map((t) => ({ position: t.position, title: t.title, isrc: t.isrc, duration: formatDuration(t.durationSec) })),
    splits: w.splits.map((s) => ({ name: s.name, role: s.role, pct: s.percent })),
    log: w.actionLog.map((l) => ({ id: l.id, action: l.action, time: relativeTimeAgo(l.time, now) })),
  }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/mappers.test.ts`
Expected: PASS (existing cases unaffected; 3 new cases green).

- [ ] **Step 6: Typecheck**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build`
Expected: 0 errors.

- [ ] **Step 7: Commit**

```bash
git add Frontend/src/lib/api/mappers.ts Frontend/src/lib/api/mappers.test.ts
git commit -m "feat(admin): catalog wire mappers (list, counts, detail)"
```

---

### Task 2: Catalog query + mutation layer

**Files:**
- Create: `Frontend/src/lib/api/queries/admin-catalog.ts`
- Test: `Frontend/src/lib/api/queries/admin-catalog.test.ts`

**Interfaces:**
- Consumes: `toCatalogList`, `toCatalogDetail`, `PagedCatalogWire`, `CatalogDetailWire` from `../mappers`; `apiFetch` from `../client`.
- Produces (used by Task 3/4): `catalogQuery()` (key `['admin','catalog','list']`), `catalogItemQuery(id)` (key `['admin','catalog','detail', id]`), `apiApproveCatalog(id): Promise<void>`, `apiFlagCatalog(id, note?): Promise<void>`, `apiTakedownCatalog(id, reason): Promise<void>`.

- [ ] **Step 1: Write the failing test**

Create `Frontend/src/lib/api/queries/admin-catalog.test.ts`:

```ts
import { afterEach, describe, expect, it, vi } from 'vitest'
import { catalogQuery, catalogItemQuery, apiApproveCatalog, apiFlagCatalog, apiTakedownCatalog } from './admin-catalog'

const pagedWire = {
  items: [{ id: 'c1', title: 'Iron Boy', note: null, artist: 'Black Sherif', type: 'Album', tracks: 14, status: 'pending' }],
  page: 1, size: 100, total: 1, counts: { pending: 1, published: 0, takedown: 0 },
}
const detailWire = {
  id: 'c1', title: 'Iron Boy', note: null, artist: 'Black Sherif', type: 'Album', status: 'pending', upc: 'BZ1',
  tracklist: [], splits: [], actionLog: [],
}
function mockFetch(status: number, json: unknown) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300, status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => json, text: async () => JSON.stringify(json),
  } as Response)
}
afterEach(() => vi.restoreAllMocks())

describe('admin-catalog queries', () => {
  it('catalogQuery hits /v1/admin/catalog and maps items + counts', async () => {
    const f = mockFetch(200, pagedWire); vi.stubGlobal('fetch', f)
    const result = await catalogQuery().queryFn!({} as never)
    expect(f).toHaveBeenCalledWith('/v1/admin/catalog', expect.objectContaining({ method: 'GET' }))
    expect(result.items[0].title).toBe('Iron Boy')
    expect(result.counts.pending).toBe(1)
    expect(catalogQuery().queryKey).toEqual(['admin', 'catalog', 'list'])
  })

  it('catalogItemQuery hits /v1/admin/catalog/:id and keys by id', async () => {
    const f = mockFetch(200, detailWire); vi.stubGlobal('fetch', f)
    const result = await catalogItemQuery('c1').queryFn!({} as never)
    expect(f).toHaveBeenCalledWith('/v1/admin/catalog/c1', expect.objectContaining({ method: 'GET' }))
    expect(result.id).toBe('c1')
    expect(catalogItemQuery('c1').queryKey).toEqual(['admin', 'catalog', 'detail', 'c1'])
  })

  it('apiApproveCatalog POSTs to /approve', async () => {
    const f = mockFetch(200, detailWire); vi.stubGlobal('fetch', f)
    await apiApproveCatalog('c1')
    expect(f).toHaveBeenCalledWith('/v1/admin/catalog/c1/approve', expect.objectContaining({ method: 'POST' }))
  })

  it('apiFlagCatalog POSTs optional note to /flag', async () => {
    const f = mockFetch(200, detailWire); vi.stubGlobal('fetch', f)
    await apiFlagCatalog('c1', 'dupe')
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/catalog/c1/flag')
    expect(opts.method).toBe('POST')
    expect(JSON.parse(opts.body)).toEqual({ note: 'dupe' })
  })

  it('apiTakedownCatalog POSTs reason to /takedown', async () => {
    const f = mockFetch(200, detailWire); vi.stubGlobal('fetch', f)
    await apiTakedownCatalog('c1', 'Copyright claim')
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/catalog/c1/takedown')
    expect(JSON.parse(opts.body)).toEqual({ reason: 'Copyright claim' })
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/queries/admin-catalog.test.ts`
Expected: FAIL — `./admin-catalog` does not exist.

- [ ] **Step 3: Write the query module**

Create `Frontend/src/lib/api/queries/admin-catalog.ts`:

```ts
import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { toCatalogList, toCatalogDetail, type PagedCatalogWire, type CatalogDetailWire } from '../mappers'

/** `GET /v1/admin/catalog` — the full catalog list plus filter-chip counts. */
export function catalogQuery() {
  return queryOptions({
    queryKey: ['admin', 'catalog', 'list'],
    queryFn: async () => toCatalogList(await apiFetch<PagedCatalogWire>('/admin/catalog')),
  })
}

/** `GET /v1/admin/catalog/:id` — one release's detail (tracklist, splits, action log). */
export function catalogItemQuery(id: string) {
  return queryOptions({
    queryKey: ['admin', 'catalog', 'detail', id],
    queryFn: async () => toCatalogDetail(await apiFetch<CatalogDetailWire>(`/admin/catalog/${id}`)),
  })
}

/** `POST /v1/admin/catalog/:id/approve` — publish a pending/flagged release. */
export function apiApproveCatalog(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/catalog/${id}/approve`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/catalog/:id/flag` — flag for review; note optional. */
export function apiFlagCatalog(id: string, note?: string): Promise<void> {
  return apiFetch<unknown>(`/admin/catalog/${id}/flag`, { method: 'POST', body: note === undefined ? undefined : { note } }).then(() => undefined)
}

/** `POST /v1/admin/catalog/:id/takedown` — reason is required (non-blank). */
export function apiTakedownCatalog(id: string, reason: string): Promise<void> {
  return apiFetch<unknown>(`/admin/catalog/${id}/takedown`, { method: 'POST', body: { reason } }).then(() => undefined)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/queries/admin-catalog.test.ts`
Expected: PASS (5 tests).

- [ ] **Step 5: Typecheck**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build`
Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/lib/api/queries/admin-catalog.ts Frontend/src/lib/api/queries/admin-catalog.test.ts
git commit -m "feat(admin): catalog query + mutation layer"
```

---

### Task 3: Wire the Catalog list

**Files:**
- Modify: `Frontend/src/routes/admin.catalog.tsx`

**Interfaces:**
- Consumes: `catalogQuery`, `apiApproveCatalog`, `apiFlagCatalog`, `apiTakedownCatalog` from `../lib/api/queries/admin-catalog`; `AdminLoadError` from `../components/admin/load-error`; `useQuery`, `useQueryClient` from `@tanstack/react-query`.

- [ ] **Step 1: Replace the imports + mock seed**

Replace the mock import line

```ts
import { getCatalog, CATALOG_SUMMARY, CATALOG_COUNTS, type CatalogItem, type CatalogStatus } from '../lib/admin-data'
```

with (note: `CATALOG_SUMMARY` stays — it feeds the Category-B header summary; `getCatalog`/`CATALOG_COUNTS` are gone; `CatalogItem`/`CatalogStatus` stay as types):

```ts
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CATALOG_SUMMARY, type CatalogItem, type CatalogStatus } from '../lib/admin-data'
import { catalogQuery, apiApproveCatalog, apiFlagCatalog, apiTakedownCatalog } from '../lib/api/queries/admin-catalog'
import { AdminLoadError } from '../components/admin/load-error'
```

- [ ] **Step 2: Move FILTERS inside the component; swap state for the query**

Delete the module-level `FILTERS` array (lines 16-21). Keep the `FilterKey` type and `inFilter`. Inside `AdminCatalog()`, replace the state/derivation block (lines 33-56) with:

```ts
  const { toast } = useToast()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data, isError, refetch } = useQuery(catalogQuery())
  const items = data?.items ?? []
  const counts = data?.counts ?? { pending: 0, published: 0, takedown: 0 }

  const [filter, setFilter] = useState<FilterKey>('pending')
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<Set<string>>(new Set())

  const FILTERS: { key: FilterKey; label: string; count?: number }[] = [
    { key: 'pending', label: 'Pending review', count: counts.pending },
    { key: 'published', label: 'Published', count: counts.published },
    { key: 'takedown', label: 'Takedown', count: counts.takedown },
    { key: 'all', label: 'All' },
  ]

  const q = query.trim().toLowerCase()
  const rows = useMemo(
    () => items.filter((c) => inFilter(c, filter) && (!q || `${c.title} ${c.artist}`.toLowerCase().includes(q))),
    [items, filter, q],
  )
  const paged = usePaged(rows)

  const invalidate = () => queryClient.invalidateQueries({ queryKey: catalogQuery().queryKey })

  const handleApprove = async (c: CatalogItem) => {
    try { await apiApproveCatalog(c.id); await invalidate(); toast(`“${c.title}” approved`, 'success') }
    catch { toast('Could not approve release', 'error') }
  }
  const handleFlag = async (c: CatalogItem) => {
    try { await apiFlagCatalog(c.id); await invalidate(); toast(`“${c.title}” flagged`, 'info') }
    catch { toast('Could not flag release', 'error') }
  }
  const handleTakedown = async (c: CatalogItem) => {
    try { await apiTakedownCatalog(c.id, 'Taken down from catalog list'); await invalidate(); toast(`“${c.title}” taken down`, 'success') }
    catch { toast('Could not take down release', 'error') }
  }

  const allShownSelected = rows.length > 0 && rows.every((c) => selected.has(c.id))
  const toggleAll = () => setSelected(allShownSelected ? new Set() : new Set(rows.map((c) => c.id)))
  const toggleOne = (id: string) => setSelected((s) => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n })

  const bulkApprove = async () => {
    const ids = [...selected]
    try {
      await Promise.all(ids.map((id) => apiApproveCatalog(id)))
      await invalidate()
      toast(`${ids.length} release${ids.length > 1 ? 's' : ''} approved`, 'success')
      setSelected(new Set())
    } catch { toast('Could not approve the selected releases', 'error') }
  }
```

(`CatalogStatus` is still imported — it types `StatusPill`. The `setStatus`/`setItems` local mutators are gone.)

- [ ] **Step 3: Wire the row handlers + the error state**

The `<CatalogRow>` action props (lines 122-125) become:

```tsx
                  onApprove={() => handleApprove(c)}
                  onView={() => navigate({ to: '/admin/catalog/$itemId', params: { itemId: c.id } })}
                  onFlag={() => handleFlag(c)}
                  onTakedown={() => handleTakedown(c)}
```

Replace the table-body ternary (lines 117-128) so an error renders `AdminLoadError`:

```tsx
            {isError ? (
              <AdminLoadError label="Couldn't load catalog." onRetry={() => refetch()} />
            ) : rows.length === 0 ? (
              <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">Nothing here.</div>
            ) : (
              paged.pageItems.map((c) => (
                <CatalogRow key={c.id} item={c} selected={selected.has(c.id)} onSelect={() => toggleOne(c.id)}
                  onApprove={() => handleApprove(c)}
                  onView={() => navigate({ to: '/admin/catalog/$itemId', params: { itemId: c.id } })}
                  onFlag={() => handleFlag(c)}
                  onTakedown={() => handleTakedown(c)}
                />
              ))
            )}
```

(The header summary line using `CATALOG_SUMMARY` is unchanged — Category B.)

- [ ] **Step 4: Typecheck + full unit suite**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; vitest green.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/routes/admin.catalog.tsx
git commit -m "feat(admin): wire catalog list to live query (approve/flag/takedown + error state)"
```

---

### Task 4: Wire the Catalog detail

**Files:**
- Modify: `Frontend/src/routes/admin.catalog.$itemId.tsx`

**Interfaces:**
- Consumes: `catalogItemQuery`, `catalogQuery`, `apiApproveCatalog`, `apiFlagCatalog`, `apiTakedownCatalog` from `../lib/api/queries/admin-catalog`; `AdminLoadError` from `../components/admin/load-error`; `useQuery`, `useQueryClient` from `@tanstack/react-query`.

- [ ] **Step 1: Replace imports + delete the client-side fabrication**

Replace the mock import line

```ts
import { getCatalog, type CatalogItem, type CatalogStatus } from '../lib/admin-data'
```

with (`CatalogStatus` stays — types `StatusPill`; `getCatalog`/`CatalogItem` gone):

```ts
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { type CatalogStatus } from '../lib/admin-data'
import { catalogItemQuery, catalogQuery, apiApproveCatalog, apiFlagCatalog, apiTakedownCatalog } from '../lib/api/queries/admin-catalog'
import { AdminLoadError } from '../components/admin/load-error'
```

Delete the module-level `dur` helper (line 20) and the `interface Log` (line 22) — the mapper now supplies durations and the log shape.

- [ ] **Step 2: Replace the component head (state, guards, fabricated data) — lines 24-46**

```tsx
function AdminCatalogDetail() {
  const { itemId } = Route.useParams()
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const { data, isLoading, isError, refetch } = useQuery(catalogItemQuery(itemId))
  const [takedownOpen, setTakedownOpen] = useState(false)

  if (isError) {
    return (
      <div className="py-24">
        <AdminLoadError label="Couldn't load this release." onRetry={() => refetch()} />
      </div>
    )
  }

  const item = data
  if (!item) {
    return isLoading ? (
      <div className="py-24 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
    ) : (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-24">
        <p className="text-sm text-gray-500 dark:text-gray-300">Release not found.</p>
        <Link to="/admin/catalog" className="h-10 px-5 rounded-full bg-beatz-green text-black font-bold text-sm flex items-center">Back to catalog</Link>
      </div>
    )
  }

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: catalogItemQuery(itemId).queryKey })
    await queryClient.invalidateQueries({ queryKey: catalogQuery().queryKey })
  }
  const runAction = async (fn: () => Promise<void>, okMsg: string, errMsg: string) => {
    try { await fn(); await invalidate(); toast(okMsg, 'success') }
    catch { toast(errMsg, 'error') }
  }
  const approve = () => runAction(() => apiApproveCatalog(item.id), 'Approved & published', 'Could not approve release')
  const flag = () => runAction(() => apiFlagCatalog(item.id), 'Flagged for review', 'Could not flag release')
  const takedown = (reason: string) => runAction(() => apiTakedownCatalog(item.id, reason), `Taken down · ${reason}`, 'Could not take down release')

  const reviewable = item.status === 'pending' || item.status === 'flagged'
```

Note: `item` is now the mapped `CatalogDetail` — `item.tracks` is the **array** `CatalogDetailTrack[]`, `item.splits` the mapped splits, `item.log` the mapped log. The old `const tracks = …`, `const isrc = …`, `const splits = [...]` fabrication and the `addLog`/`setStatus`/`setItem`/`log` state are all deleted.

- [ ] **Step 3: Update the header, metadata, tracklist, splits, and log render sites**

Header subtitle (line 62) — the track count now comes from `item.tracks.length`:

```tsx
              <span className="text-sm text-gray-500 dark:text-gray-300">{item.artist} · {item.type} · {item.tracks.length} track{item.tracks.length === 1 ? '' : 's'}{item.note ? ` · ${item.note}` : ''}</span>
```

Header action buttons (lines 66-68):

```tsx
            {reviewable && <button onClick={approve} className="h-10 px-4 rounded-full bg-beatz-green text-black text-sm font-bold hover:scale-105 transition-transform"><span className="flex items-center gap-2"><Check size={15} /> Approve</span></button>}
            {item.status !== 'flagged' && <button onClick={flag} className="h-10 px-4 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white text-sm font-bold flex items-center gap-2 hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"><Flag size={15} /> Flag</button>}
            <button onClick={() => setTakedownOpen(true)} className="h-10 px-4 rounded-full bg-beatz-red/10 text-beatz-red text-sm font-bold flex items-center gap-2 hover:bg-beatz-red/20 transition-colors"><ShieldX size={15} /> Take down</button>
```

Tracklist (lines 78-86) — iterate `item.tracks` (`position`/`title`/`isrc`/`duration`); the Preview button stays a toast (Category B):

```tsx
            {item.tracks.map((t) => (
              <div key={t.position} className="flex items-center gap-3 py-2.5 border-b border-dashed border-gray-200 dark:border-white/5 last:border-0 group">
                <span className="w-5 text-sm font-mono text-gray-400 dark:text-gray-500 shrink-0">{t.position}</span>
                <button onClick={() => toast(`Previewing “${t.title}”`, 'info')} className="w-7 h-7 rounded-full bg-beatz-green/10 text-beatz-green flex items-center justify-center shrink-0 opacity-0 group-hover:opacity-100 transition-opacity"><Play size={12} fill="currentColor" /></button>
                <span className="flex-1 text-sm font-bold text-beatz-dark-bg dark:text-white truncate">{t.title}</span>
                <span className="text-xs font-mono text-gray-400 dark:text-gray-500 shrink-0">{t.isrc}</span>
                <span className="w-12 text-right text-sm font-mono text-gray-500 dark:text-gray-300 shrink-0">{t.duration}</span>
              </div>
            ))}
```

Metadata (lines 94-97) — **UPC becomes real**; genre/label stay Category B; track count from the array:

```tsx
            <Meta label="UPC" value={item.upc} />
            <Meta label="Primary genre" value="Hiplife / Drill" />
            <Meta label="Label" value={item.artist === 'Various' ? 'Beatzclik Compilations' : 'Independent'} />
            <Meta label="Tracks" value={`${item.tracks.length}`} last />
```

Splits (line 102) iterate `item.splits` (`name`/`role`/`pct`) — unchanged field names, so only the source array changes:

```tsx
            {item.splits.map((s) => (
```

Action history (line 115) iterates `item.log` (`id`/`action`/`time`):

```tsx
            {item.log.map((l) => (
```

TakedownModal `onConfirm` (line 127) calls the wired `takedown`:

```tsx
      <TakedownModal isOpen={takedownOpen} title={item.title} onClose={() => setTakedownOpen(false)}
        onConfirm={(reason) => { setTakedownOpen(false); takedown(reason) }} />
```

- [ ] **Step 4: Typecheck + full unit suite**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; vitest green.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/routes/admin.catalog.$itemId.tsx
git commit -m "feat(admin): wire catalog detail to live query (real tracklist/splits/log + actions)"
```

---

### Task 5: Moderation mappers + wire types

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Consumes: `ModerationItem`, `ModReason`, `ModSeverity`, `ModStatus` from `../../admin-data`; `relativeTime` from `../../format`.
- Produces (used by Task 6/7): types `ModerationCaseWire`, `ModerationSummaryWire`, `ModerationQueueWire`, `ModerationSummary`, `ModerationQueueData`; functions `toModerationCase`, `toModerationQueue`.

- [ ] **Step 1: Write the failing tests**

Add to the top import block of `mappers.test.ts`:

```ts
import { toModerationCase, toModerationQueue, type ModerationQueueWire } from './mappers'
```

Append:

```ts
describe('admin moderation mappers', () => {
  const caseWire = { id: 'm1', item: 'Track · X', reporter: '@dj', reason: 'Copyright', time: '2026-07-24T06:00:00Z', severity: 'high', status: 'open', escalated: false }

  it('toModerationCase maps age via relativeTime and narrows unions', () => {
    const c = toModerationCase(caseWire, Date.parse('2026-07-24T12:00:00Z'))
    expect(c).toEqual({ id: 'm1', item: 'Track · X', reporter: '@dj', reason: 'Copyright', age: '6h', severity: 'high', status: 'open' })
  })

  it('toModerationQueue maps items + summary', () => {
    const wire: ModerationQueueWire = { items: [caseWire], page: 1, size: 100, total: 1, summary: { openCount: 5, slaHours: 6, escalatedCount: 3 } }
    const q = toModerationQueue(wire, Date.parse('2026-07-24T12:00:00Z'))
    expect(q.items).toHaveLength(1)
    expect(q.summary).toEqual({ open: 5, sla: 6, escalated: 3 })
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/mappers.test.ts`
Expected: FAIL — `toModerationCase`/`toModerationQueue` not exported.

- [ ] **Step 3: Add imports to the top block of `mappers.ts`**

```ts
import type { ModerationItem, ModReason, ModSeverity, ModStatus } from '../../admin-data'
import { relativeTime } from '../../format'
```

(Extend the existing `../../format` import from Task 1 to `import { formatDuration, relativeTime, relativeTimeAgo } from '../../format'` rather than adding a second line.)

- [ ] **Step 4: Append at the END of `mappers.ts`**

```ts
// ── Admin moderation (AdminModerationResource) ────────────────────────────────
export interface ModerationCaseWire {
  id: string
  item: string
  reporter: string
  reason: string
  time: string
  severity: string
  status: string
  escalated: boolean
}
export interface ModerationSummaryWire { openCount: number; slaHours: number; escalatedCount: number }
export interface ModerationQueueWire {
  items: ModerationCaseWire[]
  page: number
  size: number
  total: number
  summary: ModerationSummaryWire
}

export interface ModerationSummary { open: number; sla: number; escalated: number }
export interface ModerationQueueData { items: ModerationItem[]; summary: ModerationSummary }

export function toModerationCase(w: ModerationCaseWire, now?: number): ModerationItem {
  return {
    id: w.id,
    item: w.item,
    reporter: w.reporter,
    reason: w.reason as ModReason,
    age: relativeTime(w.time, now),
    severity: w.severity as ModSeverity,
    status: w.status as ModStatus,
  }
}

export function toModerationQueue(w: ModerationQueueWire, now?: number): ModerationQueueData {
  return {
    items: w.items.map((c) => toModerationCase(c, now)),
    summary: { open: w.summary.openCount, sla: w.summary.slaHours, escalated: w.summary.escalatedCount },
  }
}
```

Note: `relativeTime(iso, now?)` defaults `now` to `Date.now()`; the `now` param exists only so the tests are deterministic. `escalated` is intentionally not carried into `ModerationItem` (the mock has no such field and nothing renders it).

- [ ] **Step 5: Run the tests to verify they pass**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/mappers.test.ts`
Expected: PASS.

- [ ] **Step 6: Typecheck**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build`
Expected: 0 errors.

- [ ] **Step 7: Commit**

```bash
git add Frontend/src/lib/api/mappers.ts Frontend/src/lib/api/mappers.test.ts
git commit -m "feat(admin): moderation wire mappers (queue + summary)"
```

---

### Task 6: Moderation query + mutation layer

**Files:**
- Create: `Frontend/src/lib/api/queries/admin-moderation.ts`
- Test: `Frontend/src/lib/api/queries/admin-moderation.test.ts`

**Interfaces:**
- Consumes: `toModerationQueue`, `ModerationQueueWire` from `../mappers`; `apiFetch` from `../client`.
- Produces (used by Task 7): `moderationQuery()` (key `['admin','moderation','queue']`), `apiReviewCase(id)`, `apiApproveCase(id)`, `apiRemoveCase(id, reason?)`, `apiEscalateCase(id)`, `apiDismissCase(id)` — all `Promise<void>`.

- [ ] **Step 1: Write the failing test**

Create `Frontend/src/lib/api/queries/admin-moderation.test.ts`:

```ts
import { afterEach, describe, expect, it, vi } from 'vitest'
import { moderationQuery, apiReviewCase, apiApproveCase, apiRemoveCase, apiEscalateCase, apiDismissCase } from './admin-moderation'

const queueWire = {
  items: [{ id: 'm1', item: 'X', reporter: '@a', reason: 'Spam', time: '2026-07-24T06:00:00Z', severity: 'low', status: 'open', escalated: false }],
  page: 1, size: 100, total: 1, summary: { openCount: 1, slaHours: 6, escalatedCount: 0 },
}
function mockFetch(status: number, json: unknown) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300, status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => json, text: async () => JSON.stringify(json),
  } as Response)
}
afterEach(() => vi.restoreAllMocks())

describe('admin-moderation queries', () => {
  it('moderationQuery hits /v1/admin/moderation and maps items + summary', async () => {
    const f = mockFetch(200, queueWire); vi.stubGlobal('fetch', f)
    const result = await moderationQuery().queryFn!({} as never)
    expect(f).toHaveBeenCalledWith('/v1/admin/moderation', expect.objectContaining({ method: 'GET' }))
    expect(result.items[0].item).toBe('X')
    expect(result.summary).toEqual({ open: 1, sla: 6, escalated: 0 })
    expect(moderationQuery().queryKey).toEqual(['admin', 'moderation', 'queue'])
  })

  it('apiReviewCase POSTs to /review', async () => {
    const f = mockFetch(200, queueWire.items[0]); vi.stubGlobal('fetch', f)
    await apiReviewCase('m1')
    expect(f).toHaveBeenCalledWith('/v1/admin/moderation/m1/review', expect.objectContaining({ method: 'POST' }))
  })

  it('apiApproveCase POSTs to /approve', async () => {
    const f = mockFetch(200, queueWire.items[0]); vi.stubGlobal('fetch', f)
    await apiApproveCase('m1')
    expect(f).toHaveBeenCalledWith('/v1/admin/moderation/m1/approve', expect.objectContaining({ method: 'POST' }))
  })

  it('apiRemoveCase POSTs to /remove (no body when no reason)', async () => {
    const f = mockFetch(200, queueWire.items[0]); vi.stubGlobal('fetch', f)
    await apiRemoveCase('m1')
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/moderation/m1/remove')
    expect(opts.method).toBe('POST')
  })

  it('apiEscalateCase POSTs to /escalate', async () => {
    const f = mockFetch(200, queueWire.items[0]); vi.stubGlobal('fetch', f)
    await apiEscalateCase('m1')
    expect(f).toHaveBeenCalledWith('/v1/admin/moderation/m1/escalate', expect.objectContaining({ method: 'POST' }))
  })

  it('apiDismissCase POSTs to /dismiss', async () => {
    const f = mockFetch(200, queueWire.items[0]); vi.stubGlobal('fetch', f)
    await apiDismissCase('m1')
    expect(f).toHaveBeenCalledWith('/v1/admin/moderation/m1/dismiss', expect.objectContaining({ method: 'POST' }))
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/queries/admin-moderation.test.ts`
Expected: FAIL — module does not exist.

- [ ] **Step 3: Write the query module**

Create `Frontend/src/lib/api/queries/admin-moderation.ts`:

```ts
import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { toModerationQueue, type ModerationQueueWire } from '../mappers'

/** `GET /v1/admin/moderation` — the moderation queue plus header summary. */
export function moderationQuery() {
  return queryOptions({
    queryKey: ['admin', 'moderation', 'queue'],
    queryFn: async () => toModerationQueue(await apiFetch<ModerationQueueWire>('/admin/moderation')),
  })
}

/** `POST /v1/admin/moderation/:id/review` — move a case into review. */
export function apiReviewCase(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/moderation/${id}/review`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/moderation/:id/approve` — approve & keep the content. */
export function apiApproveCase(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/moderation/${id}/approve`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/moderation/:id/remove` — remove content; reason optional. */
export function apiRemoveCase(id: string, reason?: string): Promise<void> {
  return apiFetch<unknown>(`/admin/moderation/${id}/remove`, { method: 'POST', body: reason === undefined ? undefined : { reason } }).then(() => undefined)
}

/** `POST /v1/admin/moderation/:id/escalate` — escalate to senior review. */
export function apiEscalateCase(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/moderation/${id}/escalate`, { method: 'POST' }).then(() => undefined)
}

/** `POST /v1/admin/moderation/:id/dismiss` — dismiss the report. */
export function apiDismissCase(id: string): Promise<void> {
  return apiFetch<unknown>(`/admin/moderation/${id}/dismiss`, { method: 'POST' }).then(() => undefined)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/queries/admin-moderation.test.ts`
Expected: PASS (6 tests).

- [ ] **Step 5: Typecheck**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build`
Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/lib/api/queries/admin-moderation.ts Frontend/src/lib/api/queries/admin-moderation.test.ts
git commit -m "feat(admin): moderation query + mutation layer"
```

---

### Task 7: Wire the Moderation queue

**Files:**
- Modify: `Frontend/src/routes/admin.moderation.tsx`

**Interfaces:**
- Consumes: `moderationQuery`, `apiReviewCase`, `apiApproveCase`, `apiRemoveCase`, `apiEscalateCase`, `apiDismissCase` from `../lib/api/queries/admin-moderation`; `AdminLoadError` from `../components/admin/load-error`; `useQuery`, `useQueryClient` from `@tanstack/react-query`.

- [ ] **Step 1: Replace imports + swap state for the query**

Replace the mock import line

```ts
import { getModerationQueue, MOD_TYPES, MOD_SLA_HOURS, MOD_ESCALATED, type ModerationItem, type ModReason, type ModSeverity, type ModStatus } from '../lib/admin-data'
```

with (`MOD_TYPES` stays — it feeds the type chips; `getModerationQueue`/`MOD_SLA_HOURS`/`MOD_ESCALATED` gone — the summary now comes from the query; the four types stay):

```ts
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { MOD_TYPES, type ModerationItem, type ModReason, type ModSeverity, type ModStatus } from '../lib/admin-data'
import { moderationQuery, apiReviewCase, apiApproveCase, apiRemoveCase, apiEscalateCase, apiDismissCase } from '../lib/api/queries/admin-moderation'
import { AdminLoadError } from '../components/admin/load-error'
```

Replace the component's state/derivation block (lines 23-35) with:

```ts
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const { data, isError, refetch } = useQuery(moderationQuery())
  const items = data?.items ?? []
  const summary = data?.summary ?? { open: 0, sla: 0, escalated: 0 }

  const [status, setStatus] = useState<ModStatus | 'all'>('open')
  const [type, setType] = useState<ModReason | 'all'>('all')

  const rows = useMemo(
    () => items.filter((i) => (status === 'all' || i.status === status) && (type === 'all' || i.reason === type)),
    [items, status, type],
  )
  const paged = usePaged(rows)

  const invalidate = () => queryClient.invalidateQueries({ queryKey: moderationQuery().queryKey })
  const runAction = async (fn: () => Promise<void>, okMsg: string, errMsg: string, tone: 'success' | 'info' = 'success') => {
    try { await fn(); await invalidate(); toast(okMsg, tone) }
    catch { toast(errMsg, 'error') }
  }
  const review = (it: ModerationItem) => runAction(() => apiReviewCase(it.id), `Reviewing “${it.item}”`, 'Could not start review', 'info')
  const approve = (it: ModerationItem) => runAction(() => apiApproveCase(it.id), 'Content approved & kept', 'Could not approve content')
  const remove = (it: ModerationItem) => runAction(() => apiRemoveCase(it.id), 'Content removed', 'Could not remove content')
  const escalate = (it: ModerationItem) => runAction(() => apiEscalateCase(it.id), 'Escalated to senior review', 'Could not escalate', 'info')
  const dismiss = (it: ModerationItem) => runAction(() => apiDismissCase(it.id), 'Report dismissed', 'Could not dismiss report')
```

(The `setItemStatus` local mutator and the client-side `openCount` are gone — the header now reads `summary`.)

- [ ] **Step 2: Update the header summary + row handlers + error state**

Header summary line (line 43):

```tsx
          <span className="text-sm text-gray-500 dark:text-gray-300">{summary.open} open · {summary.sla}h SLA · {summary.escalated} escalated</span>
```

The `<ModRow>` handlers (lines 80-84):

```tsx
                  onReview={() => review(it)}
                  onApprove={() => approve(it)}
                  onRemove={() => remove(it)}
                  onEscalate={() => escalate(it)}
                  onDismiss={() => dismiss(it)}
```

Replace the table-body ternary (lines 75-87) so an error renders `AdminLoadError`:

```tsx
            {isError ? (
              <AdminLoadError label="Couldn't load the moderation queue." onRetry={() => refetch()} />
            ) : rows.length === 0 ? (
              <div className="py-12 text-center text-sm text-gray-400 dark:text-gray-500">Nothing in this queue.</div>
            ) : (
              paged.pageItems.map((it) => (
                <ModRow key={it.id} item={it}
                  onReview={() => review(it)}
                  onApprove={() => approve(it)}
                  onRemove={() => remove(it)}
                  onEscalate={() => escalate(it)}
                  onDismiss={() => dismiss(it)}
                />
              ))
            )}
```

(`onEscalate` now persists via `apiEscalateCase` **and** keeps its existing `'Escalated to senior review'` info toast — the escalated flag is not rendered, so no visual change.)

- [ ] **Step 3: Typecheck + full unit suite**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; vitest green.

- [ ] **Step 4: Commit**

```bash
git add Frontend/src/routes/admin.moderation.tsx
git commit -m "feat(admin): wire moderation queue to live endpoints (review/approve/remove/escalate/dismiss)"
```

---

### Task 8: Live QA + PR (USER-run gate)

**Files:** none (verification only). The controller does NOT run `verify.sh` (IntelliJ JPS races); CI is authoritative.

- [ ] **Step 1: Final full unit + build gate**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; full vitest suite green.

- [ ] **Step 2: Live QA against the running stack** (backend on :18080, Vite proxy → :18080, signed in as the seeded `support`/admin account — note `support` can read but only `super-admin`/`moderator` may mutate catalog/moderation; use a `super-admin` or `moderator` account for the action checks)

  - Catalog list: filter-pill counts match backend; filters (pending includes flagged) + search work; Approve / menu Flag / menu Take down persist; bulk-approve persists.
  - Catalog detail: real tracklist (title/ISRC/duration) + splits + action log render; UPC is the real value; Approve/Flag persist; Take down with a reason persists and appears in the action log after refetch.
  - Moderation: header summary (open/SLA/escalated) is live; Review → in_review; Approve & keep / Remove / Dismiss → resolved; Escalate persists (toast shows) — all survive refetch.
  - Force a load error (stop the backend, refetch) → the distinct "Couldn't load …" + Retry affordance appears (not the empty state) on each screen.

- [ ] **Step 3: Open the PR**

```bash
git push -u origin feat/frontend-admin-catalog-moderation
gh pr create --base master --title "feat(admin): wire Catalog list+detail and Moderation queue to live endpoints" --body "<DoD checklist; no-visual-change note; Category-B list; note the list-takedown default reason>"
```

---

## Notes for the executor

- **Branch:** `feat/frontend-admin-catalog-moderation` (already created off `master` post-#165; spec `7aa5264`). BASE for the first review package is `7aa5264`.
- **Do NOT** touch backend or stage backend secrets. Frontend-only branch.
- **`AdminLoadError`** and the `format.ts` helpers (`relativeTime`/`relativeTimeAgo`/`formatDuration`) already exist on `master` (from #165) — import, do not recreate.
- **Category B (leave as-is):** catalog header summary, track Preview, detail genre/label metadata, Reinstate (no button), track price, moderation Remove-reason. Moderation Escalate now persists but keeps its toast and has no visual indicator.
- The in-flight-guard follow-up from the Users slice is separate; match the currently-shipped async-handler pattern here (no disabled guard) unless that shared utility has landed on `master` by implementation time.
