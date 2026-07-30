# Frontend Admin Editorial Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire `admin.editorial` from the `admin-data` mock to the live `AdminEditorialResource` endpoints with no visual change, and make featured-slot reordering and removal genuinely persist via `PUT /featured`.

**Architecture:** The established admin idiom — one `queries/admin-editorial.ts` (three `queryOptions` reads + one save mutation), wire types + `toX` mappers in the shared `lib/api/mappers.ts`, and the route swapped to `useQuery` with `AdminLoadError`. This is the last admin slice.

**Tech Stack:** React 18, TanStack Query v5, TanStack Router, Vitest, TypeScript (`tsc -b`), Tailwind.

## Global Constraints

- **Branch is independent** — off `master`, needs nothing from the unmerged #167/#168.
- **Node 22 via nvm** for all `npm`/`npx`: prefix with `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null &&`.
- **Real typecheck gate is `npm run build`** (`tsc -b`), not vitest. Every task runs both.
- **NEVER stage** `backend/src/main/resources/application.properties` or `backend/docker-compose.yml`. Frontend-only — do not touch backend.
- **No visual change.** JSX, `className`, and copy stay byte-for-byte. The one behavioral correction is that reorder/remove now persist (see below).
- **`PUT /featured` is a FULL ORDERED REPLACE**, not a patch. Every save sends the complete list in display order. `title` is `@NotBlank` and a duplicate `id` is a 422.
- **Writes require `super-admin` or `editor`**; reads also allow `support`. A `support` admin can view this page but every write 403s.
- **Query keys:** `['admin','editorial','featured']`, `['admin','editorial','push']`, `['admin','editorial','playlists']`.
- **New imports in the TOP import block; `import type`** for type-only imports.
- **A test named for a behavior must assert that behavior** — the recurring defect in this project.
- **Category B (do NOT wire):** "New playlist" and "Schedule push" keep their existing informational toasts (their endpoints exist but neither has a form, and building one is a product-design task); "Replace" on a slot and opening a playlist have **no endpoint at all**; `PushItemDto.scheduledAt` is served but not rendered; the push row's hardcoded "scheduled" pill has no status field behind it.

### The one behavioral correction

