# Frontend Consumer Quick-Wins Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the fan-facing browse experience by wiring the last small consumer surfaces — **Playlist detail**, the **Artist page's events strip**, and **Notifications** — from mock data to their existing backend endpoints, with no visual change.

**Architecture:** Same idiom as the just-merged store/events/podcasts wiring: TanStack `queryOptions` + `apiFetch<Wire>` + `mappers.toX` for reads; for the notification mark-read writes, async `apiFetch` mutation functions + optimistic update in the context (mirroring the existing `collection-context.tsx` / `cart-context.tsx`). Reuse the already-shipped `eventsListQuery` and `toPlaylist`.

**Tech Stack:** React 19 + TanStack Query v5 + TanStack Router, TypeScript, Vitest + RTL, Vite proxy `/v1` → `127.0.0.1:8080`.

## Global Constraints

- **No visual change.** Data-source swap only; JSX/classes preserved. The frontend is the functional spec.
- Mapper outputs match the exact `Frontend/src/types/index.ts` types (`Playlist`, `Event`, `AppNotification`); money stays the `Money` type `{ amount, currency }`.
- `apiFetch` (`lib/api/client.ts`) prepends `BASE_URL='/v1'` — query paths are written WITHOUT `/v1` (e.g. `/playlists/{id}`). This is the established idiom (`catalog.ts`).
- Paginated list endpoints return `{ items, page, size, total, ... }`; unwrap with `unwrapPage` (`lib/api/pagination.ts`) or read `.items` directly.
- No `@testing-library/jest-dom` → RTL uses `toBeTruthy()` / `queryByText`.
- All commands from `Frontend/`. Test: `npx vitest run <path>`. Typecheck: `npx tsc --noEmit`. Lint: `npm run lint` (repo has ~214 pre-existing errors; add no NEW ones).
- Branch: `feat/frontend-consumer-quickwins` off master.

## Out of scope (deferred — no backend endpoint exists)
The home page's **"Made for you" (playlists rail)**, **"New releases" (albums rail)**, and `featured-carousel`'s `getAlbumTracks` still read `lib/mock-data.ts`. The catalog backend exposes only `/v1/playlists/{id}` and `/v1/albums/{id}` (detail) + the home feed (`homeQuery`) + `browse-categories` — there is **no "list all playlists" or "list all albums" endpoint** to source those rails. Wiring them needs new backend work (e.g. `GET /v1/playlists`, `GET /v1/albums?sort=newest`) or a product decision to reuse `browseCategoriesQuery`/`homeQuery` data. Left on mock; `index.tsx` stays hybrid. Do NOT delete `lib/mock-data.ts`.

---

## File Structure

- `Frontend/src/lib/api/queries/catalog.ts` (modify) — add `playlistQuery(id)` (hits `/v1/playlists/{id}` → `toPlaylist`).
- `Frontend/src/routes/playlist.$playlistId.tsx` (modify) — wire the catalog-playlist branch via `playlistQuery`; drop the `mock-data` catalog-playlist lookups.
- `Frontend/src/routes/artist/$artistId.tsx` (modify) — replace the `events` mock import with `eventsListQuery()` + client-filter by `artistId`.
- `Frontend/src/lib/api/queries/notifications.ts` (new) — `notificationsQuery()` (GET list → `{ items: AppNotification[]; unread: number }`), `apiMarkAllRead()`, `apiMarkOneRead(id)`.
- `Frontend/src/lib/api/mappers.ts` (modify) — add `AppNotificationWire` + `toAppNotification` (near-1:1; `type` string → `NotificationType`).
- `Frontend/src/features/notifications/notifications-context.tsx` (modify) — back the context with `notificationsQuery` + the mark-read mutations (optimistic), replacing the `useState`+localStorage mock store; keep the `markRead`/`markAllRead`/`notifications`/`unreadCount` API the consumers already use.
- Tests co-located: `notifications.test.ts`, cases in `mappers.test.ts`.

---

## Task 1: `playlistQuery` + wire playlist detail

**Files:**
- Modify: `Frontend/src/lib/api/queries/catalog.ts`
- Modify: `Frontend/src/routes/playlist.$playlistId.tsx`

