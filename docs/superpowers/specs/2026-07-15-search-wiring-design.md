# Slice 4 — Search wiring design

## Context

BeatzClik's frontend SPA (`Frontend/`) is a finished, mock-backed functional spec being wired to the
real Quarkus backend incrementally, with no visual change. Prior slices: **1** (foundation + auth +
catalog, PR #121), **2a** (backend catalog batch resolve, PR #123), **2b** (collection + library,
PR #124), **3a/3b** (commerce cart/checkout/orders, PRs #126/#127).

This slice wires `Frontend/src/routes/search.tsx` — the only remaining route still importing directly
from `Frontend/src/lib/mock-data.ts` for its core content (`tracks`, `artists`, `albums`, `playlists`,
`browseCategories`). The backend's search module is feature-complete (`WU-SRCH-1`, `status: done`):
`GET /v1/search?q=` already exists on `catalog/adapter/in/rest/PublicCatalogResource.java` and returns
`SearchResultsView(tracks, artists, albums, playlists, topResult?)` — a shape that maps almost 1:1 onto
the mock's local-filter output. This is a pure frontend slice: no backend work is required.

## Decisions (confirmed with the user during brainstorming)

1. **Debounce the query, not the input.** The mock searches on every keystroke for free (local array
   filtering, zero latency). A real backend call on every keystroke would spam `/v1/search`. There is
   no existing debounce utility in this codebase (`grep` for `debounce`/`useDebounce` found nothing) —
   add a small local `useDebouncedValue(value, delayMs)` hook (no new dependency). The URL's `q` param
   and the input's displayed value update instantly on every keystroke (unchanged from today); only the
   value handed to the search query is debounced. Delay: **300ms**.
2. **Plain gated `useQuery`, not the loader + `useSuspenseQuery` pattern from slice 1.** That pattern
   fits one-fetch-per-route-entry reads (a track/album/artist page loaded once per navigation). Search's
   query key changes continuously as the user types, which doesn't fit a route-loader prefetch model.
   Use `useQuery({ ...searchQuery(debouncedNeedle), enabled: !!debouncedNeedle })` directly in the
   component instead.
3. **`browseCategoriesQuery()` is reused verbatim.** It was already wired in slice 1 for the home page;
   the "Browse all" grid (shown when the search box is empty) needs no new code.
4. **`topResult` resolves by id-lookup into the four hydrated arrays, not by trusting the wire
   `payload` field.** The backend's `TopResultView` is `{entityType, entityId, title, subtitle,
   payload: Map<String, Object>}` and — a real fidelity upgrade over the mock's artist-only assumption
   — `entityType` can be any of `TRACK`, `ARTIST`, `ALBUM`, `PLAYLIST` (confirmed by reading
   `SearchService.java`: `topResult` is mapped from the same `SearchHit` type that populates the four
   category lists, and the search index's `EntityType` enum also includes `STORE_ITEM`, `PODCAST`,
   `EVENT` — kinds this endpoint does not hydrate). Rather than parse the loosely-typed `payload` map,
   the frontend looks up `topResult.entityId` inside whichever of the four already-mapped
   arrays matches `topResult.entityType`. If the lookup finds nothing (only possible if the backend's
   top result is one of the un-hydrated kinds — a pre-existing backend-side gap, not touched by this
   slice), the Top Result card is omitted rather than the page crashing.
5. **The "buy" button needs no new wiring.** `buyTrack` already calls `useCart().addItem`, which has
   been the real, server-backed cart since slice 3. Nothing changes here except that the `Track` objects
   it's called with now come from the real backend instead of the mock array.

## Design

### Data flow

`search.tsx` keeps its existing URL-driven `q` search param and the input's instant local echo
(`value={q || ''}`, `onChange` navigates immediately) — no change to that part of the UX. A new
`useDebouncedValue` hook (in `Frontend/src/hooks/use-debounced-value.ts`, a new small file — no existing
`hooks/` convention to follow since this is the first cross-cutting hook of this kind, so this
introduces that directory) takes the raw `needle` and returns a value that only updates 300ms after the
input stops changing. The component calls
`useQuery({ ...searchQuery(debouncedNeedle), enabled: !!debouncedNeedle })`; while `needle` is truthy
but `debouncedNeedle` hasn't caught up yet (mid-debounce) or the query is in flight, a lightweight
loading state renders in place of the results section, using the existing
`Frontend/src/components/ui/skeleton.tsx` component (already present in the codebase, not built by
this slice) for skeleton rows matching the existing song-row layout.

### Response mapping

New `Frontend/src/lib/api/queries/search.ts`, mirroring the existing `catalog.ts`/`collection.ts`/
`commerce.ts` convention (wire-type interfaces, pure mapper functions, a `queryOptions()`-returning
query function):

- `searchQuery(q: string)` → `queryOptions({ queryKey: ['search', q], queryFn: ... })` fetching
  `GET /search?q=${encodeURIComponent(q)}` and mapping the response through the **existing** `toTrack`,
  `toArtist`, `toAlbum`, `toPlaylist` functions already in `Frontend/src/lib/api/mappers.ts` — no new
  per-entity mapping logic, since `SearchResultsView`'s `tracks`/`artists`/`albums`/`playlists` fields
  are the same `TrackView`/`ArtistView`/`AlbumView`/`PlaylistView` wire shapes already consumed
  elsewhere.
- A `SearchTopResult` app-level type: `{ kind: 'track' | 'artist' | 'album' | 'playlist'; entity: Track
  | Artist | Album | Playlist } | undefined`, produced by a pure function that takes the wire
  `topResult` plus the four already-mapped arrays and performs the id-lookup described in Decision 4.

### Component changes

`search.tsx`: replace the mock imports (`tracks, artists, albums, playlists, browseCategories` from
`lib/mock-data`) with `useQuery(searchQuery(debouncedNeedle))` and `useQuery(browseCategoriesQuery())`.
Replace the local `matchTracks`/`matchArtists`/`matchAlbums`/`matchPlaylists` filtering (which today
does client-side substring matching over the full mock arrays) with the query result's arrays directly
— filtering now happens server-side. `topResult` becomes the resolved `SearchTopResult` from the new
mapping function instead of `matchArtists[0] ?? null`; the existing artist-shaped top-result card stays
for `kind === 'artist'`, and three new small render branches are added for `track`/`album`/`playlist`
top results, each reusing the existing `Card`/`CardImage`/`CardContent`/`CardTitle`/`CardSubtitle`
components already used later on the same page for the Albums/Playlists sections (so no new visual
components, just new usages of existing ones in the top-result slot). `buyTrack` is unchanged.

### Testing

- Unit: `search.ts`'s wire-to-app mapping (all four categories, plus the `topResult` id-lookup function
  for all four entity kinds and the not-found/omit case) and `useDebouncedValue`'s timing behavior
  (using Vitest's fake timers — `vi.useFakeTimers()`/`vi.advanceTimersByTime()`).
- Live QA: type a query and confirm the network call fires ~300ms after the last keystroke (not on
  every keystroke); confirm all four result categories render from real data; confirm a non-artist top
  result renders correctly if the seed data happens to rank one to the top (try a few different query
  terms if the first doesn't surface one); confirm "Browse all" still works when the box is empty;
  confirm buy-to-cart still works from a search result.

## Explicitly out of scope

- Any backend change — `WU-SRCH-1` is done; this slice only consumes what exists.
- Hydrating `STORE_ITEM`/`PODCAST`/`EVENT` search results — the backend doesn't return them from this
  endpoint today; fixing that (if desired) is a backend follow-up, not part of this slice.
- Search history, recent searches, or autocomplete suggestions — no mock UI exists for any of these.
