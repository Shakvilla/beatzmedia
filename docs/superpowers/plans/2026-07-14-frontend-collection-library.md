# Frontend Collection & Library Wiring (Slice 2b) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the BeatzClik collection/library feature (likes, follows, saved albums, user playlists, owned-track state, Library screen) from its localStorage mock to the real backend, establishing the optimistic-mutation pattern.

**Architecture:** Back `CollectionProvider` with TanStack Query — `collectionQuery` (`GET /v1/me/collection`) is the synchronous read source; each toggle/CRUD method is a `useMutation` that optimistically transforms the cached collection (pure, unit-tested transforms), rolls back + toasts on error, and invalidates on settle. The Library screen resolves its id-lists to rich cards in one request via `POST /v1/catalog/resolve` (slice 2a). `useCollection()`'s public shape is unchanged except `createPlaylist` becomes async.

**Tech Stack:** React 19, TanStack Query 5, TanStack Router 1.169, Vite 6, TypeScript 6, Vitest (Node 22 — the shell default node v10 crashes Vitest/Vite; prefix every `npm`/`npx` command with `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH"`).

## Global Constraints

- **PREREQUISITE — merge order:** this slice builds on **PR #121 (slice 1 frontend foundation)** and **PR #123 (slice 2a `/v1/catalog/resolve`)**. Both MUST be merged to `master` first; then rebase/branch `feat/frontend-collection-library` on the updated `master`. Until then, `apiFetch`, `token.ts`, `mappers.ts`, `queries/catalog.ts`, the router `queryClient` context, real `auth-context`, and `vitest.config.ts` do not exist on this branch. This plan assumes they are present post-merge.
- **Foundation available post-merge (from slice 1):**
  - `src/lib/api/client.ts` → `apiFetch<T>(path, { method?, body?, idempotencyKey? }): Promise<T>` (Bearer auth, `{error:{code,message,field?}}` parsing, 204→undefined, 401→clear+redirect).
  - `src/lib/api/queries/catalog.ts` → `queryOptions` factories using `apiFetch`; `src/lib/api/mappers.ts` → `toArtist/toTrack/toAlbum` + `ArtistWire/TrackWire/AlbumWire`.
  - Router context carries `{ queryClient }`; routes use `loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(...)` + `useSuspenseQuery`.
  - `auth-context` exposes `useAuth(): { account, isAuthenticated, isLoading, login, signup, logout, becomeArtist }`.
  - `queryClient` mounted in `main.tsx`; `CollectionProvider` sits INSIDE `QueryClientProvider`, `AuthProvider`, and `ToastProvider` (so `useQuery`, `useMutation`, `useAuth`, `useToast` all work in it). `PlayerProvider` nests inside `CollectionProvider` and reads `isTrackOwned`.
- **Endpoint contract (as-built, all `@Authenticated`):**
  - `GET /v1/me/collection` → `{ likedTracks, followedArtists, followedPlaylists, followedShows, savedAlbums, ownedTracks: string[]; userPlaylists: {id,title,description,trackIds,createdAt}[] }`.
  - `PUT`/`DELETE /v1/me/likes/tracks/:id` → 204; `.../follows/artists|playlists|shows/:id` → 204; `.../saved/albums/:id` → 204. (`PUT` on a missing target → `404`.)
  - `GET /v1/me/playlists` → `UserPlaylistView[]`; `POST /v1/me/playlists` body `{title}` → **201** `UserPlaylistView`; `PATCH /v1/me/playlists/:id` body `{title}` → 200; `DELETE /v1/me/playlists/:id` → 204; `PUT`/`DELETE /v1/me/playlists/:id/tracks/:trackId` → **200 updated `UserPlaylistView`**.
  - `POST /v1/catalog/resolve` body `{ trackIds?, artistIds?, albumIds?, playlistIds? }` → `{ tracks, artists, albums, playlists }` (lenient; private playlists omitted).
- **`useCollection()` shape is unchanged EXCEPT `createPlaylist: (title, firstTrackId?) => Promise<string>`** (was `=> string`). Only 2 callers.
- **No new lint rules beyond the pre-existing `react-refresh/only-export-components` baseline** (repo has 214 pre-existing such errors; do not add new violations of other rules).
- Verify frontend locally with `npx tsc -b --noEmit`, `npm test`, `npx eslint <touched files>` under Node 22. Live browser QA is the user's gate and needs the running backend with 2a merged.

---