**Interfaces:**
- Consumes: `toPlaylist`/`PlaylistWire` (already in `lib/api/mappers.ts`), `apiFetch`.
- Produces: `export function playlistQuery(id: string)` returning `queryOptions` for `Playlist`.

**Prep:** Read `routes/playlist.$playlistId.tsx` fully. It currently does: `const mock = getPlaylist(playlistId)`, checks a `userPl` (user-created playlist) branch first, and pulls tracks via `getPlaylistTracks(mock.id)` / `getTracksByIds(userPl.trackIds)`. Confirm the `userPl` branch's data source (the collection context — likely already real). Confirm `Playlist` (types/index.ts) includes its tracks (the catalog `PlaylistWire`/`toPlaylist` shape — check whether `/v1/playlists/{id}` returns tracks inline or needs a separate call). This determines whether `playlistQuery` alone is sufficient for the catalog branch.

- [ ] **Step 1: Add `playlistQuery` to `catalog.ts`** (mirror `albumQuery`):

```ts
export function playlistQuery(id: string) {
  return queryOptions({
    queryKey: ['playlist', id],
    queryFn: async () => toPlaylist(await apiFetch<PlaylistWire>(`/playlists/${id}`)),
  })
}
```
Add `PlaylistWire` to the import from `../mappers` if not already imported.

- [ ] **Step 2: Typecheck**

Run: `cd Frontend && npx tsc --noEmit`
Expected: clean (`toPlaylist`/`PlaylistWire` already exist).

- [ ] **Step 3: Wire the route's catalog-playlist branch** — replace `getPlaylist(playlistId)` with a loader + `useSuspenseQuery(playlistQuery(playlistId))` for the non-user-playlist path, and source its tracks from the query result (or an `albumQuery`-style tracks field per Prep). Keep the `userPl` branch as-is if it's already real; if the `userPl` branch also reads `mock-data`, wire it to the collection context's real user-playlist query (note it as a concern if non-trivial). Drop the now-unused `getPlaylist`/`getPlaylistTracks`/`getTracksByIds` imports **only if** nothing else in the file needs them.

- [ ] **Step 4: Typecheck + lint + manual check**