`remove` currently toasts **"Removed from featured"** for a change that lives only in local state and reappears on reload. After wiring it is durable. Reorder likewise persists (it toasts nothing today and still won't). This is a correction to a false success claim, not a visual change.

---

### Task 1: Editorial mappers

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Consumes: `FeaturedSlot`, `PushItem`, `CuratedPlaylist` from `../admin-data`.
- Produces (used by Task 2): types `FeaturedSlotWire`, `PushItemWire`, `CuratedPlaylistWire`, `FeaturedSlotRequestBody`; functions `toFeaturedSlot`, `toPushItem`, `toCuratedPlaylist`, `toFeaturedSlotRequest`.

- [ ] **Step 1: Write the failing tests**

Extend the top import from `./mappers` in `mappers.test.ts` with `toFeaturedSlot, toPushItem, toCuratedPlaylist, toFeaturedSlotRequest`, then append:

```ts
describe('editorial mappers', () => {
  it('toFeaturedSlot maps 1:1 including the sponsored flag', () => {
    expect(toFeaturedSlot({ id: 'f1', title: 'Made in Ghana', note: 'Editorial pick', sponsored: false }))
      .toEqual({ id: 'f1', title: 'Made in Ghana', note: 'Editorial pick', sponsored: false })
    expect(toFeaturedSlot({ id: 'f2', title: 'MTN Presents', note: 'Paid placement', sponsored: true }).sponsored).toBe(true)
  })

  it('toPushItem renames the wire timeLabel to the UI time field', () => {
    expect(toPushItem({ id: 'p1', day: 'Fri', timeLabel: '6PM', title: 'New drops', audience: 'All fans', scheduledAt: null }))
      .toEqual({ id: 'p1', day: 'Fri', time: '6PM', title: 'New drops', audience: 'All fans' })
  })

  it('toCuratedPlaylist maps 1:1', () => {
    expect(toCuratedPlaylist({ id: 'pl1', name: 'Hiplife Throwback' })).toEqual({ id: 'pl1', name: 'Hiplife Throwback' })
  })

  it('toFeaturedSlotRequest shapes a slot for the PUT body, defaulting sponsored to false', () => {
    expect(toFeaturedSlotRequest({ id: 'f1', title: 'A', note: 'n', sponsored: true }))
      .toEqual({ id: 'f1', title: 'A', note: 'n', sponsored: true })
    // the mock type makes `sponsored` optional; the wire needs a real boolean
    expect(toFeaturedSlotRequest({ id: 'f2', title: 'B', note: '' }).sponsored).toBe(false)
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/mappers.test.ts`
Expected: FAIL — the four functions are not exported.

- [ ] **Step 3: Extend the existing top import line of `mappers.ts`**

Add `FeaturedSlot`, `PushItem`, `CuratedPlaylist` to the existing `../admin-data` type import (do NOT add a second import line from that module).

- [ ] **Step 4: Append at the END of `mappers.ts`**

```ts
// ── Admin editorial (AdminEditorialResource) ──────────────────────────────────
export interface FeaturedSlotWire { id: string; title: string; note: string; sponsored: boolean }
export interface PushItemWire {
  id: string
  day: string
  timeLabel: string
  title: string
  audience: string
  scheduledAt: string | null
}
export interface CuratedPlaylistWire { id: string; name: string }

/** The `PUT /featured` body element — `title` is @NotBlank server-side and ids must be unique. */
export interface FeaturedSlotRequestBody { id: string; title: string; note: string; sponsored: boolean }

export function toFeaturedSlot(w: FeaturedSlotWire): FeaturedSlot {
  return { id: w.id, title: w.title, note: w.note, sponsored: w.sponsored }
}

/**
 * The wire calls the cosmetic label `timeLabel`; the UI's `PushItem` calls it `time`.
 * `scheduledAt` is served but deliberately not surfaced — the row renders the label only.
 */
export function toPushItem(w: PushItemWire): PushItem {
  return { id: w.id, day: w.day, time: w.timeLabel, title: w.title, audience: w.audience }
}

export function toCuratedPlaylist(w: CuratedPlaylistWire): CuratedPlaylist {
  return { id: w.id, name: w.name }
}

/** Shapes one slot for the full-replace PUT. `sponsored` is optional on the UI type but required on the wire. */
export function toFeaturedSlotRequest(s: FeaturedSlot): FeaturedSlotRequestBody {
  return { id: s.id, title: s.title, note: s.note, sponsored: s.sponsored ?? false }
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
git commit -m "feat(admin): editorial wire mappers (featured, push, playlists)"
```

---

### Task 2: Editorial query + save layer

**Files:**
- Create: `Frontend/src/lib/api/queries/admin-editorial.ts`
- Test: `Frontend/src/lib/api/queries/admin-editorial.test.ts`

**Interfaces:**
- Consumes: `toFeaturedSlot`, `toPushItem`, `toCuratedPlaylist`, `toFeaturedSlotRequest` + wire types from `../mappers`; `apiFetch` from `../client`; `FeaturedSlot` from `../../admin-data`.
- Produces (used by Task 3): `featuredQuery()`, `pushScheduleQuery()`, `curatedPlaylistsQuery()`, `apiSaveFeatured(slots)`.

- [ ] **Step 1: Write the failing test**

Create `Frontend/src/lib/api/queries/admin-editorial.test.ts`:

```ts
import { afterEach, describe, expect, it, vi } from 'vitest'
import { featuredQuery, pushScheduleQuery, curatedPlaylistsQuery, apiSaveFeatured } from './admin-editorial'

const featuredWire = [{ id: 'f1', title: 'A', note: 'n1', sponsored: false }]
const pushWire = [{ id: 'p1', day: 'Fri', timeLabel: '6PM', title: 'T', audience: 'All', scheduledAt: null }]
const playlistWire = [{ id: 'pl1', name: 'Hiplife' }]

function mockFetch(status: number, json: unknown) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300, status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => json, text: async () => JSON.stringify(json),
  } as Response)
}
afterEach(() => vi.restoreAllMocks())

describe('admin-editorial reads', () => {
  it('featuredQuery hits /v1/admin/editorial/featured', async () => {
    const f = mockFetch(200, featuredWire); vi.stubGlobal('fetch', f)
    const r = await featuredQuery().queryFn!({} as never)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/editorial/featured')
    expect(r).toEqual([{ id: 'f1', title: 'A', note: 'n1', sponsored: false }])
    expect(featuredQuery().queryKey).toEqual(['admin', 'editorial', 'featured'])
  })

  it('pushScheduleQuery hits /push and maps timeLabel to time', async () => {
    const f = mockFetch(200, pushWire); vi.stubGlobal('fetch', f)
    const r = await pushScheduleQuery().queryFn!({} as never)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/editorial/push')
    expect(r[0].time).toBe('6PM')
    expect(pushScheduleQuery().queryKey).toEqual(['admin', 'editorial', 'push'])
  })

  it('curatedPlaylistsQuery hits /playlists', async () => {
    const f = mockFetch(200, playlistWire); vi.stubGlobal('fetch', f)
    const r = await curatedPlaylistsQuery().queryFn!({} as never)
    expect(f.mock.calls[0][0]).toBe('/v1/admin/editorial/playlists')
    expect(r).toEqual([{ id: 'pl1', name: 'Hiplife' }])
    expect(curatedPlaylistsQuery().queryKey).toEqual(['admin', 'editorial', 'playlists'])
  })
})

describe('apiSaveFeatured', () => {
  it('PUTs the FULL list in the given order (the endpoint is a whole-list replace)', async () => {
    const f = mockFetch(200, featuredWire); vi.stubGlobal('fetch', f)
    await apiSaveFeatured([
      { id: 'b', title: 'Second', note: 'n2', sponsored: true },
      { id: 'a', title: 'First', note: 'n1' },
    ])
    const [url, opts] = f.mock.calls[0]
    expect(url).toBe('/v1/admin/editorial/featured')
    expect(opts.method).toBe('PUT')
    expect(JSON.parse(opts.body)).toEqual([
      { id: 'b', title: 'Second', note: 'n2', sponsored: true },
      { id: 'a', title: 'First', note: 'n1', sponsored: false },
    ])
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/queries/admin-editorial.test.ts`
Expected: FAIL — `./admin-editorial` does not exist.

- [ ] **Step 3: Write the query module**

Create `Frontend/src/lib/api/queries/admin-editorial.ts`:

```ts
import { queryOptions } from '@tanstack/react-query'
import type { FeaturedSlot } from '../../admin-data'
import { apiFetch } from '../client'
import {
  toFeaturedSlot, toPushItem, toCuratedPlaylist, toFeaturedSlotRequest,
  type FeaturedSlotWire, type PushItemWire, type CuratedPlaylistWire,
} from '../mappers'

/** `GET /v1/admin/editorial/featured` — the ordered home-featured slots. */
export function featuredQuery() {
  return queryOptions({
    queryKey: ['admin', 'editorial', 'featured'],
    queryFn: async () => (await apiFetch<FeaturedSlotWire[]>('/admin/editorial/featured')).map(toFeaturedSlot),
  })
}

/** `GET /v1/admin/editorial/push` — this week's scheduled push notifications. */
export function pushScheduleQuery() {
  return queryOptions({
    queryKey: ['admin', 'editorial', 'push'],
    queryFn: async () => (await apiFetch<PushItemWire[]>('/admin/editorial/push')).map(toPushItem),
  })
}

/** `GET /v1/admin/editorial/playlists` — the curated playlist tiles. */
export function curatedPlaylistsQuery() {
  return queryOptions({
    queryKey: ['admin', 'editorial', 'playlists'],
    queryFn: async () => (await apiFetch<CuratedPlaylistWire[]>('/admin/editorial/playlists')).map(toCuratedPlaylist),
  })
}

/**
 * `PUT /v1/admin/editorial/featured` — a **full ordered replace**, not a patch: whatever list is
 * sent becomes the featured set, so callers must send the complete list in display order.
 * Requires `super-admin` or `editor` (a `support` admin can read this page but not save).
 * 422 on a blank title or a duplicate id.
 */
export function apiSaveFeatured(slots: FeaturedSlot[]): Promise<FeaturedSlot[]> {
  return apiFetch<FeaturedSlotWire[]>('/admin/editorial/featured', {
    method: 'PUT',
    body: slots.map(toFeaturedSlotRequest),
  }).then((saved) => saved.map(toFeaturedSlot))
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npx vitest run src/lib/api/queries/admin-editorial.test.ts`
Expected: PASS (4 tests).

- [ ] **Step 5: Typecheck**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build`
Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/lib/api/queries/admin-editorial.ts Frontend/src/lib/api/queries/admin-editorial.test.ts
git commit -m "feat(admin): editorial query layer + featured full-replace save"
```

---

### Task 3: Wire the editorial route

**Files:**
- Modify: `Frontend/src/routes/admin.editorial.tsx`

**Interfaces:**
- Consumes: `featuredQuery`, `pushScheduleQuery`, `curatedPlaylistsQuery`, `apiSaveFeatured` from `../lib/api/queries/admin-editorial`; `AdminLoadError` from `../components/admin/load-error`; `useQuery`, `useQueryClient` from `@tanstack/react-query`.

- [ ] **Step 1: Replace the imports and the mock seed**

Replace the mock import line

```ts
import { getEditorial, type FeaturedSlot, type PushItem, type CuratedPlaylist } from '../lib/admin-data'
```

with (the three types stay — they type the sub-components):

```ts
import { useQuery, useQueryClient } from '@tanstack/react-query'
import type { FeaturedSlot, PushItem, CuratedPlaylist } from '../lib/admin-data'
import { featuredQuery, pushScheduleQuery, curatedPlaylistsQuery, apiSaveFeatured } from '../lib/api/queries/admin-editorial'
import { AdminLoadError } from '../components/admin/load-error'
```

and change the react import to `import { useRef, useState } from 'react'` (`useMemo` is no longer used; `useRef` backs the in-flight guard).

- [ ] **Step 2: Replace the component's data block and the two mutators**

Replace

```ts
  const { toast } = useToast()
  const base = useMemo(() => getEditorial(), [])
  const [featured, setFeatured] = useState<FeaturedSlot[]>(base.featured)

  const move = (id: string, dir: -1 | 1) => setFeatured((list) => { … })
  const remove = (id: string) => { setFeatured((list) => list.filter((s) => s.id !== id)); toast('Removed from featured', 'success') }
```

with:

```ts
  const { toast } = useToast()
  const queryClient = useQueryClient()
  const featuredQ = useQuery(featuredQuery())
  const pushQ = useQuery(pushScheduleQuery())
  const playlistsQ = useQuery(curatedPlaylistsQuery())
  const [saving, setSaving] = useState(false)
  const inFlight = useRef(false)

  const featured = featuredQ.data ?? []

  /**
   * `PUT /featured` replaces the whole list, so every edit sends the complete array in display
   * order. On failure we invalidate rather than keeping the local edit, so the UI snaps back to
   * the server's truth instead of showing a change that never landed.
   */
  const saveOrder = async (next: FeaturedSlot[], okMsg?: string) => {
    // A ref, not the `saving` state: a state read inside this closure is stale for a second call
    // in the same render, and on a WHOLE-LIST replace two racing PUTs can land out of order and
    // resurrect a slot the user just removed.
    if (inFlight.current) return
    inFlight.current = true
    setSaving(true)
    try {
      await apiSaveFeatured(next)
      if (okMsg) toast(okMsg, 'success')
    } catch {
      toast('Could not save the featured order', 'error')
    } finally {
      await queryClient.invalidateQueries({ queryKey: ['admin', 'editorial', 'featured'] })
      inFlight.current = false
      setSaving(false)
    }
  }

  const move = (id: string, dir: -1 | 1) => {
    const i = featured.findIndex((s) => s.id === id)
    const j = i + dir
    if (i === -1 || j < 0 || j >= featured.length) return
    const next = [...featured]
    ;[next[i], next[j]] = [next[j], next[i]]
    void saveOrder(next)
  }

  const remove = (id: string) => void saveOrder(featured.filter((s) => s.id !== id), 'Removed from featured')
```

Note the `move`/`remove` signatures are unchanged, so `FeaturedRow`'s props need no edit. The "Removed from featured" toast now fires only after the save actually succeeds.

- [ ] **Step 3: Point the push and playlist sections at their queries and add the error/loading branches**

The featured card's body becomes:

```tsx
          <div className="flex flex-col">
            {featuredQ.isError ? (
              <AdminLoadError label="Couldn't load featured slots." onRetry={() => featuredQ.refetch()} />
            ) : featuredQ.isLoading ? (
              <div className="py-8 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
            ) : (
              <>
                {featured.map((s, i) => (
                  <FeaturedRow key={s.id} slot={s} index={i} isFirst={i === 0} isLast={i === featured.length - 1}
                    disabled={saving}
                    onMove={(d) => move(s.id, d)} onRemove={() => remove(s.id)} onReplace={() => toast(`Replace “${s.title}”`, 'info')} />
                ))}
                {featured.length === 0 && <div className="py-8 text-center text-sm text-gray-400 dark:text-gray-500">No featured slots.</div>}
              </>
            )}
          </div>
```

The push card's list becomes:

```tsx
          <div className="flex flex-col">
            {pushQ.isError ? (
              <AdminLoadError label="Couldn't load the push schedule." onRetry={() => pushQ.refetch()} />
            ) : pushQ.isLoading ? (
              <div className="py-8 text-center text-sm text-gray-400 dark:text-gray-500">Loading…</div>
            ) : (pushQ.data ?? []).length === 0 ? (
              <div className="py-8 text-center text-sm text-gray-400 dark:text-gray-500">Nothing scheduled.</div>
            ) : (
              (pushQ.data ?? []).map((p) => <PushRow key={p.id} push={p} />)
            )}
          </div>
```

The playlists grid becomes:

```tsx
        {playlistsQ.isError ? (
          <AdminLoadError label="Couldn't load curated playlists." onRetry={() => playlistsQ.refetch()} />
        ) : playlistsQ.isLoading ? (
          <div className="py-8 text-sm text-gray-400 dark:text-gray-500">Loading…</div>
        ) : (playlistsQ.data ?? []).length === 0 ? (
          <p className="py-8 text-sm text-gray-400 dark:text-gray-500">No curated playlists yet.</p>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
            {(playlistsQ.data ?? []).map((p) => <PlaylistCard key={p.id} playlist={p} onOpen={() => toast(`Open “${p.name}”`, 'info')} />)}
          </div>
        )}
```

The "New playlist" and "Schedule push" buttons keep their existing toasts unchanged (Category B).

- [ ] **Step 4: Disable the slot menu while a save is in flight**

`FeaturedRow` gains a `disabled` prop that it forwards to the three mutating menu items, so two rapid reorders can't race and clobber each other on a whole-list replace. Update its signature and the three `MenuItem`s:

```tsx
function FeaturedRow({ slot: s, index, isFirst, isLast, disabled, onMove, onRemove, onReplace }: {
  slot: FeaturedSlot; index: number; isFirst: boolean; isLast: boolean; disabled?: boolean
  onMove: (d: -1 | 1) => void; onRemove: () => void; onReplace: () => void
}) {
```

```tsx
              <MenuItem icon={ArrowUp} label="Move up" disabled={isFirst || disabled} onClick={() => { onMove(-1); setMenuOpen(false) }} />
              <MenuItem icon={ArrowDown} label="Move down" disabled={isLast || disabled} onClick={() => { onMove(1); setMenuOpen(false) }} />
              <MenuItem icon={Music2} label="Replace" onClick={() => { onReplace(); setMenuOpen(false) }} />
              <MenuItem icon={Trash2} label="Remove" danger disabled={disabled} onClick={() => { onRemove(); setMenuOpen(false) }} />
```

`MenuItem` already supports `disabled` with its own styling — no change needed there. "Replace" is a toast-only Category-B action, so it stays enabled.

- [ ] **Step 5: Typecheck + full suite**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; vitest green.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/routes/admin.editorial.tsx
git commit -m "feat(admin): wire editorial reads and persist featured reorder/remove"
```

---

### Task 4: Live QA + PR (USER-run gate)

**Files:** none (verification only). The controller does NOT run `verify.sh`; CI is authoritative.

- [ ] **Step 1: Final full gate**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 >/dev/null && npm run build && npx vitest run`
Expected: build 0 errors; full suite green.

- [ ] **Step 2: Live QA** (backend on :18080, Vite proxy → :18080)

**Writes need `super-admin` or `editor`** — a `support` admin can read the page but every save 403s. Revert the Vite proxy only at the very END of the session.

  - All three sections load from the live endpoints (featured slots, push schedule, curated playlists), each with its empty state when the backend returns nothing.
  - **Move up / Move down reorders and survives a page reload** — this is the whole point of the slice.
  - **Remove deletes the slot, toasts only after the save succeeds, and survives a reload.**
  - While a save is in flight the slot menu's Move/Remove items are disabled.
  - Force a failure (stop the backend, or use a `support`-role token) → the error toast fires and the list snaps back to server truth rather than keeping a phantom local edit.
  - "New playlist", "Schedule push", "Replace", and opening a playlist still just toast (Category B).

- [ ] **Step 3: Open the PR**

```bash
git push -u origin feat/frontend-admin-editorial
gh pr create --base master --title "feat(admin): wire Editorial reads and persist featured reorder/remove" --body "<DoD checklist; the full-replace semantics; the Category-B list; note this completes the admin console>"
```

---

## Notes for the executor

- **Branch:** `feat/frontend-admin-editorial` off `master` (spec `99570f0`). BASE for the first review package is the plan commit. Independent of #167/#168.
- **Do NOT** touch backend or stage backend secrets. Frontend-only branch.
- **Already on master — import, do not recreate:** `AdminLoadError`.
- **`PUT /featured` is a whole-list replace.** Never send a partial list; never send a slot with a blank title or a duplicate id (both are 422).
- This is the final admin-console slice.
