# Frontend → Backend Wiring — Slice 2b: Collection & Library

**Date:** 2026-07-14
**Status:** Approved design (pre-implementation)
**Branch:** `feat/frontend-collection-library` (off `master`; build after slice 2a / PR #123 merges)

## Purpose

Wire the BeatzClik frontend's **library / collection** feature — likes, follows, saved albums, user
playlists, owned-track state, and the Library screen — from its localStorage mock to the real backend.
This is the slice that establishes the **optimistic-mutation pattern** (`useMutation` + optimistic
cache update + rollback) that later write-heavy slices (cart/checkout, studio, admin) depend on.

Slice 1 wired read-only catalog via loaders + `useSuspenseQuery`. Slice 2a added
`POST /v1/catalog/resolve` (a batch id→object endpoint). 2b consumes both.

## Scope

**In:** the collection store, its mutations, and the Library screen.
**Out (stay mocked / later slices):** ownership grant at checkout (commerce slice), and
public/catalog playlist-detail wiring beyond what the Library needs.

## Key facts grounding this design

- **The seam is entirely inside `Frontend/src/features/collection/collection-context.tsx`.** Today it's
  a `useReducer` + whole-blob localStorage (`beatzclik-collection`). 12 consumers read its **synchronous**
  API (`isTrackLiked(id): boolean`, `toggleLikedTrack(id): void`, …). Keeping that shape while swapping
  localStorage for the API is the whole job.
- **Shapes match 1:1.** `GET /v1/me/collection` → `CollectionView { likedTracks, followedArtists,
  followedPlaylists, followedShows, savedAlbums, ownedTracks: string[]; userPlaylists: UserPlaylist[] }`
  — identical field names to the provider's `CollectionState`. `UserPlaylistView { id, title,
  description, trackIds, createdAt }` matches the frontend `UserPlaylist`.
- **All endpoints are as-built and authenticated** (JWT `sub` = account id):
  likes `PUT/DELETE /v1/me/likes/tracks/:id → 204`; follows `.../follows/artists|playlists|shows/:id →
  204`; saved `.../saved/albums/:id → 204`; playlists `GET/POST /v1/me/playlists` (`POST` → **201**
  `UserPlaylistView`, body `{title}`), `PATCH/DELETE /v1/me/playlists/:id` (delete → 204), and
  `PUT/DELETE /v1/me/playlists/:id/tracks/:trackId` → **200 updated `UserPlaylistView`**. Toggles are
  idempotent (no 409); a `PUT` like/follow/save on a missing target → `404 <KIND>_NOT_FOUND`.
- **Provider tree** (`main.tsx`): `ToastProvider > QueryClientProvider > AuthProvider > … >
  CollectionProvider > PlayerProvider`. So inside `CollectionProvider` we can use `useQuery`,
  `useMutation`, `useToast`, and `useAuth`. `PlayerProvider` (which reads `isTrackOwned`) is nested
  inside, so the provider must keep exposing that.
- **Two frictions:** (1) mutations are async but predicate reads are synchronous → optimistic updates
  keep the cache the synchronous source of truth; (2) `createPlaylist` returns an id synchronously today
  (client-generated) but the backend generates it on `POST` → `createPlaylist` becomes async.

## Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | Back `CollectionProvider` with **TanStack Query** (`collectionQuery` + per-method `useMutation`), not a hand-rolled reducer+fetch. | This is the pattern 2b exists to prove; gives caching, optimistic updates, and invalidation for free. |
| D2 | **Optimistic updates** on every toggle/CRUD: `onMutate` cancels + snapshots + `setQueryData`, `onError` rolls back + toasts, `onSettled` invalidates `collectionQuery`. | Keeps predicate reads synchronous and the UI instant; matches today's feel. |
| D3 | `useCollection()`'s shape is **unchanged except `createPlaylist` → `Promise<string>`** (server id). Update its 2 callers to `await`. | The id is server-generated; unavoidable, minimal blast radius. |
| D4 | Predicates read from the cached `CollectionView` (empty default before load). Library route uses a **loader** to prefetch so it renders populated. | No `isLoading` branches added to the 12 consumers. |
| D5 | **Library id→object resolution via `POST /v1/catalog/resolve`** (slice 2a), one request. | Bounded, single round trip; the reason 2a was built first. |
| D6 | `markTracksOwned` stays a **local optimistic cache update** (no write endpoint; ownership is granted server-side at checkout — a later slice). | There is no "mark owned" API; `ownedTracks` reflects real grants via `GET /me/collection`. |
| D7 | Drop the `beatzclik-collection` localStorage persistence; **invalidate/clear the collection on logout**; the query is `enabled` only when authenticated. | Server is now the source of truth; a signed-out user has no collection. |

## Architecture

### A. Query + mutations layer — `src/lib/api/queries/collection.ts`
- `collectionQuery()` → `queryOptions({ queryKey: ['collection'], queryFn: () => apiFetch('/me/collection') })`,
  mapping `CollectionView` → the `CollectionState` shape (near-identical; a thin mapper).
- A small set of mutation helpers (or inline `useMutation` configs in the provider) — one per write:
  like, follow-artist, follow-playlist, follow-show, save-album, create-playlist, delete-playlist,
  rename-playlist, add-track, remove-track. Each performs the `PUT`/`DELETE`/`POST`/`PATCH` via
  `apiFetch` and applies the optimistic `setQueryData` transform to the cached `CollectionView`.

### B. Rewrite `src/features/collection/collection-context.tsx`
- `const { data } = useQuery({ ...collectionQuery(), enabled: isAuthenticated })`; derive the 7 arrays
  (empty defaults) from `data`.
- Predicates read those arrays synchronously.
- Each mutating method wraps a `useMutation` (D2). `createPlaylist(title, firstTrackId?)` → `POST
  /me/playlists {title}`; if `firstTrackId`, chain `PUT /me/playlists/:id/tracks/:trackId`; returns the
  new id (async).
- `markTracksOwned(ids)` → optimistic `setQueryData` adding ids to `ownedTracks` (no network).
- On logout (via `useAuth`/the client's `onUnauthorized` already wired in slice 1): `queryClient`
  removes/invalidates `['collection']`.
- `useCollection()` returns the same shape (D3 exception noted).

### C. Resolve query — `src/lib/api/queries/catalog.ts` (extend)
- `resolveQuery({ trackIds?, artistIds?, albumIds?, playlistIds? })` →
  `POST /v1/catalog/resolve` → `{ tracks, artists, albums, playlists }` mapped via the slice-1 mappers.

### D. Library route — `src/routes/library.tsx`
- `loader`: `const c = await ensureQueryData(collectionQuery())`; then
  `ensureQueryData(resolveQuery({ trackIds: c.likedTracks, artistIds: c.followedArtists, albumIds:
  c.savedAlbums, playlistIds: c.followedPlaylists }))`.
- Component reads the collection (ids) + the resolved objects (via `useSuspenseQuery(resolveQuery(...))`)
  and renders identically. `userPlaylists` come as full objects already.
- The "Owned Tracks" tile: switch from the catalog `ownership==='owned'` count to `ownedTracks.length`
  so Library and Settings agree (both backend-driven).

### E. Consumers — no change (shape preserved)
`track.$trackId` (like), `artist/$artistId` (follow), `podcasts`/`podcast.$podcastId` (show follow),
`playlist.$playlistId` (playlist follow, delete, remove-track, `getUserPlaylist`), `settings`,
`sidebar`, `add-to-playlist-modal`, `create-playlist-modal`, and `player-context` (`isTrackOwned`) all
keep calling the same `useCollection()` methods. Only `createPlaylist`'s 2 callers change to `await`.
Album-save (`toggleSavedAlbum`/`isAlbumSaved`) and `renamePlaylist` have no UI caller today — wired but
unused, as now.

## Data flow

```
App (authed) → collectionQuery (GET /me/collection) → cached CollectionView → predicates read synchronously
Toggle like  → useMutation:
                 onMutate:  setQueryData(add/remove id)  ← instant UI
                 mutationFn: PUT|DELETE /me/likes/tracks/:id
                 onError:   rollback snapshot + toast
                 onSettled: invalidate ['collection']