Run: `cd Frontend && npx tsc --noEmit && npm run lint`
Expected: clean; no new lint errors. Controller does live QA (open a catalog playlist → `GET /v1/playlists/{id}`).

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/lib/api/queries/catalog.ts Frontend/src/routes/playlist.\$playlistId.tsx
git commit -m "feat(frontend): wire playlist detail to /v1/playlists/{id}"
```

---

## Task 2: Wire the Artist page events strip

**Files:**
- Modify: `Frontend/src/routes/artist/$artistId.tsx`

**Interfaces:**
- Consumes: `eventsListQuery()` (already shipped, `lib/api/queries/events.ts`).

**Prep:** Read `routes/artist/$artistId.tsx`. It imports `{ events }` (the full array) from `lib/event-data` (line 13) and renders an "upcoming events" strip, almost certainly filtered to this artist. Find the exact filter (by `artistId`/`artistName`). The events list endpoint has no `artist` param, so filter client-side over `eventsListQuery()`.

- [ ] **Step 1: Replace the mock events with the real query** — add `eventsListQuery` to the route's loader/component; derive the artist's events client-side, e.g.:

```tsx
const { data: allEvents } = useSuspenseQuery(eventsListQuery())
const artistEvents = allEvents.filter((e) => e.artistId === artistId)
```
Render the existing strip JSX from `artistEvents` instead of the mock `events`. Drop the `import { events } from '../../lib/event-data'` line. Preserve all JSX/classes.

- [ ] **Step 2: Typecheck + lint**

Run: `cd Frontend && npx tsc --noEmit && npm run lint`
Expected: clean; no new lint errors; `grep -n event-data src/routes/artist/\$artistId.tsx` returns nothing.

- [ ] **Step 3: Commit**

```bash
git add Frontend/src/routes/artist/\$artistId.tsx
git commit -m "feat(frontend): wire artist page events strip to /v1/events"
```

(Note: this removes the last `event-data` importer — `lib/event-data.ts` may now be deletable; confirm with `grep -rl event-data src` and delete it in this commit if zero importers remain.)

---

## Task 3: Notifications query + mark-read mutations + mapper

**Files:**
- Create: `Frontend/src/lib/api/queries/notifications.ts`
- Modify: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/queries/notifications.test.ts`, cases in `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Produces: `toAppNotification(wire): AppNotification`; `notificationsQuery()` → `{ items: AppNotification[]; unread: number }`; `apiMarkAllRead(): Promise<void>`; `apiMarkOneRead(id: string): Promise<void>`.

The backend `AppNotificationDto` is `{ id, type, title, body, time, read, to }` and the list response is `{ items, page, size, total, unread }`. The frontend `AppNotification` (`notifications-context.tsx:13`) is `{ id, type: NotificationType, title, body, time, read, to? }` — a near-1:1 map (`type` is a string on the wire, a `NotificationType` union on the domain).

- [ ] **Step 1: Add `toAppNotification` + a failing mapper test**

```ts
// mappers.test.ts
import { toAppNotification, type AppNotificationWire } from './mappers'
describe('toAppNotification', () => {
  it('maps a notification', () => {
    const wire: AppNotificationWire = { id: 'n1', type: 'sale', title: 'You made a sale', body: '₵3.00', time: '2026-07-17T00:00:00Z', read: false, to: null }
    const n = toAppNotification(wire)
    expect(n.id).toBe('n1'); expect(n.type).toBe('sale'); expect(n.read).toBe(false)
  })
})
```
```ts
// mappers.ts
import type { AppNotification, NotificationType } from '../../features/notifications/notifications-context'
export interface AppNotificationWire {
  id: string; type: string; title: string; body: string; time: string; read: boolean; to: string | null
}
export function toAppNotification(w: AppNotificationWire): AppNotification {
  return { id: w.id, type: w.type as NotificationType, title: w.title, body: w.body, time: w.time, read: w.read, to: w.to ?? undefined }
}
```
(If `AppNotification`/`NotificationType` are exported from `notifications-context.tsx` rather than `types/index.ts`, import from there — confirm at Prep. If importing a feature type into `mappers.ts` creates an undesirable dependency, instead move `AppNotification`/`NotificationType` to `types/index.ts` and re-export from the context — decide during Prep and note it.)

- [ ] **Step 2: Run mapper test → fail → (impl above) → pass + tsc**

Run: `cd Frontend && npx vitest run src/lib/api/mappers.test.ts -t toAppNotification && npx tsc --noEmit`
Expected: PASS + clean.

- [ ] **Step 3: Write the failing query-module test**

```ts
// notifications.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as client from '../client'
import { notificationsQuery, apiMarkAllRead, apiMarkOneRead } from './notifications'
vi.mock('../client')
beforeEach(() => vi.resetAllMocks())
describe('notifications', () => {
  it('lists notifications with unread count', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({ items: [{ id: 'n1', type: 'sale', title: 't', body: 'b', time: 'x', read: false, to: null }], page: 1, size: 20, total: 1, unread: 1 })
    const res = await notificationsQuery().queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/me/notifications')
    expect(res.unread).toBe(1); expect(res.items[0].id).toBe('n1')
  })
  it('marks all / one read', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(undefined)
    await apiMarkAllRead(); expect(client.apiFetch).toHaveBeenCalledWith('/me/notifications/read', { method: 'POST' })
    await apiMarkOneRead('n1'); expect(client.apiFetch).toHaveBeenCalledWith('/me/notifications/n1/read', { method: 'POST' })
  })
})
```

- [ ] **Step 4: Run → fail → implement `notifications.ts` → pass**

```ts
// notifications.ts
import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { toAppNotification, type AppNotificationWire } from '../mappers'

interface NotificationsWire { items: AppNotificationWire[]; page: number; size: number; total: number; unread: number }

