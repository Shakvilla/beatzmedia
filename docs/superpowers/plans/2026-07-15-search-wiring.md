# Slice 4 — Search Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `Frontend/src/routes/search.tsx`'s mock-array local filtering with the real backend
search endpoint (`GET /v1/search?q=`), debounced and query-cached, with zero visual change to the
existing search UI.

**Architecture:** A new debounce hook decouples the instant URL-driven input from the query key. A new
`search.ts` query-layer file (mirroring `catalog.ts`/`collection.ts`/`commerce.ts`) fetches and maps the
wire response through the existing mapper functions, and resolves `topResult` by id-lookup into the
four hydrated arrays rather than trusting the wire `payload` field. `search.tsx` is rewired to consume
these via a plain gated `useQuery` (not the loader+`useSuspenseQuery` pattern), with a skeleton loading
state and no other structural change.

**Tech Stack:** React 19, TanStack Router + Query 5, TypeScript, Vitest + Testing Library, Tailwind.

## Global Constraints

- Node 22 required for all frontend commands: prefix with
  `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH"`.
- **Typecheck with `npx tsc -b --noEmit`, never bare `npx tsc --noEmit`.** This repo uses TypeScript
  project references (root `tsconfig.json` has `"files": []`); the bare form silently checks zero files
  and reports success regardless of real errors.
- Test files using JSX must be named `*.test.tsx` — `vitest.config.ts`'s `include` already covers both
  `*.test.ts` and `*.test.tsx` (widened in slice 3), no config change needed here since this slice's new
  test files (`use-debounced-value.test.ts`, `search.test.ts`) are plain `.ts`.
- Branch `feat/frontend-search-wiring` already exists, created off `origin/master`, with the approved
  spec committed at `75f93ce` (`docs/superpowers/specs/2026-07-15-search-wiring-design.md`). All tasks
  in this plan commit to that branch.
- No backend changes in this slice — `GET /v1/search?q=` (`PublicCatalogResource.java:165`, full path
  `/v1/search`) is done and unchanged.
- `bash backend/scripts/verify.sh` / `smoke.sh` are backend-only and irrelevant to this slice; the
  verification step for this plan is frontend typecheck + `vitest run` + live QA, run by the user (not
  the agent — IntelliJ JPS races frontend builds too, per established practice).

---

### Task 1: `useDebouncedValue` hook

**Files:**
- Create: `Frontend/src/hooks/use-debounced-value.ts`
- Test: `Frontend/src/hooks/use-debounced-value.test.ts`

**Interfaces:**
- Produces: `useDebouncedValue<T>(value: T, delayMs: number): T` — consumed by Task 3
  (`search.tsx`).

- [ ] **Step 1: Write the failing test**

```typescript
// Frontend/src/hooks/use-debounced-value.test.ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useDebouncedValue } from './use-debounced-value'

describe('useDebouncedValue', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('returns the initial value immediately', () => {
    const { result } = renderHook(() => useDebouncedValue('a', 300))
    expect(result.current).toBe('a')
  })

  it('does not update before the delay elapses', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: 'a' },
    })

    rerender({ value: 'ab' })
    act(() => vi.advanceTimersByTime(299))

    expect(result.current).toBe('a')
  })

  it('updates to the latest value once the delay elapses', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: 'a' },
    })

    rerender({ value: 'ab' })
    act(() => vi.advanceTimersByTime(300))

    expect(result.current).toBe('ab')
  })

  it('resets the timer on each intermediate change, keeping only the final value', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: 'a' },
    })

    rerender({ value: 'ab' })
    act(() => vi.advanceTimersByTime(150))
    rerender({ value: 'abc' })
    act(() => vi.advanceTimersByTime(150))
    // Only 150ms elapsed since the 'abc' change — the 'ab' timer was cancelled by the rerender.
    expect(result.current).toBe('a')

    act(() => vi.advanceTimersByTime(150))
    expect(result.current).toBe('abc')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend && npx vitest run src/hooks/use-debounced-value.test.ts`
Expected: FAIL — `Failed to resolve import "./use-debounced-value"`.

- [ ] **Step 3: Write the implementation**

```typescript
// Frontend/src/hooks/use-debounced-value.ts
import { useEffect, useState } from 'react'

export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])

  return debounced
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend && npx vitest run src/hooks/use-debounced-value.test.ts`
Expected: PASS (4 tests).