### Task 1: `resolveQuery` + `toPlaylist` mapper

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts`
- Modify: `Frontend/src/lib/api/queries/catalog.ts`
- Test: `Frontend/src/lib/api/queries/catalog.test.ts` (extend existing)

**Interfaces:**
- Consumes: `apiFetch` (client), `toArtist/toTrack/toAlbum` + `ArtistWire/TrackWire/AlbumWire` (mappers, slice 1).
- Produces:
  - `mappers.ts`: `interface PlaylistWire { id; title; description: string|null; creator; creatorAvatar: string|null; image; isPublic: boolean; followers: number|null; trackIds: string[]; tracks: TrackWire[]|null }` and `toPlaylist(wire: PlaylistWire): Playlist`.
  - `catalog.ts`: `resolveQuery(ids: { trackIds?: string[]; artistIds?: string[]; albumIds?: string[]; playlistIds?: string[] })` → `queryOptions` whose data is `{ tracks: Track[]; artists: Artist[]; albums: Album[]; playlists: Playlist[] }`. Consumed by Task 4 (library loader).

- [ ] **Step 1: Write the failing test** (append to `Frontend/src/lib/api/queries/catalog.test.ts`)

Add these imports to the top import block (merge with existing): add `resolveQuery` to the import from `./catalog`. Then append inside the `describe('catalog query factories', ...)` block (before its closing `})`):

```ts
  it('resolveQuery posts the id-lists and maps all four kinds', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      tracks: [{ id: 't1', title: 'A', artistId: 'a1', artistName: 'Art', albumId: null, albumTitle: null, duration: 10, image: 'i', ownership: 'free', price: null, plays: 1, audioUrl: null, credits: null, quality: null, year: null }],
      artists: [{ id: 'a1', name: 'Art', image: 'im', coverImage: null, verified: null, monthlyListeners: null, followers: null, bio: null, location: null, genres: null }],
      albums: [{ id: 'al1', title: 'Alb', artistId: 'a1', artistName: 'Art', year: 2024, coverImage: 'c', genres: null, trackIds: [], tracks: null }],
      playlists: [{ id: 'p1', title: 'PL', description: null, creator: 'C', creatorAvatar: null, image: 'pi', isPublic: true, followers: null, trackIds: ['t1'], tracks: null }],
    })

    const result = await resolveQuery({ trackIds: ['t1'], artistIds: ['a1'], albumIds: ['al1'], playlistIds: ['p1'] }).queryFn!({} as any)

    expect(apiFetch).toHaveBeenCalledWith('/catalog/resolve', {
      method: 'POST',
      body: { trackIds: ['t1'], artistIds: ['a1'], albumIds: ['al1'], playlistIds: ['p1'] },
    })
    expect(result.tracks[0].id).toBe('t1')
    expect(result.artists[0].id).toBe('a1')
    expect(result.albums[0].id).toBe('al1')
    expect(result.playlists[0].id).toBe('p1')
    expect(result.playlists[0].creator).toBe('C')
  })
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend && npx vitest run src/lib/api/queries/catalog.test.ts`
Expected: FAIL — `resolveQuery` is not exported.

- [ ] **Step 3: Add `PlaylistWire` + `toPlaylist` to `mappers.ts`**

Append to `Frontend/src/lib/api/mappers.ts` (and add `Playlist` to the existing `import type { … } from '../../types'` line):

```ts
export interface PlaylistWire {
  id: string
  title: string
  description: string | null
  creator: string
  creatorAvatar: string | null
  image: string
  isPublic: boolean
  followers: number | null
  trackIds: string[]
  /** Populated by the detail endpoint; unused by list resolution. */
  tracks: TrackWire[] | null
}

export function toPlaylist(wire: PlaylistWire): Playlist {
  return {
    id: wire.id,
    title: wire.title,
    description: wire.description ?? undefined,
    creator: wire.creator,
    creatorAvatar: wire.creatorAvatar ?? undefined,
    image: wire.image,
    isPublic: wire.isPublic,
    followers: wire.followers ?? undefined,
    trackIds: wire.trackIds,
  }
}
```

- [ ] **Step 4: Add `resolveQuery` to `catalog.ts`**

Append to `Frontend/src/lib/api/queries/catalog.ts`. First extend its mapper import to include `toPlaylist`, `type ArtistWire`, `type AlbumWire`, `type PlaylistWire` (merge with the existing import from `../mappers`). Then add:

```ts
interface ResolveRequest {
  trackIds?: string[]
  artistIds?: string[]
  albumIds?: string[]
  playlistIds?: string[]
}

interface ResolveWire {
  tracks: TrackWire[]
  artists: ArtistWire[]
  albums: AlbumWire[]
  playlists: PlaylistWire[]
}

export function resolveQuery(ids: ResolveRequest) {
  return queryOptions({
    queryKey: ['resolve', ids],
    queryFn: async () => {
      const wire = await apiFetch<ResolveWire>('/catalog/resolve', { method: 'POST', body: ids })
      return {
        tracks: wire.tracks.map(toTrack),
        artists: wire.artists.map(toArtist),
        albums: wire.albums.map(toAlbum),
        playlists: wire.playlists.map(toPlaylist),
      }
    },
  })
}
```

(`TrackWire` is already imported in `catalog.ts` from slice 1; add `ArtistWire`, `AlbumWire`, `PlaylistWire`, and `toPlaylist` to that same import.)

- [ ] **Step 5: Run the test to verify it passes**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend && npx vitest run src/lib/api/queries/catalog.test.ts`
Expected: PASS (existing catalog tests + the new `resolveQuery` test).

- [ ] **Step 6: Commit**

```bash
cd "/Users/mac/Desktop/BeatzClik FullStack" && git add Frontend/src/lib/api/mappers.ts Frontend/src/lib/api/queries/catalog.ts Frontend/src/lib/api/queries/catalog.test.ts
git commit -m "feat(frontend): resolveQuery + toPlaylist mapper for batch resolve"
```