export function notificationsQuery() {
  return queryOptions({
    queryKey: ['notifications'],
    queryFn: async () => {
      const wire = await apiFetch<NotificationsWire>('/me/notifications')
      return { items: wire.items.map(toAppNotification), unread: wire.unread }
    },
  })
}
export async function apiMarkAllRead(): Promise<void> { await apiFetch('/me/notifications/read', { method: 'POST' }) }
export async function apiMarkOneRead(id: string): Promise<void> { await apiFetch(`/me/notifications/${id}/read`, { method: 'POST' }) }
```

Run: `cd Frontend && npx vitest run src/lib/api/queries/notifications.test.ts && npx tsc --noEmit`
Expected: PASS + clean.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/lib/api/queries/notifications.ts Frontend/src/lib/api/queries/notifications.test.ts Frontend/src/lib/api/mappers.ts Frontend/src/lib/api/mappers.test.ts
git commit -m "feat(frontend): notifications query + mark-read mutations + mapper"
```

---

## Task 4: Rewire `notifications-context` to the backend

**Files:**
- Modify: `Frontend/src/features/notifications/notifications-context.tsx`

**Interfaces:**
- Consumes: `notificationsQuery`, `apiMarkAllRead`, `apiMarkOneRead` (Task 3).
- Produces: unchanged context API — `{ notifications, unreadCount, markRead(id), markAllRead() }` (whatever the consumers already read; preserve the exact shape).

**Prep:** Read `notifications-context.tsx` and `mappers.ts` to confirm the exact `NotificationsContextValue` shape consumers depend on (the bell badge reads `unreadCount`/`unread`; the dropdown reads `notifications`). Read `collection-context.tsx` / `cart-context.tsx` for the established `useQuery` + `useMutation` + optimistic-update idiom in this repo — mirror it exactly.

- [ ] **Step 1: Replace the mock store with the query** — swap `useState(load)`/localStorage for `useQuery(notificationsQuery())`. Derive `notifications = data?.items ?? []` and `unreadCount = data?.unread ?? 0`. Delete the `seed`/`load`/localStorage code.

- [ ] **Step 2: Back `markRead`/`markAllRead` with mutations** — `useMutation({ mutationFn: apiMarkOneRead, onMutate: optimistic set read, onSettled: invalidate ['notifications'] })` and the same for `apiMarkAllRead`, mirroring `collection-context.tsx`'s optimistic pattern. Keep the method signatures the consumers call (`markRead(id)`, `markAllRead()`).

- [ ] **Step 3: Typecheck + lint + full suite**

Run: `cd Frontend && npx tsc --noEmit && npm run lint && npx vitest run src/features/notifications`
Expected: clean; no new lint; any existing notification tests pass (adjust a mock-store test to the query-backed shape if one exists).

- [ ] **Step 4: Commit**

```bash
git add Frontend/src/features/notifications/notifications-context.tsx
git commit -m "feat(frontend): back notifications context with /v1/me/notifications + mark-read"
```

---

## Cross-cutting acceptance (before PR)
- `npx tsc --noEmit` clean; `npm run lint` no new errors; `npx vitest run` — new tests green (the pre-existing `token`/`client`/`cart-context` jsdom-localStorage failures are unrelated and also red on master).
- No `mock-data`/`event-data` import remains in the wired files: `grep -n mock-data src/routes/playlist.\$playlistId.tsx`, `grep -n event-data src/routes/artist/\$artistId.tsx`, and the notifications context has no `seed`/localStorage store.
- Controller live QA (logged in): open a catalog playlist (`GET /v1/playlists/{id}`), an artist page (events strip from `GET /v1/events`), and the notifications bell (`GET /v1/me/notifications`, then mark-read → `POST`). Visual parity + zero console errors.

## Self-Review
- **Coverage:** Playlist detail (Task 1), Artist events strip (Task 2), Notifications read + write (Tasks 3–4). Home carousels explicitly deferred with reason. Every wired item maps to a confirmed endpoint (`/v1/playlists/{id}`, `/v1/events`, `/v1/me/notifications` + `/read` + `/{id}/read`).
- **Placeholder scan:** the `Playlist` tracks-inline-vs-separate-call question and the `AppNotification` type-location question are each a named Prep step with a decision, plus a `tsc` gate — verification instructions, not placeholders.
- **Type consistency:** `playlistQuery`/`toPlaylist`, `notificationsQuery`/`toAppNotification`/`AppNotificationWire`, `apiMarkAllRead`/`apiMarkOneRead` are used consistently across Tasks 3–4.