- [ ] **Step 5: Typecheck**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend && npx tsc -b --noEmit`
Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/hooks/use-debounced-value.ts Frontend/src/hooks/use-debounced-value.test.ts
git commit -m "feat(frontend): add useDebouncedValue hook"
```

---

### Task 2: `search.ts` query layer (fetch, map, resolve top result)

**Files:**
- Create: `Frontend/src/lib/api/queries/search.ts`
- Test: `Frontend/src/lib/api/queries/search.test.ts`

**Interfaces:**
- Consumes: `apiFetch<T>(path, options?)` from `../client`; `toTrack`, `toArtist`, `toAlbum`,
  `toPlaylist` and their `TrackWire`/`ArtistWire`/`AlbumWire`/`PlaylistWire` types from `../mappers`
  (all pre-existing, unchanged).
- Produces:
  - `export interface SearchResultsData { tracks: Track[]; artists: Artist[]; albums: Album[]; playlists: Playlist[]; topResult: SearchTopResult }`
  - `export type SearchTopResult = { kind: 'track'; entity: Track } | { kind: 'artist'; entity: Artist } | { kind: 'album'; entity: Album } | { kind: 'playlist'; entity: Playlist } | undefined`
  - `export function searchQuery(q: string)` → `queryOptions(...)` with `queryKey: ['search', q]`,
    consumed by Task 3.
  - `export function resolveTopResult(wire: TopResultWire | null, tracks: Track[], artists: Artist[], albums: Album[], playlists: Playlist[]): SearchTopResult` — exported for direct unit testing.

- [ ] **Step 1: Write the failing test**