---

### Task 2: Collection data module — query, mapper, transforms, API calls

**Files:**
- Create: `Frontend/src/lib/api/queries/collection.ts`
- Test: `Frontend/src/lib/api/queries/collection.test.ts`

**Interfaces:**
- Consumes: `apiFetch` (client); `UserPlaylist` from `../../../types`.
- Produces (consumed by Task 3):
  - `COLLECTION_KEY = ['collection'] as const`
  - `interface CollectionData { likedTracks: string[]; followedArtists: string[]; followedPlaylists: string[]; followedShows: string[]; savedAlbums: string[]; ownedTracks: string[]; userPlaylists: UserPlaylist[] }`
  - `EMPTY_COLLECTION: CollectionData` (all arrays empty)
  - `collectionQuery()` → `queryOptions` (data: `CollectionData`)
  - Pure transforms (each returns a NEW `CollectionData`): `toggleMembership(data, key: MembershipKey, id)`, `setOwned(data, ids)`, `upsertPlaylist(data, pl)`, `removePlaylistById(data, id)`, `renamePlaylistById(data, id, title)`, `putPlaylist(data, pl)` — where `type MembershipKey = 'likedTracks'|'followedArtists'|'followedPlaylists'|'followedShows'|'savedAlbums'`
  - API calls: `apiToggleMembership(kind, id, add)`, `apiCreatePlaylist(title)`, `apiAddTrack(playlistId, trackId)`, `apiRemovePlaylistTrack(playlistId, trackId)`, `apiDeletePlaylist(id)`, `apiRenamePlaylist(id, title)` — where `type MembershipKind = 'likes/tracks'|'follows/artists'|'follows/playlists'|'follows/shows'|'saved/albums'`

- [ ] **Step 1: Write the failing tests**

Create `Frontend/src/lib/api/queries/collection.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { apiFetch } from '../client'
import {
  collectionQuery,
  EMPTY_COLLECTION,
  toggleMembership,
  setOwned,
  putPlaylist,
  removePlaylistById,
  renamePlaylistById,
  apiToggleMembership,
  apiCreatePlaylist,
  apiAddTrack,
  type CollectionData,
} from './collection'

vi.mock('../client', () => ({ apiFetch: vi.fn() }))

const base: CollectionData = {
  likedTracks: ['t1'],
  followedArtists: [],
  followedPlaylists: [],
  followedShows: [],
  savedAlbums: [],
  ownedTracks: [],
  userPlaylists: [{ id: 'p1', title: 'Mine', trackIds: ['t1'], createdAt: '2026-01-01' }],
}

describe('collection transforms (pure)', () => {
  it('toggleMembership adds when absent and removes when present', () => {
    const added = toggleMembership(base, 'followedArtists', 'a1')
    expect(added.followedArtists).toEqual(['a1'])
    const removed = toggleMembership(base, 'likedTracks', 't1')
    expect(removed.likedTracks).toEqual([])
    // input not mutated
    expect(base.likedTracks).toEqual(['t1'])
  })

  it('setOwned unions ids without duplicates', () => {
    const next = setOwned({ ...base, ownedTracks: ['t1'] }, ['t1', 't2'])
    expect(next.ownedTracks.sort()).toEqual(['t1', 't2'])
  })

  it('putPlaylist prepends, removePlaylistById drops, renamePlaylistById renames', () => {
    const pl = { id: 'p2', title: 'New', trackIds: [], createdAt: '2026-02-02' }
    expect(putPlaylist(base, pl).userPlaylists[0].id).toBe('p2')
    expect(removePlaylistById(base, 'p1').userPlaylists).toEqual([])
    expect(renamePlaylistById(base, 'p1', 'Renamed').userPlaylists[0].title).toBe('Renamed')
  })
})

describe('collection API calls', () => {
  beforeEach(() => vi.mocked(apiFetch).mockReset())

  it('collectionQuery maps the wire view, nulling playlist description to undefined', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      likedTracks: ['t1'], followedArtists: [], followedPlaylists: [], followedShows: [],
      savedAlbums: [], ownedTracks: ['t9'],
      userPlaylists: [{ id: 'p1', title: 'Mine', description: null, trackIds: ['t1'], createdAt: '2026-01-01' }],
    })
    const data = await collectionQuery().queryFn!({} as any)
    expect(apiFetch).toHaveBeenCalledWith('/me/collection')
    expect(data.ownedTracks).toEqual(['t9'])
    expect(data.userPlaylists[0].description).toBeUndefined()
  })

  it('apiToggleMembership uses PUT to add and DELETE to remove', async () => {
    vi.mocked(apiFetch).mockResolvedValue(undefined)
    await apiToggleMembership('likes/tracks', 't1', true)
    expect(apiFetch).toHaveBeenCalledWith('/me/likes/tracks/t1', { method: 'PUT' })
    await apiToggleMembership('follows/artists', 'a1', false)
    expect(apiFetch).toHaveBeenCalledWith('/me/follows/artists/a1', { method: 'DELETE' })
  })

  it('apiCreatePlaylist posts the title and returns the created playlist', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ id: 'p9', title: 'X', description: null, trackIds: [], createdAt: '2026-03-03' })
    const pl = await apiCreatePlaylist('X')
    expect(apiFetch).toHaveBeenCalledWith('/me/playlists', { method: 'POST', body: { title: 'X' } })
    expect(pl.id).toBe('p9')
    expect(pl.description).toBeUndefined()
  })

  it('apiAddTrack PUTs and returns the updated playlist', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ id: 'p1', title: 'Mine', description: null, trackIds: ['t1', 't2'], createdAt: '2026-01-01' })
    const pl = await apiAddTrack('p1', 't2')
    expect(apiFetch).toHaveBeenCalledWith('/me/playlists/p1/tracks/t2', { method: 'PUT' })
    expect(pl.trackIds).toEqual(['t1', 't2'])
  })

  it('EMPTY_COLLECTION has all empty arrays', () => {
    expect(EMPTY_COLLECTION.likedTracks).toEqual([])
    expect(EMPTY_COLLECTION.userPlaylists).toEqual([])
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend && npx vitest run src/lib/api/queries/collection.test.ts`
Expected: FAIL — `Failed to resolve import "./collection"`.