Library route → loader: ensure collection → ensure resolve(ids) → render cards
Logout       → remove ['collection'] from cache
```

## Error handling
- Mutations: `apiFetch` throws `ApiError`; `onError` rolls back the optimistic change and shows a toast
  (e.g. a `404` liking a since-removed track → revert + "Couldn't update your library").
- Collection load failure (non-401): the query surfaces an error; the Library route's `errorComponent`
  catches it; scattered predicate consumers degrade to "not liked/followed" (acceptable).
- `401` is already handled globally (slice 1): clears token + redirects to `/login`.

## Testing
- **Unit (Vitest):** `collectionQuery` maps `CollectionView`; one mutation's optimistic-add +
  rollback-on-error path (mock `apiFetch`); `resolveQuery` builds the right body + maps the response.
- **Live browser QA (needs 2a merged + backend running):** log in; like a track (heart fills instantly,
  network `PUT …/likes/… 204`); follow an artist; create a playlist + add a track; open Library (cards
  resolve via one `/v1/catalog/resolve`); **reload** — state persists via the backend, not localStorage;
  log out/in — collection re-hydrates.

## Risks
- **R1 — optimistic/refetch races:** standard `cancelQueries(['collection'])` in `onMutate` before
  snapshotting avoids in-flight refetches clobbering the optimistic state.
- **R2 — empty collection for new accounts:** a fresh fan's `GET /me/collection` is empty → Library
  shows empty states (correct, matches mock empty state). Data appears as the user likes/follows.
- **R3 — `createPlaylist` async surface change:** contained to 2 callers (create-modal, add-to-playlist
  create-and-add); both updated to `await`.

## Definition of done (Slice 2b)
- `CollectionProvider` backed by TanStack Query; `useCollection()` shape unchanged except
  `createPlaylist` async; localStorage persistence removed; collection cleared on logout.
- Every toggle/CRUD is an optimistic mutation with rollback + toast, hitting the real endpoints.
- Library screen resolves its id-lists via `POST /v1/catalog/resolve` and renders with no visual change;
  Owned-count sourced from `ownedTracks`.
- Vitest unit tests (collection query, one mutation optimism/rollback, resolve query) pass; `tsc -b`
  clean; no new lint violations beyond the pre-existing `react-refresh` baseline.
- Live QA confirms like/follow/create-playlist/add-track work, persist across reload, and clear on
  logout — verified against the running backend with 2a merged.
- All other domains remain on mocks and still work.