```typescript
// Frontend/src/lib/api/queries/search.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { apiFetch } from '../client'
import { searchQuery, resolveTopResult } from './search'
import type { Track, Artist, Album, Playlist } from '../../../types'

vi.mock('../client', () => ({ apiFetch: vi.fn() }))

const ctx = {} as any

const wireTrack = {
  id: 't1', title: 'Second Sermon', artistId: 'a1', artistName: 'Black Sherif', albumId: null,
  albumTitle: null, duration: 200, image: 'track.jpg', ownership: 'for-sale',
  price: { amount: 5, currency: 'GHS' }, plays: 100, audioUrl: null, credits: null, quality: null,
  year: 2022,
}
const wireArtist = {
  id: 'a1', name: 'Black Sherif', image: 'artist.jpg', coverImage: null, verified: true,
  monthlyListeners: 1000, followers: 500, bio: null, location: null, genres: null,
}
const wireAlbum = {
  id: 'al1', title: 'The Villain I Never Was', artistId: 'a1', artistName: 'Black Sherif', year: 2022,
  coverImage: 'album.jpg', genres: null, trackIds: ['t1'], tracks: null,
}
const wirePlaylist = {
  id: 'p1', title: 'Afrobeats Mix', description: null, creator: 'BeatzClik', creatorAvatar: null,
  image: 'playlist.jpg', isPublic: true, followers: null, trackIds: ['t1'], tracks: null,
}

describe('searchQuery', () => {
  beforeEach(() => vi.mocked(apiFetch).mockReset())

  it('fetches /search?q= and maps all four categories, with no top result', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      tracks: [wireTrack], artists: [wireArtist], albums: [wireAlbum], playlists: [wirePlaylist],
      topResult: null,
    })

    const result = await searchQuery('sherif').queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/search?q=sherif')
    expect(result.tracks).toHaveLength(1)
    expect(result.artists).toHaveLength(1)
    expect(result.albums).toHaveLength(1)
    expect(result.playlists).toHaveLength(1)
    expect(result.topResult).toBeUndefined()
  })

  it('URL-encodes the query', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ tracks: [], artists: [], albums: [], playlists: [], topResult: null })

    await searchQuery('black sherif').queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/search?q=black%20sherif')
  })

  it('resolves a TRACK topResult by id-lookup into the mapped tracks array', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      tracks: [wireTrack], artists: [], albums: [], playlists: [],
      topResult: { entityType: 'TRACK', entityId: 't1', title: 'Second Sermon', subtitle: 'Black Sherif', payload: {} },
    })

    const result = await searchQuery('sherif').queryFn!(ctx)

    expect(result.topResult).toEqual({ kind: 'track', entity: result.tracks[0] })
  })
})

describe('resolveTopResult (pure)', () => {
  const tracks: Track[] = [{
    id: 't1', title: 'Second Sermon', artistId: 'a1', artistName: 'Black Sherif', duration: 200,
    image: 'track.jpg', ownership: 'for-sale', price: { amount: 5, currency: 'GHS' },
  }]
  const artists: Artist[] = [{ id: 'a1', name: 'Black Sherif', image: 'artist.jpg' }]
  const albums: Album[] = [{ id: 'al1', title: 'The Villain I Never Was', artistId: 'a1', artistName: 'Black Sherif', year: 2022, coverImage: 'album.jpg', trackIds: ['t1'] }]
  const playlists: Playlist[] = [{ id: 'p1', title: 'Afrobeats Mix', creator: 'BeatzClik', image: 'playlist.jpg', isPublic: true, trackIds: ['t1'] }]

  it('returns undefined when the wire topResult is null', () => {
    expect(resolveTopResult(null, tracks, artists, albums, playlists)).toBeUndefined()
  })

  it('resolves ARTIST', () => {
    const wire = { entityType: 'ARTIST', entityId: 'a1', title: 'Black Sherif', subtitle: '', payload: {} }
    expect(resolveTopResult(wire, tracks, artists, albums, playlists)).toEqual({ kind: 'artist', entity: artists[0] })
  })

  it('resolves ALBUM', () => {
    const wire = { entityType: 'ALBUM', entityId: 'al1', title: 'The Villain I Never Was', subtitle: '', payload: {} }
    expect(resolveTopResult(wire, tracks, artists, albums, playlists)).toEqual({ kind: 'album', entity: albums[0] })
  })

  it('resolves PLAYLIST', () => {
    const wire = { entityType: 'PLAYLIST', entityId: 'p1', title: 'Afrobeats Mix', subtitle: '', payload: {} }
    expect(resolveTopResult(wire, tracks, artists, albums, playlists)).toEqual({ kind: 'playlist', entity: playlists[0] })
  })

  it('returns undefined for an un-hydrated kind (e.g. STORE_ITEM) rather than crashing', () => {
    const wire = { entityType: 'STORE_ITEM', entityId: 'si1', title: 'Merch', subtitle: '', payload: {} }
    expect(resolveTopResult(wire, tracks, artists, albums, playlists)).toBeUndefined()
  })

  it('returns undefined when the entityId is not found in the matching array', () => {
    const wire = { entityType: 'TRACK', entityId: 'does-not-exist', title: 'Ghost', subtitle: '', payload: {} }
    expect(resolveTopResult(wire, tracks, artists, albums, playlists)).toBeUndefined()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend && npx vitest run src/lib/api/queries/search.test.ts`
Expected: FAIL — `Failed to resolve import "./search"`.

- [ ] **Step 3: Write the implementation**