- [ ] **Step 3: Implement `collection.ts`**

Create `Frontend/src/lib/api/queries/collection.ts`:

```ts
import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import type { UserPlaylist } from '../../../types'

export const COLLECTION_KEY = ['collection'] as const

export type MembershipKey =
  | 'likedTracks'
  | 'followedArtists'
  | 'followedPlaylists'
  | 'followedShows'
  | 'savedAlbums'

export interface CollectionData {
  likedTracks: string[]
  followedArtists: string[]
  followedPlaylists: string[]
  followedShows: string[]
  savedAlbums: string[]
  ownedTracks: string[]
  userPlaylists: UserPlaylist[]
}

export const EMPTY_COLLECTION: CollectionData = {
  likedTracks: [],
  followedArtists: [],
  followedPlaylists: [],
  followedShows: [],
  savedAlbums: [],
  ownedTracks: [],
  userPlaylists: [],
}

interface UserPlaylistWire {
  id: string
  title: string
  description: string | null
  trackIds: string[]
  createdAt: string
}

interface CollectionWire {
  likedTracks: string[]
  followedArtists: string[]
  followedPlaylists: string[]
  followedShows: string[]
  savedAlbums: string[]
  ownedTracks: string[]
  userPlaylists: UserPlaylistWire[]
}

function toPlaylist(w: UserPlaylistWire): UserPlaylist {
  return {
    id: w.id,
    title: w.title,
    description: w.description ?? undefined,
    trackIds: w.trackIds,
    createdAt: w.createdAt,
  }
}

function toCollectionData(w: CollectionWire): CollectionData {
  return {
    likedTracks: w.likedTracks,
    followedArtists: w.followedArtists,
    followedPlaylists: w.followedPlaylists,
    followedShows: w.followedShows,
    savedAlbums: w.savedAlbums,
    ownedTracks: w.ownedTracks,
    userPlaylists: w.userPlaylists.map(toPlaylist),
  }
}

export function collectionQuery() {
  return queryOptions({
    queryKey: COLLECTION_KEY,
    queryFn: async () => toCollectionData(await apiFetch<CollectionWire>('/me/collection')),
  })
}

// ---- pure optimistic transforms (never mutate the input) ----

export function toggleMembership(data: CollectionData, key: MembershipKey, id: string): CollectionData {
  const list = data[key]
  const next = list.includes(id) ? list.filter((x) => x !== id) : [id, ...list]
  return { ...data, [key]: next }
}

export function setOwned(data: CollectionData, ids: string[]): CollectionData {
  return { ...data, ownedTracks: Array.from(new Set([...data.ownedTracks, ...ids])) }
}

export function putPlaylist(data: CollectionData, pl: UserPlaylist): CollectionData {
  return { ...data, userPlaylists: [pl, ...data.userPlaylists.filter((p) => p.id !== pl.id)] }
}

export function removePlaylistById(data: CollectionData, id: string): CollectionData {
  return { ...data, userPlaylists: data.userPlaylists.filter((p) => p.id !== id) }
}

export function renamePlaylistById(data: CollectionData, id: string, title: string): CollectionData {
  return {
    ...data,
    userPlaylists: data.userPlaylists.map((p) => (p.id === id ? { ...p, title } : p)),
  }
}

// ---- raw API calls ----

export type MembershipKind =
  | 'likes/tracks'
  | 'follows/artists'
  | 'follows/playlists'
  | 'follows/shows'
  | 'saved/albums'

export async function apiToggleMembership(kind: MembershipKind, id: string, add: boolean): Promise<void> {
  await apiFetch(`/me/${kind}/${id}`, { method: add ? 'PUT' : 'DELETE' })
}

export async function apiCreatePlaylist(title: string): Promise<UserPlaylist> {
  return toPlaylist(await apiFetch<UserPlaylistWire>('/me/playlists', { method: 'POST', body: { title } }))
}

export async function apiRenamePlaylist(id: string, title: string): Promise<UserPlaylist> {
  return toPlaylist(await apiFetch<UserPlaylistWire>(`/me/playlists/${id}`, { method: 'PATCH', body: { title } }))
}

export async function apiDeletePlaylist(id: string): Promise<void> {
  await apiFetch(`/me/playlists/${id}`, { method: 'DELETE' })
}

export async function apiAddTrack(playlistId: string, trackId: string): Promise<UserPlaylist> {
  return toPlaylist(await apiFetch<UserPlaylistWire>(`/me/playlists/${playlistId}/tracks/${trackId}`, { method: 'PUT' }))
}

export async function apiRemovePlaylistTrack(playlistId: string, trackId: string): Promise<UserPlaylist> {
  return toPlaylist(await apiFetch<UserPlaylistWire>(`/me/playlists/${playlistId}/tracks/${trackId}`, { method: 'DELETE' }))
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend && npx vitest run src/lib/api/queries/collection.test.ts`
Expected: PASS (all transform + API-call tests).

- [ ] **Step 5: Commit**

```bash
cd "/Users/mac/Desktop/BeatzClik FullStack" && git add Frontend/src/lib/api/queries/collection.ts Frontend/src/lib/api/queries/collection.test.ts
git commit -m "feat(frontend): collection query + optimistic transforms + API calls"
```

---

### Task 3: Rewrite `CollectionProvider` (Query-backed) + update `createPlaylist` callers

**Files:**
- Modify (full rewrite of the provider body): `Frontend/src/features/collection/collection-context.tsx`
- Modify: `Frontend/src/features/collection/components/create-playlist-modal.tsx`
- Modify: `Frontend/src/features/collection/components/add-to-playlist-modal.tsx`

**Interfaces:**
- Consumes: everything from `collection.ts` (Task 2); `apiFetch`-based calls; `useAuth` (auth-context); `useToast` (toast-provider); `useQuery`/`useMutation`/`useQueryClient` (react-query).
- Produces: `useCollection()` with the SAME shape as today EXCEPT `createPlaylist: (title: string, firstTrackId?: string) => Promise<string>`. `CollectionProvider` unchanged as an export name/signature.

- [ ] **Step 1: Rewrite `collection-context.tsx`**

Replace the ENTIRE file with:

```tsx
/**
 * Collection store — the user's saved/liked/followed items + their own playlists.
 *
 * Backed by TanStack Query against /v1/me/collection. Every mutating action updates
 * the cache optimistically (instant UI), calls the API, rolls back + toasts on error,
 * and invalidates on settle. `useCollection()`'s shape is unchanged except
 * createPlaylist is async (the id is server-generated).
 */

import { createContext, useContext, useEffect, useMemo, type ReactNode } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { UserPlaylist } from '../../types'
import { useAuth } from '../auth/auth-context'
import { useToast } from '../../components/ui/toast-provider'
import {
  COLLECTION_KEY,
  EMPTY_COLLECTION,
  collectionQuery,
  toggleMembership,
  setOwned,
  putPlaylist,
  removePlaylistById,
  apiToggleMembership,
  apiCreatePlaylist,
  apiAddTrack,
  apiRemovePlaylistTrack,
  apiDeletePlaylist,
  type CollectionData,
  type MembershipKey,
  type MembershipKind,
} from '../../lib/api/queries/collection'

interface CollectionContextValue extends CollectionData {
  toggleLikedTrack: (id: string) => void
  toggleFollowedArtist: (id: string) => void
  toggleFollowedPlaylist: (id: string) => void
  toggleFollowedShow: (id: string) => void
  toggleSavedAlbum: (id: string) => void
  isTrackLiked: (id: string) => boolean
  isArtistFollowed: (id: string) => boolean
  isPlaylistFollowed: (id: string) => boolean
  isShowFollowed: (id: string) => boolean
  isAlbumSaved: (id: string) => boolean
  isTrackOwned: (id: string) => boolean
  markTracksOwned: (ids: string[]) => void
  createPlaylist: (title: string, firstTrackId?: string) => Promise<string>
  deletePlaylist: (id: string) => void
  renamePlaylist: (id: string, title: string) => void
  addTrackToPlaylist: (playlistId: string, trackId: string) => void
  removeTrackFromPlaylist: (playlistId: string, trackId: string) => void
  getUserPlaylist: (id: string) => UserPlaylist | undefined
  isTrackInPlaylist: (playlistId: string, trackId: string) => boolean
}

const CollectionContext = createContext<CollectionContextValue | null>(null)

/** Pairs a membership predicate key with its REST path segment. */
const MEMBERSHIP: Record<MembershipKey, MembershipKind> = {
  likedTracks: 'likes/tracks',
  followedArtists: 'follows/artists',
  followedPlaylists: 'follows/playlists',
  followedShows: 'follows/shows',
  savedAlbums: 'saved/albums',
}

export function CollectionProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const { toast } = useToast()
  const queryClient = useQueryClient()

  const { data } = useQuery({ ...collectionQuery(), enabled: isAuthenticated })
  const collection = data ?? EMPTY_COLLECTION

  // Drop the collection cache when the session ends (server is the source of truth).
  useEffect(() => {
    if (!isAuthenticated) queryClient.removeQueries({ queryKey: COLLECTION_KEY })
  }, [isAuthenticated, queryClient])

  // Generic optimistic-cache helper: apply `transform` immediately, run `call`, roll back on error.
  async function optimistic(transform: (d: CollectionData) => CollectionData, call: () => Promise<unknown>) {
    await queryClient.cancelQueries({ queryKey: COLLECTION_KEY })
    const prev = queryClient.getQueryData<CollectionData>(COLLECTION_KEY) ?? EMPTY_COLLECTION
    queryClient.setQueryData<CollectionData>(COLLECTION_KEY, transform(prev))
    try {
      await call()
    } catch {
      queryClient.setQueryData<CollectionData>(COLLECTION_KEY, prev)
      toast('Could not update your library', 'error')
    } finally {
      queryClient.invalidateQueries({ queryKey: COLLECTION_KEY })
    }
  }

  const toggle = (key: MembershipKey, id: string) => {
    const willAdd = !collection[key].includes(id)
    void optimistic(
      (d) => toggleMembership(d, key, id),
      () => apiToggleMembership(MEMBERSHIP[key], id, willAdd),
    )
  }

  const createMutation = useMutation({
    mutationFn: async ({ title, firstTrackId }: { title: string; firstTrackId?: string }) => {
      const created = await apiCreatePlaylist(title)
      if (firstTrackId) return apiAddTrack(created.id, firstTrackId)
      return created
    },
    onSuccess: (playlist) => {
      queryClient.setQueryData<CollectionData>(COLLECTION_KEY, (prev) =>
        putPlaylist(prev ?? EMPTY_COLLECTION, playlist),
      )
      queryClient.invalidateQueries({ queryKey: COLLECTION_KEY })
    },
    onError: () => toast('Could not create the playlist', 'error'),
  })

  const value = useMemo<CollectionContextValue>(
    () => ({
      ...collection,
      toggleLikedTrack: (id) => toggle('likedTracks', id),
      toggleFollowedArtist: (id) => toggle('followedArtists', id),
      toggleFollowedPlaylist: (id) => toggle('followedPlaylists', id),
      toggleFollowedShow: (id) => toggle('followedShows', id),
      toggleSavedAlbum: (id) => toggle('savedAlbums', id),
      isTrackLiked: (id) => collection.likedTracks.includes(id),
      isArtistFollowed: (id) => collection.followedArtists.includes(id),
      isPlaylistFollowed: (id) => collection.followedPlaylists.includes(id),
      isShowFollowed: (id) => collection.followedShows.includes(id),
      isAlbumSaved: (id) => collection.savedAlbums.includes(id),
      isTrackOwned: (id) => collection.ownedTracks.includes(id),
      // Ownership is granted server-side at checkout (a later slice). Here we only reflect it
      // optimistically in the cache; GET /me/collection is the real source.
      markTracksOwned: (ids) =>
        queryClient.setQueryData<CollectionData>(COLLECTION_KEY, (prev) => setOwned(prev ?? EMPTY_COLLECTION, ids)),
      createPlaylist: async (title, firstTrackId) => {
        const playlist = await createMutation.mutateAsync({ title: title.trim() || 'New Playlist', firstTrackId })
        return playlist.id
      },
      deletePlaylist: (id) =>
        void optimistic((d) => removePlaylistById(d, id), () => apiDeletePlaylist(id)),
      renamePlaylist: (id, title) =>
        void optimistic(
          (d) => ({ ...d, userPlaylists: d.userPlaylists.map((p) => (p.id === id ? { ...p, title } : p)) }),
          () => apiRenamePlaylist(id, title),
        ),
      addTrackToPlaylist: (playlistId, trackId) =>
        void optimistic(
          (d) => ({
            ...d,
            userPlaylists: d.userPlaylists.map((p) =>
              p.id === playlistId && !p.trackIds.includes(trackId)
                ? { ...p, trackIds: [...p.trackIds, trackId] }
                : p,
            ),
          }),
          () => apiAddTrack(playlistId, trackId),
        ),
      removeTrackFromPlaylist: (playlistId, trackId) =>
        void optimistic(
          (d) => ({
            ...d,
            userPlaylists: d.userPlaylists.map((p) =>
              p.id === playlistId ? { ...p, trackIds: p.trackIds.filter((t) => t !== trackId) } : p,
            ),
          }),
          () => apiRemovePlaylistTrack(playlistId, trackId),
        ),
      getUserPlaylist: (id) => collection.userPlaylists.find((p) => p.id === id),
      isTrackInPlaylist: (playlistId, trackId) =>
        collection.userPlaylists.find((p) => p.id === playlistId)?.trackIds.includes(trackId) ?? false,
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [collection],
  )

  return <CollectionContext.Provider value={value}>{children}</CollectionContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useCollection(): CollectionContextValue {
  const ctx = useContext(CollectionContext)
  if (!ctx) throw new Error('useCollection must be used within a <CollectionProvider>')
  return ctx
}
```