```typescript
// Frontend/src/lib/api/queries/search.ts
import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import {
  toArtist,
  toTrack,
  toAlbum,
  toPlaylist,
  type ArtistWire,
  type TrackWire,
  type AlbumWire,
  type PlaylistWire,
} from '../mappers'
import type { Track, Artist, Album, Playlist } from '../../../types'

export interface TopResultWire {
  entityType: string
  entityId: string
  title: string
  subtitle: string
  payload: Record<string, unknown>
}

interface SearchResultsWire {
  tracks: TrackWire[]
  artists: ArtistWire[]
  albums: AlbumWire[]
  playlists: PlaylistWire[]
  topResult: TopResultWire | null
}

export type SearchTopResult =
  | { kind: 'track'; entity: Track }
  | { kind: 'artist'; entity: Artist }
  | { kind: 'album'; entity: Album }
  | { kind: 'playlist'; entity: Playlist }
  | undefined

export interface SearchResultsData {
  tracks: Track[]
  artists: Artist[]
  albums: Album[]
  playlists: Playlist[]
  topResult: SearchTopResult
}

/**
 * The backend's TopResultView.payload is a loosely-typed map and its entityType can include kinds
 * this endpoint doesn't hydrate (STORE_ITEM/PODCAST/EVENT). Resolving by id-lookup into the already-
 * mapped arrays sidesteps parsing payload and degrades safely (omit, don't crash) for those kinds.
 */
export function resolveTopResult(
  wire: TopResultWire | null,
  tracks: Track[],
  artists: Artist[],
  albums: Album[],
  playlists: Playlist[],
): SearchTopResult {
  if (!wire) return undefined
  switch (wire.entityType) {
    case 'TRACK': {
      const entity = tracks.find((t) => t.id === wire.entityId)
      return entity ? { kind: 'track', entity } : undefined
    }
    case 'ARTIST': {
      const entity = artists.find((a) => a.id === wire.entityId)
      return entity ? { kind: 'artist', entity } : undefined
    }
    case 'ALBUM': {
      const entity = albums.find((a) => a.id === wire.entityId)
      return entity ? { kind: 'album', entity } : undefined
    }
    case 'PLAYLIST': {
      const entity = playlists.find((p) => p.id === wire.entityId)
      return entity ? { kind: 'playlist', entity } : undefined
    }
    default:
      return undefined
  }
}

export function searchQuery(q: string) {
  return queryOptions({
    queryKey: ['search', q],
    queryFn: async () => {
      const wire = await apiFetch<SearchResultsWire>(`/search?q=${encodeURIComponent(q)}`)
      const tracks = wire.tracks.map(toTrack)
      const artists = wire.artists.map(toArtist)
      const albums = wire.albums.map(toAlbum)
      const playlists = wire.playlists.map(toPlaylist)
      return {
        tracks,
        artists,
        albums,
        playlists,
        topResult: resolveTopResult(wire.topResult, tracks, artists, albums, playlists),
      }
    },
  })
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend && npx vitest run src/lib/api/queries/search.test.ts`
Expected: PASS (9 tests).

- [ ] **Step 5: Typecheck**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend && npx tsc -b --noEmit`
Expected: no errors. If the `resolveTopResult` pure-test fixtures (plain object literals typed as
`Track`/`Artist`/`Album`/`Playlist`) fail structural typing because of missing optional fields, that's
expected — TypeScript accepts object literals missing `?`-optional fields already (all fields left out
above are optional on their interfaces), so this should pass as written.

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/lib/api/queries/search.ts Frontend/src/lib/api/queries/search.test.ts
git commit -m "feat(frontend): search query layer — fetch, map, resolve top result"
```

---

### Task 3: Rewire `search.tsx` to the real backend

**Files:**
- Modify: `Frontend/src/routes/search.tsx` (full rewrite of the mock-consuming parts; JSX structure and
  class names otherwise unchanged)

**Interfaces:**
- Consumes: `searchQuery`, `SearchTopResult` from `../lib/api/queries/search` (Task 2);
  `useDebouncedValue` from `../hooks/use-debounced-value` (Task 1); `browseCategoriesQuery` from
  `../lib/api/queries/catalog` (pre-existing, used since slice 1); `Skeleton` from
  `../components/ui/skeleton` (pre-existing).

This task has no isolated unit test — `search.ts` and `useDebouncedValue` are already covered by Tasks
1–2. This task is verified by typecheck + live QA (Step 3 below and Task 4).

- [ ] **Step 1: Replace the file contents**