Note `apiRenamePlaylist` must be in the import list from `../../lib/api/queries/collection` — add it (it's exported by Task 2). The `react-refresh` disable comment is kept (pre-existing pattern); the `react-hooks/exhaustive-deps` disable is because the closures intentionally close over the stable `queryClient`/`toast`/`createMutation` and re-memoize only on `collection`.

- [ ] **Step 2: Update `create-playlist-modal.tsx` for async createPlaylist**

In `Frontend/src/features/collection/components/create-playlist-modal.tsx`, change `submit` to async:

```tsx
  const submit = async () => {
    const title = name.trim()
    if (!title) return
    const id = await createPlaylist(title)
    setName('')
    onClose()
    onCreated?.(id)
  }
```

- [ ] **Step 3: Update `add-to-playlist-modal.tsx` for async createPlaylist**

In `Frontend/src/features/collection/components/add-to-playlist-modal.tsx`, change `createAndAdd` to async:

```tsx
  const createAndAdd = async () => {
    const title = name.trim()
    if (!title || !trackId) return
    await createPlaylist(title, trackId)
    toast(`Added to ${title}`, 'success')
    setName('')
    setCreating(false)
  }
```

- [ ] **Step 4: Type-check and lint the touched files**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend && npx tsc -b --noEmit`
Expected: no errors.

Run: `npx eslint src/features/collection/collection-context.tsx src/features/collection/components/create-playlist-modal.tsx src/features/collection/components/add-to-playlist-modal.tsx | grep -vE 'react-refresh/only-export-components' | tail -20`
Expected: no NEW rule violations (only the pre-existing `react-refresh` line on `useCollection`, filtered out). If any `react-hooks/*` or unused-var errors surface in the touched files, fix them before committing.

- [ ] **Step 5: Run the full unit suite**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend && npm test`
Expected: all test files pass (catalog + collection + slice-1 client/token/mappers).

- [ ] **Step 6: Commit**

```bash
cd "/Users/mac/Desktop/BeatzClik FullStack" && git add Frontend/src/features/collection/collection-context.tsx Frontend/src/features/collection/components/create-playlist-modal.tsx Frontend/src/features/collection/components/add-to-playlist-modal.tsx
git commit -m "feat(frontend): back CollectionProvider with TanStack Query + optimistic mutations"
```

---

### Task 4: Wire the Library route

**Files:**
- Modify: `Frontend/src/routes/library.tsx`

**Interfaces:**
- Consumes: `collectionQuery` (Task 2), `resolveQuery` (Task 1), `useCollection` (Task 3); `queryClient` from router context (slice 1).

- [ ] **Step 1: Add loader + swap mock getters for resolved data**

Replace the top of `Frontend/src/routes/library.tsx` — the imports, `Route`, and the start of `LibraryComponent` through the derived-list consts — with:

```tsx
import { createFileRoute, useNavigate, Link } from '@tanstack/react-router'
import { useSuspenseQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Heart, Check, Play, Plus, ListMusic } from 'lucide-react'
import { Card, CardContent, CardImage, CardSubtitle, CardTitle } from '../components/ui/card'
import { usePlayer } from '../features/player/player-context'
import { useCollection } from '../features/collection/collection-context'
import { CreatePlaylistModal } from '../features/collection/components/create-playlist-modal'
import { resolveQuery } from '../lib/api/queries/catalog'
import { collectionQuery } from '../lib/api/queries/collection'
import { formatDuration } from '../lib/format'
import { cn } from '../utils/cn'

export const Route = createFileRoute('/library')({
  loader: async ({ context: { queryClient } }) => {
    const c = await queryClient.ensureQueryData(collectionQuery())
    await queryClient.ensureQueryData(
      resolveQuery({
        trackIds: c.likedTracks,
        artistIds: c.followedArtists,
        albumIds: c.savedAlbums,
        playlistIds: c.followedPlaylists,
      }),
    )
  },
  component: LibraryComponent,
})

const TABS = ['All', 'Playlists', 'Albums', 'Artists', 'Liked'] as const
type Tab = (typeof TABS)[number]

function LibraryComponent() {
  const [tab, setTab] = useState<Tab>('All')
  const [createOpen, setCreateOpen] = useState(false)
  const navigate = useNavigate()
  const { playQueue } = usePlayer()
  const { likedTracks, followedPlaylists, followedArtists, savedAlbums, userPlaylists, ownedTracks } = useCollection()

  const { data: resolved } = useSuspenseQuery(
    resolveQuery({ trackIds: likedTracks, artistIds: followedArtists, albumIds: savedAlbums, playlistIds: followedPlaylists }),
  )
  const likedList = resolved.tracks
  const playlists = resolved.playlists
  const artistsList = resolved.artists
  const albumsList = resolved.albums
  const ownedCount = ownedTracks.length
  const playlistCoverById = new Map(resolved.tracks.map((t) => [t.id, t.image]))
```

Two things this changes vs. the mock version:
- `likedList`/`playlists`/`artistsList`/`albumsList` now come from the single `resolveQuery`, not per-item mock getters. Their element shapes (`Track`/`Playlist`/`Artist`/`Album`) are identical, so the JSX below is unchanged.
- `ownedCount` is now `ownedTracks.length` (backend-driven), matching Settings — replacing the old `allTracks.filter(t => t.ownership === 'owned').length`.
- The user-playlist tile cover previously used `getTracksByIds(p.trackIds)[0]?.image`. Since user-playlist tracks may not be in the resolved liked-tracks set, replace that lookup (Step 2).
- The old imports `getAlbum, getArtist, getPlaylist, getTracksByIds, tracks as allTracks` from `../lib/mock-data` are fully removed (replaced by the two query imports above).

- [ ] **Step 2: Fix the user-playlist tile cover lookup**

In the `{showSection('Playlists') && userPlaylists.map((p) => { ... })}` block, replace:
```tsx
            const cover = getTracksByIds(p.trackIds)[0]?.image
```
with a lookup against the resolved liked-tracks map (falls back to the placeholder tile when the first track isn't in the resolved set):
```tsx
            const cover = p.trackIds.map((id) => playlistCoverById.get(id)).find(Boolean)
```
`playlistCoverById` was defined in Step 1. This keeps a cover when the playlist's first track happens to be a liked track, and otherwise shows the existing gradient `ListMusic` placeholder — no visual regression versus the mock, which also only had covers for tracks it knew about.

- [ ] **Step 3: Type-check**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend && npx tsc -b --noEmit`
Expected: no errors. (If TS flags the removed `getAlbum/getArtist/getPlaylist/getTracksByIds/allTracks` imports as unused, confirm they were fully removed from the import block in Step 1 — they should be gone.)

- [ ] **Step 4: Commit**

```bash
cd "/Users/mac/Desktop/BeatzClik FullStack" && git add Frontend/src/routes/library.tsx
git commit -m "feat(frontend): wire Library route to collection + /v1/catalog/resolve"
```

---

### Task 5: Full-slice verification + PR

**Files:** none (verification + PR only).

- [ ] **Step 1: Full frontend checks**

Run:
```bash
export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend
npm test && npx tsc -b --noEmit && npm run build
```
Expected: unit tests pass; tsc clean; production build succeeds.

- [ ] **Step 2: Confirm no new lint violations**

Run:
```bash
export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend
npx eslint src/lib/api/queries/collection.ts src/lib/api/queries/catalog.ts src/lib/api/mappers.ts src/features/collection/collection-context.tsx src/routes/library.tsx 2>&1 | grep -oE '[a-z-]+/[a-z-]+$' | sort | uniq -c
```
Expected: only `react-refresh/only-export-components` entries (pre-existing pattern). Any other rule → fix it.

- [ ] **Step 3: Live browser QA (user-run; needs #121 + #123 merged and the backend running)**

Tell the user to run the dev stack (`cd backend && ./mvnw quarkus:dev`, and `cd Frontend && npm run dev` under Node 22), then, signed in:
1. **Like a track** on a track page → heart fills instantly; Network shows `PUT /v1/me/likes/tracks/:id → 204`.
2. **Follow an artist** → button flips instantly; `PUT /v1/me/follows/artists/:id → 204`.
3. **Create a playlist** (Library "Create playlist" tile) → lands on the new playlist; `POST /v1/me/playlists → 201`.
4. **Add a track to a playlist** via the track page's add-to-playlist modal → `PUT /v1/me/playlists/:id/tracks/:trackId → 200`.
5. **Open Library** → liked/followed/saved cards render; Network shows ONE `POST /v1/catalog/resolve`.
6. **Reload** the page → all of the above persist (now from the backend, not localStorage).
7. **Log out, log back in** → collection re-hydrates from `GET /v1/me/collection`.
8. Console shows **zero errors** throughout.

- [ ] **Step 4: Open the PR**

```bash
cd "/Users/mac/Desktop/BeatzClik FullStack" && git push -u origin feat/frontend-collection-library
gh pr create --base master --head feat/frontend-collection-library --title "feat(frontend): wire collection + library to the real backend (slice 2b)" --body "$(cat <<'EOF'
## Summary
- Back `CollectionProvider` with TanStack Query (`GET /v1/me/collection`); every like/follow/save and
  playlist CRUD is an optimistic mutation (instant UI, rollback + toast on error, invalidate on settle).
- Wire the Library screen: resolves its id-lists to cards in one `POST /v1/catalog/resolve` (slice 2a);
  Owned-count now sourced from `ownedTracks` (agrees with Settings).
- Drop the `beatzclik-collection` localStorage store; clear the collection on logout.
- `useCollection()` shape unchanged except `createPlaylist` is now async (server-generated id); its 2
  callers updated.

Depends on: #121 (slice 1 foundation) and #123 (slice 2a resolve endpoint) — both merged.

Design: docs/superpowers/specs/2026-07-14-frontend-collection-library-design.md
Plan: docs/superpowers/plans/2026-07-14-frontend-collection-library.md

## Test plan
- [x] Vitest: resolveQuery + collection query/transforms/API calls
- [x] tsc -b clean; production build OK; no new lint violations
- [x] Live QA: like/follow/create-playlist/add-track work, persist across reload, clear on logout;
      Library resolves via one /v1/catalog/resolve; zero console errors

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