```tsx
// Frontend/src/routes/search.tsx
import { createFileRoute, useNavigate, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Search as SearchIcon, X, Check, Play, ShoppingCart } from 'lucide-react'
import { z } from 'zod'
import { cn } from '../utils/cn'
import { usePlayer } from '../features/player/player-context'
import { useCart } from '../features/cart/cart-context'
import { useToast } from '../components/ui/toast-provider'
import { Card, CardContent, CardImage, CardSubtitle, CardTitle } from '../components/ui/card'
import { Skeleton } from '../components/ui/skeleton'
import { ArtistCircle } from '../features/discover/components/artist-circle'
import { browseCategoriesQuery } from '../lib/api/queries/catalog'
import { searchQuery, type SearchTopResult } from '../lib/api/queries/search'
import { useDebouncedValue } from '../hooks/use-debounced-value'
import { formatDuration, formatPrice } from '../lib/format'
import type { Track } from '../types'

const searchSchema = z.object({ q: z.string().optional().catch('') })

export const Route = createFileRoute('/search')({
  validateSearch: searchSchema,
  component: SearchComponent,
})

const FILTERS = ['All', 'Tracks', 'Artists', 'Albums', 'Playlists'] as const
type Filter = (typeof FILTERS)[number]

function SearchComponent() {
  const { q } = Route.useSearch()
  const navigate = useNavigate({ from: Route.fullPath })
  const [filter, setFilter] = useState<Filter>('All')
  const { currentTrack, playQueue } = usePlayer()
  const { addItem } = useCart()
  const { toast } = useToast()

  const needle = (q ?? '').trim()
  const debouncedNeedle = useDebouncedValue(needle, 300)

  const { data: browseCategories } = useQuery(browseCategoriesQuery())
  const { data: results, isLoading } = useQuery({
    ...searchQuery(debouncedNeedle),
    enabled: !!debouncedNeedle,
  })

  const searching = needle !== debouncedNeedle || (!!debouncedNeedle && isLoading)
  const noResults = !!results && !results.tracks.length && !results.artists.length && !results.albums.length && !results.playlists.length
  const show = (section: Filter) => filter === 'All' || filter === section
  const topResult = results?.topResult

  const buyTrack = (track: Track) => {
    addItem({ id: `track:${track.id}`, kind: 'track', title: track.title, subtitle: track.artistName, image: track.image, price: track.price ?? { amount: 0, currency: 'GHS' } })
    toast(`"${track.title}" added to cart`, 'success')
  }

  return (
    <div className="flex flex-col gap-8 -mt-6">
      {/* Search input */}
      <div className="relative group max-w-4xl">
        <SearchIcon className="absolute left-6 top-1/2 -translate-y-1/2 text-gray-400" size={24} />
        <input
          type="text"
          value={q || ''}
          onChange={(e) => navigate({ search: { q: e.target.value || undefined } })}
          placeholder="What do you want to listen to?"
          className="w-full h-16 pl-16 pr-14 bg-white dark:bg-beatz-dark-surface-2 text-beatz-dark-bg dark:text-white rounded-full font-bold text-lg border-2 border-transparent focus:border-beatz-green outline-none transition-all shadow-xl"
        />
        {q && (
          <button onClick={() => navigate({ search: {} })} className="absolute right-6 top-1/2 -translate-y-1/2 text-gray-400 hover:text-beatz-dark-bg dark:hover:text-white transition-colors">
            <X size={24} />
          </button>
        )}
      </div>

      {!needle ? (
        /* Browse */
        <div className="flex flex-col gap-6 animate-in fade-in duration-500">
          <h2 className="text-title text-beatz-dark-bg dark:text-white">Browse all</h2>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
            {(browseCategories ?? []).map((category) => (
              <Link
                key={category.id}
                to="/search"
                search={{ q: category.title }}
                className={cn('relative overflow-hidden rounded-xl p-4 aspect-[2/1] flex items-start shadow-md hover:scale-[1.02] transition-transform duration-300 group', category.colorClass)}
              >
                <h3 className="text-white font-bold text-lg md:text-xl z-10 relative">{category.title}</h3>
                <div className="absolute -right-3 -bottom-3 w-16 h-16 bg-black/20 rounded-lg rotate-[25deg] shadow-lg group-hover:scale-110 transition-transform duration-300" />
              </Link>
            ))}
          </div>
        </div>
      ) : searching || !results ? (
        <SearchResultsSkeleton />
      ) : noResults ? (
        <div className="flex flex-col items-center gap-2 py-24 text-center">
          <h2 className="text-title text-beatz-dark-bg dark:text-white">No results for "{q}"</h2>
          <p className="text-gray-500 dark:text-gray-300">Try a different spelling or search for something else.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-10 animate-in slide-in-from-bottom-4 duration-500">
          {/* Filters */}
          <div className="flex items-center gap-2 flex-wrap">
            {FILTERS.map((f) => (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className={cn(
                  'px-4 py-2 rounded-full text-sm font-bold transition-colors',
                  filter === f
                    ? 'bg-beatz-dark-bg dark:bg-white text-white dark:text-black'
                    : 'bg-white dark:bg-beatz-dark-surface-2 text-beatz-dark-bg dark:text-white hover:bg-gray-100 dark:hover:bg-beatz-dark-surface-3',
                )}
              >
                {f}
              </button>
            ))}
          </div>

          {/* Top result + tracks */}
          {show('Tracks') && (results.tracks.length > 0 || topResult) && (
            <div className="grid grid-cols-1 lg:grid-cols-5 gap-8">
              {filter === 'All' && topResult && (
                <div className="lg:col-span-2 flex flex-col gap-4">
                  <h3 className="text-title text-beatz-dark-bg dark:text-white">Top result</h3>
                  <TopResultCard top={topResult} />
                </div>
              )}

              {results.tracks.length > 0 && (
                <div className={cn('flex flex-col gap-4', filter === 'All' && topResult ? 'lg:col-span-3' : 'lg:col-span-5')}>
                  <h3 className="text-title text-beatz-dark-bg dark:text-white">Songs</h3>
                  <div className="flex flex-col">
                    {(filter === 'All' ? results.tracks.slice(0, 5) : results.tracks).map((track, index) => (
                      <div key={track.id} onClick={() => playQueue(results.tracks, index)} className="flex items-center gap-4 p-2 rounded-md hover:bg-gray-100 dark:hover:bg-white/5 transition-colors group cursor-pointer">
                        <div className="relative w-12 h-12 rounded overflow-hidden shrink-0 bg-beatz-light-surface-2 dark:bg-beatz-dark-surface-3">
                          <img src={track.image} alt={track.title} className="w-full h-full object-cover" />
                          <div className="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"><Play size={16} className="text-white" fill="currentColor" /></div>
                        </div>
                        <div className="flex flex-col flex-1 min-w-0">
                          <Link
                            to="/track/$trackId"
                            params={{ trackId: track.id }}
                            onClick={(e) => e.stopPropagation()}
                            className={cn('font-bold truncate hover:underline w-fit max-w-full', currentTrack?.id === track.id ? 'text-beatz-green' : 'text-beatz-dark-bg dark:text-white')}
                          >
                            {track.title}
                          </Link>
                          <span className="text-sm text-gray-500 dark:text-gray-300 truncate">{track.artistName}</span>
                        </div>
                        <div className="flex items-center gap-4 shrink-0">
                          {track.ownership === 'owned' ? (
                            <span className="flex items-center gap-1 text-[10px] font-bold text-[#f6c644] bg-[#f6c644]/10 px-2 py-0.5 rounded uppercase tracking-wider"><Check size={10} strokeWidth={3} /> Owned</span>
                          ) : track.ownership === 'free' ? (
                            <span className="text-[10px] font-bold text-beatz-green bg-beatz-green/10 px-2 py-0.5 rounded uppercase">Free</span>
                          ) : (
                            <button onClick={(e) => { e.stopPropagation(); buyTrack(track) }} className="text-[10px] font-bold text-beatz-green border border-beatz-green/30 px-2.5 py-1 rounded hover:bg-beatz-green/10 flex items-center gap-1 transition-colors">
                              <ShoppingCart size={11} /> {formatPrice(track.price)}
                            </button>
                          )}
                          <span className="text-sm text-gray-500 dark:text-gray-300 font-mono w-10 text-right tabular-nums">{formatDuration(track.duration)}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Artists */}
          {show('Artists') && results.artists.length > 0 && (
            <div className="flex flex-col gap-6">
              <h3 className="text-title text-beatz-dark-bg dark:text-white">Artists</h3>
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-6">
                {results.artists.map((artist) => <ArtistCircle key={artist.id} artist={artist} />)}
              </div>
            </div>
          )}

          {/* Albums */}
          {show('Albums') && results.albums.length > 0 && (
            <div className="flex flex-col gap-6">
              <h3 className="text-title text-beatz-dark-bg dark:text-white">Albums</h3>
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-6">
                {results.albums.map((album) => (
                  <Card key={album.id} to={`/album/${album.id}`}>
                    <CardImage src={album.coverImage} alt={album.title} />
                    <CardContent className="mt-2"><CardTitle>{album.title}</CardTitle><CardSubtitle>{album.artistName} • {album.year}</CardSubtitle></CardContent>
                  </Card>
                ))}
              </div>
            </div>
          )}

          {/* Playlists */}
          {show('Playlists') && results.playlists.length > 0 && (
            <div className="flex flex-col gap-6">
              <h3 className="text-title text-beatz-dark-bg dark:text-white">Playlists</h3>
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-6">
                {results.playlists.map((playlist) => (
                  <Card key={playlist.id} to={`/playlist/${playlist.id}`}>
                    <CardImage src={playlist.image} alt={playlist.title} />
                    <CardContent className="mt-2"><CardTitle>{playlist.title}</CardTitle><CardSubtitle>By {playlist.creator}</CardSubtitle></CardContent>
                  </Card>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

const TOP_CARD_CLASS = 'p-8 rounded-2xl bg-white dark:bg-white/5 hover:bg-gray-50 dark:hover:bg-white/10 border border-gray-100 dark:border-transparent'

/** Artists keep their distinct circular-avatar treatment; track/album/playlist share one card shape. */
function TopResultCard({ top }: { top: NonNullable<SearchTopResult> }) {
  if (top.kind === 'artist') {
    return (
      <Link to="/artist/$artistId" params={{ artistId: top.entity.id }} className={cn(TOP_CARD_CLASS, 'transition-colors group cursor-pointer flex flex-col gap-4')}>
        <div className="w-28 h-28 rounded-full overflow-hidden shadow-xl">
          <img src={top.entity.image} alt={top.entity.name} className="w-full h-full object-cover" />
        </div>
        <h4 className="text-3xl font-bold text-beatz-dark-bg dark:text-white">{top.entity.name}</h4>
        <span className="text-xs font-bold uppercase tracking-widest text-gray-500 dark:text-gray-300">Artist</span>
      </Link>
    )
  }

  const card =
    top.kind === 'track'
      ? { to: `/track/${top.entity.id}`, image: top.entity.image, title: top.entity.title, subtitle: top.entity.artistName }
      : top.kind === 'album'
        ? { to: `/album/${top.entity.id}`, image: top.entity.coverImage, title: top.entity.title, subtitle: `${top.entity.artistName} • ${top.entity.year}` }
        : { to: `/playlist/${top.entity.id}`, image: top.entity.image, title: top.entity.title, subtitle: `By ${top.entity.creator}` }

  return (
    <Card to={card.to} className={TOP_CARD_CLASS}>
      <CardImage src={card.image} alt={card.title} />
      <CardContent className="mt-2">
        <CardTitle>{card.title}</CardTitle>
        <CardSubtitle>{card.subtitle}</CardSubtitle>
      </CardContent>
    </Card>
  )
}

function SearchResultsSkeleton() {
  return (
    <div className="flex flex-col gap-3 pt-4" aria-hidden="true">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="flex items-center gap-4 p-2">
          <Skeleton className="w-12 h-12 rounded" />
          <div className="flex flex-col gap-2 flex-1">
            <Skeleton className="h-4 w-1/3" />
            <Skeleton className="h-3 w-1/5" />
          </div>
        </div>
      ))}
    </div>
  )
}
```

- [ ] **Step 2: Run the full test suite to confirm no regressions**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend && npx vitest run`
Expected: all suites PASS, including the two new files from Tasks 1–2.

- [ ] **Step 3: Typecheck**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend && npx tsc -b --noEmit`
Expected: no errors. This is the step that proves the ternary-chain narrowing of `results` (from
`SearchResultsData | undefined` down to `SearchResultsData` once past the `searching || !results`
branch) actually typechecks — TypeScript's control-flow analysis narrows through `||`/`!` combinations
in ternary conditions the same way it already does in `checkout.complete.tsx`'s
`if (isLoading || !order) return AuthorizingState` guard. If it does not narrow cleanly here, replace
the final ternary branch with an early-return guard (`if (!results) return null` before the JSX) instead
of fighting the ternary — do not use a non-null assertion (`results!`) to route around a real narrowing
gap.

- [ ] **Step 4: Commit**

```bash
git add Frontend/src/routes/search.tsx
git commit -m "feat(frontend): wire search route to the real backend (slice 4)"
```

---

### Task 4: Live QA, verify, open PR

**Files:** none (verification-only task).

- [ ] **Step 1: Start the frontend dev server and the backend stack**

Backend: `cd backend && ./mvnw quarkus:dev` (or `docker compose up` from repo root if already
configured for this environment).
Frontend: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd Frontend && npm run dev`.
Confirm `Frontend/vite.config.ts`'s dev proxy target is `127.0.0.1:8080` (not `localhost:8080` — an
established gotcha in this repo, `::1` resolution collides with an unrelated app).

- [ ] **Step 2: Live QA against the running stack**

Using the browser tools, log in, then on the Search screen:
- Confirm "Browse all" renders from the real `/browse-categories` endpoint when the search box is empty
  (unchanged from slice 1 — regression check).
- Type a query one character at a time and confirm via the network panel that only one `/v1/search?q=`
  request fires, roughly 300ms after the last keystroke (not on every keystroke).
- Confirm the skeleton rows render during the debounce window and while the request is in flight, then
  are replaced by real results.
- Confirm all four result categories (Songs, Artists, Albums, Playlists) render from real backend data,
  and each filter chip (Tracks/Artists/Albums/Playlists) narrows correctly.
- Try a few different query terms and confirm at least one surfaces a non-artist top result (track,
  album, or playlist) — verifies the id-lookup resolution path for those three kinds, not just the
  pre-existing artist path.
- Confirm the "No results for ..." empty state renders for a query with genuinely no matches.
- Confirm buying a for-sale track from a search result still adds it to the (real, server-backed since
  slice 3) cart.
- Confirm clicking a search result (track/artist/album/playlist, including the top-result card)
  navigates to the correct detail page.

- [ ] **Step 3: Ask the user to run the frontend verification gate**

Per established practice in this repo (IntelliJ JPS races frontend builds when run by the agent), ask
the user to run and report the results of:

```bash
export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH"
cd Frontend
npx tsc -b --noEmit && npx vitest run && npm run build
```

Do not proceed to opening the PR until the user confirms this passed.

- [ ] **Step 4: Open the pull request**

```bash
git push -u origin feat/frontend-search-wiring
gh pr create --title "feat(frontend): wire search to the real backend (slice 4)" --body "$(cat <<'EOF'
## Summary
- Replace `search.tsx`'s mock-array local filtering with the real `GET /v1/search?q=` endpoint.
- New `useDebouncedValue` hook (300ms) decouples the instant URL-driven input from the search query key.
- New `search.ts` query layer maps the wire response through the existing catalog mappers and resolves
  `topResult` by id-lookup into the four hydrated arrays (a fidelity upgrade over the mock's
  artist-only assumption — track/album/playlist top results now render correctly too).
- No backend changes — `WU-SRCH-1` was already done.

## Test plan
- [x] `npx tsc -b --noEmit` clean
- [x] `npx vitest run` — all suites pass, including new `use-debounced-value.test.ts` and `search.test.ts`
- [x] Live QA: debounce timing, all four result categories, non-artist top results, empty state,
      buy-to-cart, navigation from results — see plan Task 4 Step 2
- [ ] User-run frontend verification gate (tsc + vitest + build)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: Update the SDD progress ledger**

Append to `.superpowers/sdd/progress.md`: PLAN COMPLETE for slice 4 (search wiring), PR URL, and note
any live-QA findings that were out of scope (e.g. if a non-artist top result never surfaced during QA
with the current seed data, that is a data-coverage gap, not a code defect — say so explicitly).

---

## Self-Review Notes

- **Spec coverage:** Decision 1 (debounce query not input, 300ms) → Task 1. Decision 2 (plain gated
  `useQuery`) → Task 3. Decision 3 (`browseCategoriesQuery()` reused verbatim) → Task 3, no new code.
  Decision 4 (`topResult` id-lookup, not payload) → Task 2's `resolveTopResult`. Decision 5 (buy button
  needs no new wiring) → Task 3 keeps `buyTrack` unchanged, calling the same `useCart().addItem`.
  Testing section → Tasks 1–2 unit tests + Task 4 live QA. Explicitly-out-of-scope items (no backend
  change, no STORE_ITEM/PODCAST/EVENT hydration, no search history/autocomplete) are honored — nothing
  in this plan touches the backend or adds those features.
- **Placeholder scan:** no TBD/TODO/"add error handling" phrasing; every step has runnable code or an
  exact command with expected output.
- **Type consistency:** `SearchTopResult`/`SearchResultsData` are defined once in Task 2 and consumed
  verbatim (same field names: `kind`, `entity`, `tracks`, `artists`, `albums`, `playlists`, `topResult`)
  in Task 3 — no renamed fields between tasks.
