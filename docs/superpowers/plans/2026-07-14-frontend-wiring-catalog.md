# Frontend Wiring — Slice 1 (Foundation + Auth + Catalog) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the BeatzClik frontend's mock data with real calls to the running Quarkus backend for auth (login/signup/session) and catalog reads (home, artist, album, track), with no visual change.

**Architecture:** A new `src/lib/api/` layer (token storage, a `fetch` wrapper with Bearer auth + typed error parsing, wire→domain mappers, TanStack Query `queryOptions` factories) sits behind TanStack Router `loader`s, so route components keep reading data synchronously via `useSuspenseQuery` against a pre-filled cache. Auth becomes real (JWT in localStorage), replacing the mocked always-signed-in session.

**Tech Stack:** React 19, TanStack Router 1.169 (file-based routes, already generating `routeTree.gen.ts`), TanStack Query 5.100, Vite 6, TypeScript 6, Vitest (new) + jsdom (new) for unit tests. Backend: Quarkus, JAX-RS, JWT (`Authorization: Bearer`), error envelope `{ error: { code, message, field? } }`.

## Global Constraints

- **No visual change.** Every wired screen must render identically to its mock-backed version, using the running backend's dev-seeded data (`R__seed_dev_data.sql`, confirmed present with 2 artists, 2 tracks, 1 album, 1 browse category, 2 playlists).
- **Field names are 1:1 with `Frontend/src/types/index.ts`** on every catalog/auth view confirmed in this plan (`ArtistView`, `AlbumView`, `TrackView`, `BrowseCategoryView`, `AccountView`, `AuthResponse`) — verified directly against the backend Java records, not the provisional `API-CONTRACT.md`.
- **Backend error envelope:** `{ "error": { "code": string, "message": string, "field"?: string } }` (confirmed via `DomainExceptionMapper`).
- **Base URL:** `import.meta.env.VITE_API_URL ?? '/v1'`. In dev this resolves to `/v1`, proxied by Vite to `http://localhost:8080`.
- **Token storage:** JWT in `localStorage` under key `beatzclik-token`, attached as `Authorization: Bearer <token>`.
- **Out of scope for this slice (stays on mocks, untouched):** playback/streaming URLs, ownership (`/me/owned`), library/collection, cart/checkout, studio, admin, podcasts, events (including the "Upcoming shows" section on the artist page, which reads `event-data.ts`), live search, notifications, and the home page's "Made for you" (playlists) and "Popular artists" sections (no backend list-all endpoint exists for either — confirmed against `PublicCatalogResource`).
- **No `pendingComponent` is added.** TanStack Router's default behavior — block navigation until the `loader` resolves, keeping the previous screen visible in the meantime — achieves "no visual change" more directly than a new skeleton/spinner would (the design doc's R3 flagged a skeleton as a fallback option, not a requirement). If manual verification in Task 13 finds the wait noticeable on a real network, add a `pendingComponent` then rather than pre-building one against an untested latency assumption.
- **Social login (`POST /v1/auth/social`) is out of scope.** The frontend has no real OAuth SDK integration to obtain a provider token, so this slice does not wire it. The social buttons show an info toast instead of pretending to sign in.
- Follow the codebase's minimal-comment style: only comment non-obvious *why*, not *what*.

---

### Task 1: Test tooling (Vitest + jsdom)

**Files:**
- Modify: `Frontend/package.json`
- Create: `Frontend/vitest.config.ts`
- Create: `Frontend/src/lib/smoke.test.ts` (deleted at the end of this task once tooling is proven — see Step 5)

**Interfaces:**
- Produces: `npm test` script; a Vitest environment (`jsdom`) available to every later task's `*.test.ts` file.

- [ ] **Step 1: Add Vitest + jsdom as dev dependencies**

Run:
```bash
cd "Frontend" && npm install -D vitest@^3 jsdom@^25
```

- [ ] **Step 2: Add the `test` script to `package.json`**

In `Frontend/package.json`, add to `"scripts"`:
```json
"test": "vitest run"
```

- [ ] **Step 3: Create `Frontend/vitest.config.ts`**

```ts
import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts'],
  },
})
```

- [ ] **Step 4: Write a throwaway smoke test to prove the toolchain works**

Create `Frontend/src/lib/smoke.test.ts`:
```ts
import { describe, it, expect } from 'vitest'

describe('vitest smoke test', () => {
  it('runs and can see jsdom localStorage', () => {
    localStorage.setItem('x', '1')
    expect(localStorage.getItem('x')).toBe('1')
  })
})
```

Run: `cd "Frontend" && npm test`
Expected: `1 passed` (1 test file, 1 test).

- [ ] **Step 5: Delete the smoke test (its job — proving the toolchain — is done)**

```bash
cd "Frontend" && rm src/lib/smoke.test.ts
```

- [ ] **Step 6: Commit**

```bash
cd "Frontend" && git add package.json package-lock.json vitest.config.ts
git commit -m "chore(frontend): add Vitest + jsdom test tooling"
```

---

### Task 2: Token storage

**Files:**
- Create: `Frontend/src/lib/api/token.ts`
- Test: `Frontend/src/lib/api/token.test.ts`

**Interfaces:**
- Produces: `getToken(): string | null`, `setToken(token: string): void`, `clearToken(): void` — used by Task 3 (`client.ts`) and Task 8 (`auth-context.tsx`).

- [ ] **Step 1: Write the failing test**

Create `Frontend/src/lib/api/token.test.ts`:
```ts
import { describe, it, expect, beforeEach } from 'vitest'
import { getToken, setToken, clearToken } from './token'

describe('token storage', () => {
  beforeEach(() => localStorage.clear())

  it('returns null when no token is stored', () => {
    expect(getToken()).toBeNull()
  })

  it('stores and retrieves a token', () => {
    setToken('abc.def.ghi')
    expect(getToken()).toBe('abc.def.ghi')
  })

  it('clears a stored token', () => {
    setToken('abc.def.ghi')
    clearToken()
    expect(getToken()).toBeNull()
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd "Frontend" && npx vitest run src/lib/api/token.test.ts`
Expected: FAIL — `Failed to resolve import "./token"`.

- [ ] **Step 3: Implement `token.ts`**

Create `Frontend/src/lib/api/token.ts`:
```ts
const TOKEN_KEY = 'beatzclik-token'

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token)
  } catch {
    /* storage unavailable (e.g. private mode) — session just won't persist */
  }
}

export function clearToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* ignore */
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd "Frontend" && npx vitest run src/lib/api/token.test.ts`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
cd "Frontend" && git add src/lib/api/token.ts src/lib/api/token.test.ts
git commit -m "feat(frontend): JWT token storage over localStorage"
```

---

### Task 3: API error type + fetch client

**Files:**
- Create: `Frontend/src/lib/api/errors.ts`
- Create: `Frontend/src/lib/api/client.ts`
- Test: `Frontend/src/lib/api/client.test.ts`

**Interfaces:**
- Consumes: `getToken`, `clearToken` from `./token` (Task 2).
- Produces:
  - `class ApiError extends Error { status: number; code: string; field?: string }`
  - `apiFetch<T>(path: string, options?: { method?: 'GET'|'POST'|'PATCH'|'DELETE'; body?: unknown; idempotencyKey?: string }): Promise<T>`
  - `setUnauthorizedHandler(handler: () => void): void`
  - Used by Task 5 (`queries/catalog.ts`) and Task 8 (`auth-context.tsx`).

- [ ] **Step 1: Write the failing tests**

Create `Frontend/src/lib/api/client.test.ts`:
```ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { apiFetch, setUnauthorizedHandler } from './client'
import { getToken, setToken } from './token'

function mockFetchOnce(status: number, body: unknown) {
  const fetchMock = vi.fn().mockResolvedValue({
    status,
    ok: status >= 200 && status < 300,
    json: async () => body,
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('apiFetch', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.unstubAllGlobals()
    setUnauthorizedHandler(() => {})
  })

  it('attaches the Bearer token when one is stored', async () => {
    setToken('tok-123')
    const fetchMock = mockFetchOnce(200, { ok: true })

    await apiFetch('/me')

    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers.Authorization).toBe('Bearer tok-123')
  })

  it('sends no Authorization header when signed out', async () => {
    const fetchMock = mockFetchOnce(200, { ok: true })

    await apiFetch('/home')

    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers.Authorization).toBeUndefined()
  })

  it('returns the parsed JSON body on success', async () => {
    mockFetchOnce(200, { id: 'a1', name: 'Black Sherif' })

    const result = await apiFetch<{ id: string; name: string }>('/artists/a1')

    expect(result).toEqual({ id: 'a1', name: 'Black Sherif' })
  })

  it('returns undefined for a 204 response', async () => {
    mockFetchOnce(204, null)

    const result = await apiFetch('/auth/logout', { method: 'POST' })

    expect(result).toBeUndefined()
  })

  it('parses the backend error envelope into an ApiError', async () => {
    mockFetchOnce(404, { error: { code: 'ARTIST_NOT_FOUND', message: 'No such artist' } })

    await expect(apiFetch('/artists/missing')).rejects.toMatchObject({
      status: 404,
      code: 'ARTIST_NOT_FOUND',
      message: 'No such artist',
    })
  })

  it('clears the token and calls the unauthorized handler on 401', async () => {
    setToken('tok-123')
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    mockFetchOnce(401, { error: { code: 'UNAUTHENTICATED', message: 'Token expired' } })

    await expect(apiFetch('/me')).rejects.toMatchObject({ status: 401 })
    expect(getToken()).toBeNull()
    expect(handler).toHaveBeenCalledOnce()
  })

  it('sends a JSON body and Idempotency-Key when provided', async () => {
    const fetchMock = mockFetchOnce(200, { token: 't', account: {} })

    await apiFetch('/auth/login', {
      method: 'POST',
      body: { email: 'a@b.com', password: 'pw' },
      idempotencyKey: 'key-1',
    })

    const [, init] = fetchMock.mock.calls[0]
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ email: 'a@b.com', password: 'pw' })
    expect(init.headers['Idempotency-Key']).toBe('key-1')
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd "Frontend" && npx vitest run src/lib/api/client.test.ts`
Expected: FAIL — `Failed to resolve import "./client"`.

- [ ] **Step 3: Implement `errors.ts`**

Create `Frontend/src/lib/api/errors.ts`:
```ts
export class ApiError extends Error {
  status: number
  code: string
  field?: string

  constructor(status: number, code: string, message: string, field?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.field = field
  }
}
```

- [ ] **Step 4: Implement `client.ts`**

Create `Frontend/src/lib/api/client.ts`:
```ts
import { getToken, clearToken } from './token'
import { ApiError } from './errors'

const BASE_URL = import.meta.env.VITE_API_URL ?? '/v1'

interface ErrorEnvelope {
  error: { code: string; message: string; field?: string }
}

type UnauthorizedHandler = () => void
let unauthorizedHandler: UnauthorizedHandler = () => {}

export function setUnauthorizedHandler(handler: UnauthorizedHandler): void {
  unauthorizedHandler = handler
}

export interface ApiFetchOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
  idempotencyKey?: string
}

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey

  const response = await fetch(`${BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })

  if (response.status === 401) {
    clearToken()
    unauthorizedHandler()
  }

  if (response.status === 204) {
    return undefined as T
  }

  if (!response.ok) {
    let envelope: ErrorEnvelope | null = null
    try {
      envelope = (await response.json()) as ErrorEnvelope
    } catch {
      envelope = null
    }
    throw new ApiError(
      response.status,
      envelope?.error.code ?? 'UNKNOWN',
      envelope?.error.message ?? 'Request failed',
      envelope?.error.field,
    )
  }

  return (await response.json()) as T
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd "Frontend" && npx vitest run src/lib/api/client.test.ts`
Expected: PASS (7 tests).

- [ ] **Step 6: Commit**

```bash
cd "Frontend" && git add src/lib/api/errors.ts src/lib/api/client.ts src/lib/api/client.test.ts
git commit -m "feat(frontend): typed fetch client with Bearer auth + error parsing"
```

---

### Task 4: Catalog wire→domain mappers

**Files:**
- Create: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Consumes: types `Artist`, `Album`, `Track`, `TrackCredit`, `BrowseCategory`, `Genre`, `OwnershipStatus`, `Money` from `../../types` (existing).
- Produces (exported wire interfaces + mapper functions, used by Task 5):
  - `ArtistWire`, `toArtist(wire: ArtistWire): Artist`
  - `TrackWire`, `toTrack(wire: TrackWire): Track`
  - `AlbumWire`, `toAlbum(wire: AlbumWire): Album`, `toAlbumTracks(wire: AlbumWire): Track[]`
  - `BrowseCategoryWire`, `toBrowseCategory(wire: BrowseCategoryWire): BrowseCategory`
  - `LyricsWire`, `LyricLineWire`, `toLyricLines(wire: LyricsWire): LyricLineWire[]`

- [ ] **Step 1: Write the failing tests**

Create `Frontend/src/lib/api/mappers.test.ts`:
```ts
import { describe, it, expect } from 'vitest'
import { toArtist, toTrack, toAlbum, toAlbumTracks, toBrowseCategory, toLyricLines } from './mappers'

describe('toArtist', () => {
  it('maps a full wire artist, converting nulls to undefined', () => {
    const artist = toArtist({
      id: 'a1',
      name: 'Black Sherif',
      image: 'img.jpg',
      coverImage: null,
      verified: true,
      monthlyListeners: 1000,
      followers: 500,
      bio: null,
      location: null,
      genres: null,
    })

    expect(artist).toEqual({
      id: 'a1',
      name: 'Black Sherif',
      image: 'img.jpg',
      coverImage: undefined,
      verified: true,
      monthlyListeners: 1000,
      followers: 500,
      bio: undefined,
      location: undefined,
      genres: undefined,
    })
  })
})

describe('toTrack', () => {
  it('maps a wire track including nested price', () => {
    const track = toTrack({
      id: 't1',
      title: 'Song',
      artistId: 'a1',
      artistName: 'Black Sherif',
      albumId: null,
      albumTitle: null,
      duration: 180,
      image: 'i.jpg',
      ownership: 'for-sale',
      price: { amount: 5, currency: 'GHS' },
      plays: 42,
      audioUrl: null,
      credits: null,
      quality: null,
      year: 2024,
    })

    expect(track.ownership).toBe('for-sale')
    expect(track.price).toEqual({ amount: 5, currency: 'GHS' })
    expect(track.albumId).toBeUndefined()
  })
})

describe('toAlbum / toAlbumTracks', () => {
  const wire = {
    id: 'al1',
    title: 'Album',
    artistId: 'a1',
    artistName: 'Black Sherif',
    year: 2024,
    coverImage: 'c.jpg',
    genres: ['Afrobeats'],
    trackIds: ['t1'],
    tracks: [
      {
        id: 't1',
        title: 'Song',
        artistId: 'a1',
        artistName: 'Black Sherif',
        albumId: 'al1',
        albumTitle: 'Album',
        duration: 180,
        image: 'i.jpg',
        ownership: 'free',
        price: null,
        plays: 10,
        audioUrl: null,
        credits: null,
        quality: null,
        year: 2024,
      },
    ],
  }

  it('maps the album without the embedded tracks field', () => {
    const album = toAlbum(wire)
    expect(album).toEqual({
      id: 'al1',
      title: 'Album',
      artistId: 'a1',
      artistName: 'Black Sherif',
      year: 2024,
      coverImage: 'c.jpg',
      genres: ['Afrobeats'],
      trackIds: ['t1'],
    })
  })

  it('maps the embedded tracks separately', () => {
    const tracks = toAlbumTracks(wire)
    expect(tracks).toHaveLength(1)
    expect(tracks[0].title).toBe('Song')
  })

  it('returns an empty array when tracks were not requested', () => {
    expect(toAlbumTracks({ ...wire, tracks: null })).toEqual([])
  })
})

describe('toBrowseCategory', () => {
  it('passes fields through unchanged', () => {
    expect(toBrowseCategory({ id: 'c1', title: 'Afrobeats', colorClass: 'bg-red-500' })).toEqual({
      id: 'c1',
      title: 'Afrobeats',
      colorClass: 'bg-red-500',
    })
  })
})

describe('toLyricLines', () => {
  it('returns the lines array', () => {
    expect(toLyricLines({ lines: [{ time: 0, text: 'la la' }] })).toEqual([{ time: 0, text: 'la la' }])
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd "Frontend" && npx vitest run src/lib/api/mappers.test.ts`
Expected: FAIL — `Failed to resolve import "./mappers"`.

- [ ] **Step 3: Implement `mappers.ts`**

Create `Frontend/src/lib/api/mappers.ts`:
```ts
import type { Artist, Album, Track, TrackCredit, BrowseCategory, Genre, OwnershipStatus, Money } from '../../types'

export interface ArtistWire {
  id: string
  name: string
  image: string
  coverImage: string | null
  verified: boolean | null
  monthlyListeners: number | null
  followers: number | null
  bio: string | null
  location: string | null
  genres: string[] | null
}

export function toArtist(wire: ArtistWire): Artist {
  return {
    id: wire.id,
    name: wire.name,
    image: wire.image,
    coverImage: wire.coverImage ?? undefined,
    verified: wire.verified ?? undefined,
    monthlyListeners: wire.monthlyListeners ?? undefined,
    followers: wire.followers ?? undefined,
    bio: wire.bio ?? undefined,
    location: wire.location ?? undefined,
    genres: (wire.genres ?? undefined) as Genre[] | undefined,
  }
}

export interface TrackCreditWire {
  role: string
  names: string[]
}

export interface TrackWire {
  id: string
  title: string
  artistId: string
  artistName: string
  albumId: string | null
  albumTitle: string | null
  duration: number
  image: string
  ownership: string
  price: Money | null
  plays: number | null
  audioUrl: string | null
  credits: TrackCreditWire[] | null
  quality: string | null
  year: number | null
}

export function toTrack(wire: TrackWire): Track {
  return {
    id: wire.id,
    title: wire.title,
    artistId: wire.artistId,
    artistName: wire.artistName,
    albumId: wire.albumId ?? undefined,
    albumTitle: wire.albumTitle ?? undefined,
    duration: wire.duration,
    image: wire.image,
    ownership: wire.ownership as OwnershipStatus,
    price: wire.price ?? undefined,
    plays: wire.plays ?? undefined,
    audioUrl: wire.audioUrl ?? undefined,
    credits: (wire.credits as TrackCredit[] | null) ?? undefined,
    quality: wire.quality ?? undefined,
    year: wire.year ?? undefined,
  }
}

export interface AlbumWire {
  id: string
  title: string
  artistId: string
  artistName: string
  year: number
  coverImage: string
  genres: string[] | null
  trackIds: string[]
  /** Populated only when the album was fetched with ?tracks=true. */
  tracks: TrackWire[] | null
}

export function toAlbum(wire: AlbumWire): Album {
  return {
    id: wire.id,
    title: wire.title,
    artistId: wire.artistId,
    artistName: wire.artistName,
    year: wire.year,
    coverImage: wire.coverImage,
    genres: (wire.genres ?? undefined) as Genre[] | undefined,
    trackIds: wire.trackIds,
  }
}

export function toAlbumTracks(wire: AlbumWire): Track[] {
  return (wire.tracks ?? []).map(toTrack)
}

export interface BrowseCategoryWire {
  id: string
  title: string
  colorClass: string
}

export function toBrowseCategory(wire: BrowseCategoryWire): BrowseCategory {
  return { id: wire.id, title: wire.title, colorClass: wire.colorClass }
}

export interface LyricLineWire {
  time: number
  text: string
}

export interface LyricsWire {
  lines: LyricLineWire[]
}

export function toLyricLines(wire: LyricsWire): LyricLineWire[] {
  return wire.lines
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd "Frontend" && npx vitest run src/lib/api/mappers.test.ts`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
cd "Frontend" && git add src/lib/api/mappers.ts src/lib/api/mappers.test.ts
git commit -m "feat(frontend): wire-to-domain mappers for catalog views"
```

---

### Task 5: Catalog query factories

**Files:**
- Create: `Frontend/src/lib/api/queries/catalog.ts`
- Test: `Frontend/src/lib/api/queries/catalog.test.ts`

**Interfaces:**
- Consumes: `apiFetch` from `../client` (Task 3); `toArtist`, `toTrack`, `toAlbum`, `toAlbumTracks`, `toBrowseCategory`, `toLyricLines` and their wire types from `../mappers` (Task 4).
- Produces (each returns a `queryOptions(...)` object — consumed by Task 9–12 route loaders and components via `queryClient.ensureQueryData(...)` / `useSuspenseQuery(...)`):
  - `homeQuery()` → `{ trending: Track[]; top10: Track[]; featuredAlbums: Album[] }`
  - `browseCategoriesQuery()` → `BrowseCategory[]`
  - `artistQuery(id: string)` → `Artist`
  - `artistTracksQuery(id: string)` → `Track[]`
  - `artistAlbumsQuery(id: string)` → `Album[]`
  - `albumQuery(id: string)` → `{ album: Album; tracks: Track[] }`
  - `trackQuery(id: string)` → `Track`
  - `lyricsQuery(id: string)` → `LyricLineWire[]`

- [ ] **Step 1: Write the failing tests**

Create `Frontend/src/lib/api/queries/catalog.test.ts`:
```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { apiFetch } from '../client'
import { homeQuery, artistQuery, albumQuery, trackQuery, browseCategoriesQuery, lyricsQuery } from './catalog'

vi.mock('../client', () => ({ apiFetch: vi.fn() }))

const ctx = {} as any

describe('catalog query factories', () => {
  beforeEach(() => vi.mocked(apiFetch).mockReset())

  it('homeQuery fetches /home and maps all three sections', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      trending: [{ id: 't1', title: 'A', artistId: 'a1', artistName: 'Art', albumId: null, albumTitle: null, duration: 10, image: 'i', ownership: 'free', price: null, plays: 1, audioUrl: null, credits: null, quality: null, year: null }],
      top10: [],
      featuredAlbums: [{ id: 'al1', title: 'B', artistId: 'a1', artistName: 'Art', year: 2024, coverImage: 'c', genres: null, trackIds: [], tracks: null }],
    })

    const result = await homeQuery().queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/home')
    expect(result.trending).toHaveLength(1)
    expect(result.featuredAlbums).toHaveLength(1)
  })

  it('browseCategoriesQuery fetches /browse-categories', async () => {
    vi.mocked(apiFetch).mockResolvedValue([{ id: 'c1', title: 'Afrobeats', colorClass: 'bg-red-500' }])

    const result = await browseCategoriesQuery().queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/browse-categories')
    expect(result).toEqual([{ id: 'c1', title: 'Afrobeats', colorClass: 'bg-red-500' }])
  })

  it('artistQuery fetches /artists/:id', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      id: 'a1', name: 'Black Sherif', image: 'img', coverImage: null, verified: true,
      monthlyListeners: 1, followers: 1, bio: null, location: null, genres: null,
    })

    const result = await artistQuery('a1').queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/artists/a1')
    expect(result.name).toBe('Black Sherif')
  })

  it('albumQuery requests embedded tracks and splits the result', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      id: 'al1', title: 'Album', artistId: 'a1', artistName: 'Art', year: 2024, coverImage: 'c',
      genres: null, trackIds: ['t1'],
      tracks: [{ id: 't1', title: 'Song', artistId: 'a1', artistName: 'Art', albumId: 'al1', albumTitle: 'Album', duration: 180, image: 'i', ownership: 'free', price: null, plays: 1, audioUrl: null, credits: null, quality: null, year: 2024 }],
    })

    const result = await albumQuery('al1').queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/albums/al1?tracks=true')
    expect(result.album.title).toBe('Album')
    expect(result.tracks).toHaveLength(1)
  })

  it('trackQuery fetches /tracks/:id', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      id: 't1', title: 'Song', artistId: 'a1', artistName: 'Art', albumId: null, albumTitle: null,
      duration: 180, image: 'i', ownership: 'free', price: null, plays: 1, audioUrl: null,
      credits: null, quality: null, year: 2024,
    })

    const result = await trackQuery('t1').queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/tracks/t1')
    expect(result.title).toBe('Song')
  })

  it('lyricsQuery fetches /tracks/:id/lyrics', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ lines: [{ time: 0, text: 'la' }] })

    const result = await lyricsQuery('t1').queryFn!(ctx)

    expect(apiFetch).toHaveBeenCalledWith('/tracks/t1/lyrics')
    expect(result).toEqual([{ time: 0, text: 'la' }])
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd "Frontend" && npx vitest run src/lib/api/queries/catalog.test.ts`
Expected: FAIL — `Failed to resolve import "./catalog"`.

- [ ] **Step 3: Implement `queries/catalog.ts`**

Create `Frontend/src/lib/api/queries/catalog.ts`:
```ts
import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import {
  toArtist,
  toTrack,
  toAlbum,
  toAlbumTracks,
  toBrowseCategory,
  toLyricLines,
  type ArtistWire,
  type TrackWire,
  type AlbumWire,
  type BrowseCategoryWire,
  type LyricsWire,
} from '../mappers'

interface HomeFeedWire {
  trending: TrackWire[]
  top10: TrackWire[]
  featuredAlbums: AlbumWire[]
}

export function homeQuery() {
  return queryOptions({
    queryKey: ['home'],
    queryFn: async () => {
      const wire = await apiFetch<HomeFeedWire>('/home')
      return {
        trending: wire.trending.map(toTrack),
        top10: wire.top10.map(toTrack),
        featuredAlbums: wire.featuredAlbums.map(toAlbum),
      }
    },
  })
}

export function browseCategoriesQuery() {
  return queryOptions({
    queryKey: ['browse-categories'],
    queryFn: async () => {
      const wire = await apiFetch<BrowseCategoryWire[]>('/browse-categories')
      return wire.map(toBrowseCategory)
    },
  })
}

export function artistQuery(id: string) {
  return queryOptions({
    queryKey: ['artist', id],
    queryFn: async () => toArtist(await apiFetch<ArtistWire>(`/artists/${id}`)),
  })
}

export function artistTracksQuery(id: string) {
  return queryOptions({
    queryKey: ['artist', id, 'tracks'],
    queryFn: async () => (await apiFetch<TrackWire[]>(`/artists/${id}/tracks`)).map(toTrack),
  })
}

export function artistAlbumsQuery(id: string) {
  return queryOptions({
    queryKey: ['artist', id, 'albums'],
    queryFn: async () => (await apiFetch<AlbumWire[]>(`/artists/${id}/albums`)).map(toAlbum),
  })
}

export function albumQuery(id: string) {
  return queryOptions({
    queryKey: ['album', id],
    queryFn: async () => {
      const wire = await apiFetch<AlbumWire>(`/albums/${id}?tracks=true`)
      return { album: toAlbum(wire), tracks: toAlbumTracks(wire) }
    },
  })
}

export function trackQuery(id: string) {
  return queryOptions({
    queryKey: ['track', id],
    queryFn: async () => toTrack(await apiFetch<TrackWire>(`/tracks/${id}`)),
  })
}

export function lyricsQuery(id: string) {
  return queryOptions({
    queryKey: ['track', id, 'lyrics'],
    queryFn: async () => toLyricLines(await apiFetch<LyricsWire>(`/tracks/${id}/lyrics`)),
  })
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd "Frontend" && npx vitest run src/lib/api/queries/catalog.test.ts`
Expected: PASS (6 tests).

- [ ] **Step 5: Run the full test suite**

Run: `cd "Frontend" && npm test`
Expected: all test files pass (token, client, mappers, catalog queries).

- [ ] **Step 6: Commit**

```bash
cd "Frontend" && git add src/lib/api/queries/catalog.ts src/lib/api/queries/catalog.test.ts
git commit -m "feat(frontend): TanStack Query factories for catalog endpoints"
```

---

### Task 6: Dev networking — Vite proxy + backend CORS

**Files:**
- Modify: `Frontend/vite.config.ts`
- Modify: `backend/src/main/resources/application.properties`

**Interfaces:**
- Produces: in dev, `fetch('/v1/...')` from the frontend (port 5173) reaches the backend (port 8080) with no CORS error, via the Vite proxy. This is the network path every query factory (Task 5) and the auth actions (Task 8) use once wired.

- [ ] **Step 1: Add the dev proxy to `vite.config.ts`**

Read the current file first, then replace its contents. `Frontend/vite.config.ts` becomes:
```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { TanStackRouterVite } from '@tanstack/router-vite-plugin'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    TanStackRouterVite(),
  ],
  server: {
    proxy: {
      '/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
```

- [ ] **Step 2: Enable CORS on the backend (for non-proxied use, e.g. a future mobile client or `npm run preview`)**

Read `backend/src/main/resources/application.properties` first. Add, right after the `# ============ Core / HTTP ============` block (after the `quarkus.http.limits.max-body-size` line):
```properties
# CORS — the frontend SPA calls this API from a different origin in preview/prod builds
# (dev uses the Vite proxy instead, so this matters outside local dev). WU frontend-wiring-1.
quarkus.http.cors=true
quarkus.http.cors.origins=${BEATZ_CORS_ORIGINS:http://localhost:5173,http://localhost:4173}
quarkus.http.cors.methods=GET,POST,PATCH,DELETE,OPTIONS
quarkus.http.cors.headers=Content-Type,Authorization,Idempotency-Key
```

- [ ] **Step 3: Verify manually**

Tell the user to run, in two terminals:
```bash
cd backend && ./mvnw quarkus:dev
```
```bash
cd Frontend && npm run dev
```
Then, in a third terminal, confirm the proxy works:
```bash
curl -s http://localhost:5173/v1/browse-categories | head -c 200
```
Expected: a JSON array (not a connection error, not an HTML 404 page).

- [ ] **Step 4: Commit**

```bash
git add Frontend/vite.config.ts backend/src/main/resources/application.properties
git commit -m "chore: dev proxy (frontend) + CORS (backend) for local API wiring"
```

---

### Task 7: Router context wiring

**Files:**
- Modify: `Frontend/src/main.tsx`

**Interfaces:**
- Produces: every route's `loader` receives `context.queryClient` (a `QueryClient`), used by Task 9–12.

- [ ] **Step 1: Wire the QueryClient into the router context**

Read `Frontend/src/main.tsx` first. Replace the router creation block (currently `const router = createRouter({ routeTree })` at line 11, immediately after the `queryClient` is created at lines 21-28) so the file reads:
```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createRouter } from '@tanstack/react-router'
import './index.css'

// Import the generated route tree
import { routeTree } from './routeTree.gen'

// Create a client
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5 minutes
      retry: 1,
    },
  },
})

// Create a new router instance, with the query client available to every loader
const router = createRouter({ routeTree, context: { queryClient } })

// Register the router instance for type safety
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}

import { ThemeProvider } from './components/theme-provider'
import { ToastProvider } from './components/ui/toast-provider'
import { AuthProvider } from './features/auth/auth-context'
import { NotificationsProvider } from './features/notifications/notifications-context'
import { PlayerProvider } from './features/player/player-context'
import { CartProvider } from './features/cart/cart-context'
import { CollectionProvider } from './features/collection/collection-context'

// Initialize the root
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider defaultTheme="dark">
      <ToastProvider>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <NotificationsProvider>
              <CartProvider>
                <CollectionProvider>
                  <PlayerProvider>
                    <RouterProvider router={router} />
                  </PlayerProvider>
                </CollectionProvider>
              </CartProvider>
            </NotificationsProvider>
          </AuthProvider>
        </QueryClientProvider>
      </ToastProvider>
    </ThemeProvider>
  </StrictMode>,
)
```

The only change from the current file: `createRouter({ routeTree })` → `createRouter({ routeTree, context: { queryClient } })`.

- [ ] **Step 2: Declare the router context type on the root route**

Read `Frontend/src/routes/__root.tsx` first. It currently exports `Route` via `createRootRoute(...)` (or similar) with no context type. Change the export to use `createRootRouteWithContext`:

Find the line that creates the root route (something like `export const Route = createRootRoute({ ... })`) and change it to:
```tsx
import { createRootRouteWithContext } from '@tanstack/react-router'
import type { QueryClient } from '@tanstack/react-query'

interface RouterContext {
  queryClient: QueryClient
}

export const Route = createRootRouteWithContext<RouterContext>()({
  // ...keep the existing options object exactly as-is
})
```
Keep every existing option in that object (component, etc.) unchanged — only the factory call and its generic changes, plus the two new imports.

- [ ] **Step 3: Verify the app still boots**

Tell the user to run `cd Frontend && npm run dev`, open the app, and confirm it loads with no console errors and no change in behavior (still mock-backed at this point — this task only wires plumbing, no route uses it yet).

- [ ] **Step 4: Commit**

```bash
git add Frontend/src/main.tsx Frontend/src/routes/__root.tsx
git commit -m "feat(frontend): expose QueryClient via router context for loaders"
```

---

### Task 8: Real authentication

**Files:**
- Modify: `Frontend/src/features/auth/auth-context.tsx`
- Modify: `Frontend/src/routes/login.tsx`
- Modify: `Frontend/src/routes/signup.tsx`
- Modify: `Frontend/src/components/layout/app-shell.tsx`
- Test: `Frontend/src/features/auth/auth-context.test.tsx` (deferred — see Step 7 note; this task's primary verification is manual, consistent with the spec's testing section, which scopes automated tests to the API client layer)

**Interfaces:**
- Consumes: `apiFetch`, `setUnauthorizedHandler` from `../../lib/api/client` (Task 3); `getToken`, `setToken`, `clearToken` from `../../lib/api/token` (Task 2).
- Produces: `useAuth()` returning `{ account, isAuthenticated, isLoading, login, signup, logout, becomeArtist }` where `login`/`signup`/`logout`/`becomeArtist` are now `async` (return `Promise<void>`). `isLoading` is new — `true` until the initial session hydration (`GET /v1/me`, if a token exists) resolves.

- [ ] **Step 1: Rewrite `auth-context.tsx`**

Read the current file first. Replace its entire contents with:
```tsx
/**
 * Client-side auth store.
 *
 * Holds the signed-in account + role, backed by the real /v1/auth and /v1/me
 * endpoints. The JWT returned by login/signup is persisted via
 * `lib/api/token.ts`; the session hydrates from GET /v1/me on load.
 */

import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { apiFetch, setUnauthorizedHandler } from '../../lib/api/client'
import { clearToken, getToken, setToken } from '../../lib/api/token'

export interface Account {
  id: string
  name: string
  email: string
  avatar: string | null
  isArtist: boolean
  isAdmin: boolean
}

interface AuthContextValue {
  account: Account | null
  isAuthenticated: boolean
  /** True until the initial session hydration (GET /v1/me) has resolved. */
  isLoading: boolean
  login: (email: string, password: string) => Promise<void>
  signup: (name: string, email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  becomeArtist: () => Promise<void>
}

interface AuthResponse {
  token: string
  account: Account
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [account, setAccount] = useState<Account | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const hydrated = useRef(false)

  useEffect(() => {
    setUnauthorizedHandler(() => setAccount(null))
  }, [])

  useEffect(() => {
    if (hydrated.current) return
    hydrated.current = true
    const token = getToken()
    if (!token) {
      setIsLoading(false)
      return
    }
    apiFetch<Account>('/me')
      .then(setAccount)
      .catch(() => {
        clearToken()
        setAccount(null)
      })
      .finally(() => setIsLoading(false))
  }, [])

  const value = useMemo<AuthContextValue>(() => ({
    account,
    isAuthenticated: account !== null,
    isLoading,
    login: async (email, password) => {
      const result = await apiFetch<AuthResponse>('/auth/login', {
        method: 'POST',
        body: { email, password },
      })
      setToken(result.token)
      setAccount(result.account)
    },
    signup: async (name, email, password) => {
      const result = await apiFetch<AuthResponse>('/auth/signup', {
        method: 'POST',
        body: { name, email, password },
      })
      setToken(result.token)
      setAccount(result.account)
    },
    logout: async () => {
      try {
        await apiFetch('/auth/logout', { method: 'POST' })
      } finally {
        clearToken()
        setAccount(null)
      }
    },
    becomeArtist: async () => {
      const result = await apiFetch<Account>('/me/become-artist', { method: 'POST' })
      setAccount(result)
    },
  }), [account, isLoading])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an <AuthProvider>')
  return ctx
}

export function initialsOfAccount(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (!parts.length) return '?'
  return (parts[0][0] + (parts[1]?.[0] ?? '')).toUpperCase()
}
```

Note what was deliberately dropped: `PERSIST_KEY`, `seedAccount`, `nameFromEmail`, `normalize`, `load` — all mock-only scaffolding for the always-signed-in demo session. There is no `localStorage`-persisted `Account` object anymore; only the JWT persists, and the account is re-derived from `GET /v1/me` on each load.

- [ ] **Step 2: Update `login.tsx` for async login + real error display + disabled social buttons**

Read the current file first. Change the `LoginComponent` function body (keep the JSX structure/classes identical; only the logic changes):
```tsx
function LoginComponent() {
  const navigate = useNavigate()
  const { login } = useAuth()
  const { toast } = useToast()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const canSubmit = email.trim() !== '' && password !== ''

  const submit = async () => {
    if (!canSubmit) return
    setError('')
    try {
      await login(email, password)
      navigate({ to: '/' })
    } catch {
      setError('Incorrect email or password.')
    }
  }
```

Add the import (near the other feature imports):
```tsx
import { useToast } from '../components/ui/toast-provider'
```

Below the password `<input>` block (after its closing `</div>`) and before the submit `<button>`, add the error line:
```tsx
            {error && <p className="text-sm font-medium text-red-500 -mt-2">{error}</p>}
```

Change the submit button's `onClick` from `onClick={submit}` to keep calling the same (now-async) function — no change needed there, React allows an async handler.

Change the `SocialButtons` line from:
```tsx
<SocialButtons onSelect={(provider) => { login(`${provider}@beatzclik.com`, provider); navigate({ to: '/' }) }} />
```
to:
```tsx
<SocialButtons onSelect={() => toast('Social sign-in is coming soon — use email for now.', 'info')} />
```

- [ ] **Step 3: Update `signup.tsx` the same way**

Read the current file first. Mirror Task 8 Step 2's changes: async `submit`, an `error` state rendered under the password field, and the `SocialButtons onSelect` swapped for the same info toast. Full `SignUpComponent` body:
```tsx
function SignUpComponent() {
  const navigate = useNavigate()
  const { signup } = useAuth()
  const { toast } = useToast()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const canSubmit = email.trim() !== '' && password.length >= 8 && name.trim() !== ''

  const submit = async () => {
    if (!canSubmit) return
    setError('')
    try {
      await signup(name, email, password)
      navigate({ to: '/' })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create your account.')
    }
  }
```

Add the import:
```tsx
import { useToast } from '../components/ui/toast-provider'
```

Below the password `<input>` block, before the submit `<button>`:
```tsx
            {error && <p className="text-sm font-medium text-red-500 -mt-2">{error}</p>}
```

Change the `SocialButtons` line from:
```tsx
<SocialButtons onSelect={(provider) => { signup('', `${provider}@beatzclik.com`, provider); navigate({ to: '/' }) }} />
```
to:
```tsx
<SocialButtons onSelect={() => toast('Social sign-in is coming soon — use email for now.', 'info')} />
```

- [ ] **Step 4: Guard the app-shell redirect on `isLoading`**

Read the current file first. In `Frontend/src/components/layout/app-shell.tsx`, change:
```tsx
  const { isAuthenticated } = useAuth()
  const onAuthRoute = AUTH_ROUTES.some((route) => location.pathname.startsWith(route))

  // Gate the whole app: signed-out users are sent to the login screen.
  useEffect(() => {
    if (!isAuthenticated && !onAuthRoute) navigate({ to: '/login' })
  }, [isAuthenticated, onAuthRoute, navigate])

  if (!isAuthenticated && !onAuthRoute) return null
```
to:
```tsx
  const { isAuthenticated, isLoading } = useAuth()
  const onAuthRoute = AUTH_ROUTES.some((route) => location.pathname.startsWith(route))

  // Gate the whole app: signed-out users are sent to the login screen. Wait for the
  // initial session hydration (GET /v1/me) before deciding — otherwise a valid,
  // already-logged-in session briefly bounces to /login on every page load.
  useEffect(() => {
    if (!isLoading && !isAuthenticated && !onAuthRoute) navigate({ to: '/login' })
  }, [isLoading, isAuthenticated, onAuthRoute, navigate])

  if (isLoading) return null
  if (!isAuthenticated && !onAuthRoute) return null
```

- [ ] **Step 5: Type-check**

Run: `cd "Frontend" && npx tsc -b --noEmit`
Expected: no errors. If `becomeArtist` or other `useAuth()` consumers fail to type-check because they expected a sync function, fix the call site to either ignore the returned promise (fine for a plain `onClick={becomeArtist}`) or `await` it — there should be none besides what Steps 2–3 already handled, since `artist-gate.tsx`'s `onClick={becomeArtist}` accepts an async handler unchanged.

- [ ] **Step 6: Manual verification**

Tell the user to run the dev stack (backend `quarkus:dev` + frontend `npm run dev`), then:
1. Load the app — it should redirect to `/login` (no more auto-signed-in demo session).
2. Sign up with a new email/password (8+ chars) — should land on `/` signed in as that fan.
3. Reload the page — should stay signed in (session rehydrates from the stored JWT via `GET /v1/me`).
4. Log out (existing logout control in the header/sidebar) — should return to `/login`.
5. Log in with the same credentials — should succeed.
6. Log in with a wrong password — should show "Incorrect email or password." and stay on `/login`.
7. Click a social button on `/login` — should show the "coming soon" toast, no navigation.

- [ ] **Step 7: Commit**

```bash
git add Frontend/src/features/auth/auth-context.tsx Frontend/src/routes/login.tsx \
  Frontend/src/routes/signup.tsx Frontend/src/components/layout/app-shell.tsx
git commit -m "feat(frontend): wire auth to POST /v1/auth/* and GET /v1/me"
```

---

### Task 9: Wire the home route

**Files:**
- Modify: `Frontend/src/routes/index.tsx`

**Interfaces:**
- Consumes: `homeQuery`, `browseCategoriesQuery` from `../lib/api/queries/catalog` (Task 5); `context.queryClient` (Task 7).

- [ ] **Step 1: Add loader + live data for trending/top10/featuredAlbums/browseCategories**

Read the current file first (`Frontend/src/routes/index.tsx`). This route mixes sections that have a backend source (`trending`, `top10`/Ghana chart, `featuredAlbums`, `browseCategories`) with two that don't (`playlists` → "Made for you", `artists` → "Popular artists" — no list-all endpoint exists per the Global Constraints). Keep the latter two on their existing mock imports; wire the former to live data.

Replace the top of the file (imports through the `top10`/`quickPickPlaylists` consts) with:
```tsx
import { createFileRoute, Link, useSuspenseQuery } from '@tanstack/react-router'
import { Heart, Play } from 'lucide-react'
import { Card, CardContent, CardImage, CardSubtitle, CardTitle, QuickPickCard } from '../components/ui/card'
import { MediaRail } from '../features/discover/components/media-rail'
import { ArtistCircle } from '../features/discover/components/artist-circle'
import { FeaturedCarousel } from '../features/discover/components/featured-carousel'
import { usePlayer } from '../features/player/player-context'
import { artists, playlists } from '../lib/mock-data'
import { homeQuery, browseCategoriesQuery } from '../lib/api/queries/catalog'
import { formatCount } from '../lib/format'
import { useAuth } from '../features/auth/auth-context'

export const Route = createFileRoute('/')({
  loader: async ({ context: { queryClient } }) => {
    await Promise.all([
      queryClient.ensureQueryData(homeQuery()),
      queryClient.ensureQueryData(browseCategoriesQuery()),
    ])
  },
  component: HomeComponent,
})

function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}

const RAIL_ITEM = 'snap-start shrink-0 w-44 sm:w-48 lg:w-52'

const quickPickPlaylists = playlists.slice(0, 3)
```

- [ ] **Step 2: Read the live data in the component and use it in place of the removed mock arrays**

Inside `HomeComponent`, right after the existing `const { account } = useAuth()` / `firstName` lines, add:
```tsx
  const { data: home } = useSuspenseQuery(homeQuery())
  const { data: browseCategories } = useSuspenseQuery(browseCategoriesQuery())
  const top10 = home.top10
  const trending = home.trending
  const featuredAlbums = home.featuredAlbums
```

Delete the old module-level `const trending = ...` / `const top10 = ...` lines (already removed in Step 1's replacement block — confirm they're gone) since they're now per-render, sourced from the query.

Everywhere the JSX currently reads `albums` (the "New releases" rail, which maps over the *entire* mock `albums` array) — that section has no backend equivalent (no "list all albums" endpoint) and is not one of the four backend-sourced sections named in Step 1. Leave the "New releases" rail's `{albums.map(...)}` as-is, still reading from the mock `albums` import — but since `albums` is no longer imported (Step 1's import line drops it), add it back to the import line so the file still compiles:
```tsx
import { artists, playlists, albums } from '../lib/mock-data'
```

All other JSX in the file — the quick picks, `FeaturedCarousel albums={featuredAlbums}`, "Made for you" rail (`playlists`), "Trending" rail (`trending`), "New releases" rail (`albums`), "Popular artists" rail (`artists`), the Ghana Top 10 chart (`top10`), and "Browse by mood & genre" (`browseCategories`) — stays byte-for-byte the same JSX; only where each variable now comes from changes.

- [ ] **Step 3: Type-check**

Run: `cd "Frontend" && npx tsc -b --noEmit`
Expected: no errors.

- [ ] **Step 4: Manual verification**

Tell the user to run the dev stack, log in, and load `/`. Confirm:
- The page renders with no layout shift or missing sections compared to the mock version.
- "Trending in Ghana", the Top 10 chart, and the featured carousel show the backend's seeded tracks/albums (from `R__seed_dev_data.sql`) — not the old mock catalog's tracks (e.g. not the same track titles as before, since the seed data differs from the mock data).
- "Made for you", "New releases", and "Popular artists" still show the original mock content (deliberately unchanged this slice).
- Network tab shows `GET /v1/home` and `GET /v1/browse-categories`.

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/routes/index.tsx
git commit -m "feat(frontend): wire home route to GET /v1/home + /v1/browse-categories"
```

---

### Task 10: Wire the artist route

**Files:**
- Modify: `Frontend/src/routes/artist/$artistId.tsx`

**Interfaces:**
- Consumes: `artistQuery`, `artistTracksQuery`, `artistAlbumsQuery` from `../../lib/api/queries/catalog` (Task 5).

- [ ] **Step 1: Add the loader and swap `getArtist`/`albums.filter` for live queries**

Read the current file first. Replace the imports and the two top-level components:
```tsx
import { createFileRoute, Link, useSuspenseQuery } from '@tanstack/react-router'
import { useState } from 'react'
import { Play, Pause, Check, Share2, MoreHorizontal, BadgeCheck, ShoppingCart, MapPin } from 'lucide-react'
import { usePlayer } from '../../features/player/player-context'
import { useCart } from '../../features/cart/cart-context'
import { useCollection } from '../../features/collection/collection-context'
import { useToast } from '../../components/ui/toast-provider'
import { SupportModal } from '../../features/podcasts/components/support-modal'
import { EventListRow } from '../../features/events/components/event-list-row'
import { Card, CardContent, CardImage, CardSubtitle, CardTitle } from '../../components/ui/card'
import { artistQuery, artistTracksQuery, artistAlbumsQuery } from '../../lib/api/queries/catalog'
import { events } from '../../lib/event-data'
import { formatCount, formatDuration, formatPrice } from '../../lib/format'
import { cn } from '../../utils/cn'
import type { Track } from '../../types'

export const Route = createFileRoute('/artist/$artistId')({
  loader: async ({ context: { queryClient }, params: { artistId } }) => {
    await Promise.all([
      queryClient.ensureQueryData(artistQuery(artistId)),
      queryClient.ensureQueryData(artistTracksQuery(artistId)),
      queryClient.ensureQueryData(artistAlbumsQuery(artistId)),
    ])
  },
  component: ArtistComponent,
})

function ArtistComponent() {
  const { artistId } = Route.useParams()
  return <Artist artistId={artistId} />
}

function Artist({ artistId }: { artistId: string }) {
  const { data: artist } = useSuspenseQuery(artistQuery(artistId))
  const { data: topTracks } = useSuspenseQuery(artistTracksQuery(artistId))
  const { data: discography } = useSuspenseQuery(artistAlbumsQuery(artistId))
  const { currentTrack, isPlaying, playQueue, togglePlay } = usePlayer()
  const { addItem } = useCart()
  const { isArtistFollowed, toggleFollowedArtist } = useCollection()
  const { toast } = useToast()
  const [tipOpen, setTipOpen] = useState(false)
  const following = isArtistFollowed(artistId)

  const buyTrack = (track: Track) => {
    addItem({
      id: `track:${track.id}`,
      kind: 'track',
      title: track.title,
      subtitle: track.artistName,
      image: track.image,
      price: track.price ?? { amount: 0, currency: 'GHS' },
    })
    toast(`“${track.title}” added to cart`, 'success')
  }

  const shows = events.filter((e) => e.artistId === artistId).sort((a, b) => a.date.localeCompare(b.date))
```

Notes on this change:
- The old "not found" branch (`if (!artist) return <...Artist not found.../>`) is removed: the backend now 404s (`ARTIST_NOT_FOUND`) before the component ever renders — a `TrackId`/`ArtistId` route error surfaces via the route's `errorComponent` (added in Step 2), not an in-component `null` check.
- `getArtistTracks(artistId)` → `topTracks` (from the query, already backend-filtered/sorted the same way the mock helper was).
- `albums.filter((a) => a.artistId === artistId)` → `discography` (from `artistAlbumsQuery`).
- Everything below this point in the function body (the JSX, the `PopularRow` component at the bottom of the file) is unchanged — it already reads `artist`, `topTracks`, `discography`, `shows` by name.

- [ ] **Step 2: Add a not-found error component to the route**

Directly under the `Route = createFileRoute(...)` call from Step 1, before `function ArtistComponent()`, add an `errorComponent` to the route options:
```tsx
export const Route = createFileRoute('/artist/$artistId')({
  loader: async ({ context: { queryClient }, params: { artistId } }) => {
    await Promise.all([
      queryClient.ensureQueryData(artistQuery(artistId)),
      queryClient.ensureQueryData(artistTracksQuery(artistId)),
      queryClient.ensureQueryData(artistAlbumsQuery(artistId)),
    ])
  },
  component: ArtistComponent,
  errorComponent: () => (
    <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
      <h1 className="text-title text-beatz-dark-bg dark:text-white">Artist not found</h1>
      <Link to="/" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">Back to home</Link>
    </div>
  ),
})
```
This renders the exact same markup the old `if (!artist)` branch rendered — so the "not found" screen is visually identical, just reached via a different mechanism (a thrown 404 from the loader instead of an in-component `null` check).

- [ ] **Step 3: Type-check**

Run: `cd "Frontend" && npx tsc -b --noEmit`
Expected: no errors.

- [ ] **Step 4: Manual verification**

Tell the user to visit `/artist/<a real seeded artist id>` (find one via the browser network tab on the `/v1/home` or `/v1/browse-categories`/`/v1/artists/...` response, or query the dev DB) and confirm: hero, popular tracks, about, discography all render from live data with no visual difference from before. Also visit `/artist/does-not-exist` and confirm the "Artist not found" screen still appears.

- [ ] **Step 5: Commit**

```bash
git add "Frontend/src/routes/artist/\$artistId.tsx"
git commit -m "feat(frontend): wire artist route to GET /v1/artists/:id(+tracks,+albums)"
```

---

### Task 11: Wire the album route

**Files:**
- Modify: `Frontend/src/routes/album/$albumId.tsx`

**Interfaces:**
- Consumes: `albumQuery` from `../../lib/api/queries/catalog` (Task 5); `artistQuery` (Task 5, for the album's artist link).

- [ ] **Step 1: Add the loader and swap `getAlbum`/`getAlbumTracks`/`getArtist` for live queries**

Read the current file first. Replace the imports and the two top-level components:
```tsx
import { createFileRoute, Link, useSuspenseQuery } from '@tanstack/react-router'
import { Play, Pause, Plus, Download, Share2, MoreHorizontal, Clock, ShoppingCart, Check } from 'lucide-react'
import { cn } from '../../utils/cn'
import { usePlayer } from '../../features/player/player-context'
import { useCart } from '../../features/cart/cart-context'
import { useToast } from '../../components/ui/toast-provider'
import { albumQuery, artistQuery } from '../../lib/api/queries/catalog'
import { formatCount, formatDuration, formatPrice, formatTotalDuration } from '../../lib/format'
import type { Track } from '../../types'

export const Route = createFileRoute('/album/$albumId')({
  loader: async ({ context: { queryClient }, params: { albumId } }) => {
    const { album } = await queryClient.ensureQueryData(albumQuery(albumId))
    await queryClient.ensureQueryData(artistQuery(album.artistId))
  },
  component: AlbumComponent,
  errorComponent: () => (
    <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
      <h1 className="text-title text-beatz-dark-bg dark:text-white">Album not found</h1>
      <Link to="/" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">
        Back to home
      </Link>
    </div>
  ),
})

function AlbumComponent() {
  const { albumId } = Route.useParams()
  return <Album albumId={albumId} />
}

function Album({ albumId }: { albumId: string }) {
  const { data: albumData } = useSuspenseQuery(albumQuery(albumId))
  const { album, tracks } = albumData
  const { data: artist } = useSuspenseQuery(artistQuery(album.artistId))
  const { currentTrack, isPlaying, playQueue, togglePlay } = usePlayer()
  const { addItem } = useCart()
  const { toast } = useToast()
```

Everything below this point (the `totalSeconds`/`ownedCount`/etc. derived consts, all JSX, the `TrackRow` component) is unchanged — it already reads `album`, `tracks`, `artist` by name.

- [ ] **Step 2: Type-check**

Run: `cd "Frontend" && npx tsc -b --noEmit`
Expected: no errors.

- [ ] **Step 3: Manual verification**

Visit `/album/<a real seeded album id>` and confirm hero, tracklist, buy actions, and the artist link all render identically to the mock version. Visit `/album/does-not-exist` and confirm the "Album not found" screen appears.

- [ ] **Step 4: Commit**

```bash
git add "Frontend/src/routes/album/\$albumId.tsx"
git commit -m "feat(frontend): wire album route to GET /v1/albums/:id?tracks=true"
```

---

### Task 12: Wire the track route

**Files:**
- Modify: `Frontend/src/routes/track.$trackId.tsx`

**Interfaces:**
- Consumes: `trackQuery`, `artistQuery`, `lyricsQuery`, `artistTracksQuery` from `../lib/api/queries/catalog` (Task 5).

- [ ] **Step 1: Add the loader and swap `getTrack`/`getArtist`/`getLyrics`/`getArtistTracks` for live queries**

Read the current file first. Replace the imports and the top of `TrackPageComponent`:
```tsx
import { createFileRoute, Link, useSuspenseQuery } from '@tanstack/react-router'
import { useState } from 'react'
import { Play, Pause, Share2, Heart, Plus, ListPlus, Info, Users, Check, ShoppingCart, Mic2, Send, MoreHorizontal, Clock, CalendarDays } from 'lucide-react'
import { cn } from '../utils/cn'
import { LyricsView } from '../components/music/lyrics-view'
import { AddToPlaylistModal } from '../features/collection/components/add-to-playlist-modal'
import { Card, CardContent, CardImage, CardSubtitle, CardTitle } from '../components/ui/card'
import { usePlayer } from '../features/player/player-context'
import { useCart } from '../features/cart/cart-context'
import { useCollection } from '../features/collection/collection-context'
import { useToast } from '../components/ui/toast-provider'
import { trackQuery, artistQuery, lyricsQuery, artistTracksQuery } from '../lib/api/queries/catalog'
import { formatCount, formatDuration, formatPrice } from '../lib/format'

export const Route = createFileRoute('/track/$trackId')({
  loader: async ({ context: { queryClient }, params: { trackId } }) => {
    const track = await queryClient.ensureQueryData(trackQuery(trackId))
    await Promise.all([
      queryClient.ensureQueryData(artistQuery(track.artistId)),
      queryClient.ensureQueryData(lyricsQuery(trackId)),
      queryClient.ensureQueryData(artistTracksQuery(track.artistId)),
    ])
  },
  component: TrackPageComponent,
  errorComponent: () => (
    <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
      <h1 className="text-title text-beatz-dark-bg dark:text-white">Track not found</h1>
      <Link to="/" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">Back to home</Link>
    </div>
  ),
})

// Deterministic, organic waveform amplitudes (0–100%), mirrored around a centre line.
const BAR_COUNT = 150
const hash = (n: number) => {
  const x = Math.sin(n * 127.1 + 311.7) * 43758.5453
  return x - Math.floor(x)
}
const WAVE = Array.from({ length: BAR_COUNT }, (_, i) => {
  const t = i / BAR_COUNT
  const attack = Math.min(1, t / 0.03)
  const release = 1 - Math.max(0, (t - 0.96) / 0.04)
  const body = 0.7 + 0.3 * Math.sin(t * Math.PI * 4)
  const amp = (0.18 + 0.82 * hash(i)) * attack * release * body
  return Math.max(8, Math.round(amp * 100))
})

function TrackPageComponent() {
  const { trackId } = Route.useParams()
  const { data: track } = useSuspenseQuery(trackQuery(trackId))
  const [isLyricsMode, setIsLyricsMode] = useState(false)
  const [reaction, setReaction] = useState('')
  const [addOpen, setAddOpen] = useState(false)

  const { currentTrack, isPlaying, progress, playQueue, togglePlay, seek } = usePlayer()
  const { addItem } = useCart()
  const { isTrackLiked, toggleLikedTrack } = useCollection()
  const { toast } = useToast()

  if (isLyricsMode) return <LyricsView onClose={() => setIsLyricsMode(false)} />

  const { data: artist } = useSuspenseQuery(artistQuery(track.artistId))
  const { data: lyricsLines } = useSuspenseQuery(lyricsQuery(trackId))
  const { data: artistTracks } = useSuspenseQuery(artistTracksQuery(track.artistId))
  const liked = isTrackLiked(track.id)
  const isThis = currentTrack?.id === track.id
  const isThisPlaying = isPlaying && isThis
  const ratio = isThis && track.duration ? Math.min(1, progress / track.duration) : 0
  const lyricLines = lyricsLines.filter((l) => l.text !== '♪').slice(0, 5)
  const moreTracks = artistTracks.filter((t) => t.id !== track.id).slice(0, 5)
```

Notes:
- The old `if (!track) return <...not found.../>` branch moves to the route's `errorComponent` (same rendered markup, reached via the loader's 404 instead of an in-component check) — same pattern as Task 10/11.
- `getLyrics(track.id, track.duration)` (which synthesized lyric timing client-side) becomes `lyricsQuery(trackId)` reading the backend's `GET /v1/tracks/:id/lyrics` directly — no client-side synthesis needed since the backend already returns `{time, text}` lines.
- Everything below this point (the rest of the JSX, `InfoRow`) is unchanged — it already reads `track`, `artist`, `lyricLines`, `moreTracks` by name.

- [ ] **Step 2: Type-check**

Run: `cd "Frontend" && npx tsc -b --noEmit`
Expected: no errors.

- [ ] **Step 3: Manual verification**

Visit `/track/<a real seeded track id>` and confirm the hero, waveform, lyrics preview, credits, and "more from artist" rail all render identically to the mock version. Visit `/track/does-not-exist` and confirm the "Track not found" screen appears.

- [ ] **Step 4: Commit**

```bash
git add "Frontend/src/routes/track.\$trackId.tsx"
git commit -m "feat(frontend): wire track route to GET /v1/tracks/:id(+lyrics)"
```

---

### Task 13: Full-slice manual verification

**Files:** none (verification only).

**Interfaces:** none — this task exercises everything built in Tasks 1–12 together.

- [ ] **Step 1: Run the full automated test suite**

```bash
cd "Frontend" && npm test
```
Expected: all tests pass (token, client, mappers, catalog queries — ~23 tests across 4 files).

- [ ] **Step 2: Type-check and lint the whole frontend**

```bash
cd "Frontend" && npx tsc -b --noEmit && npm run lint
```
Expected: no errors.

- [ ] **Step 3: Full manual walkthrough**

Tell the user to run the dev stack (`cd backend && ./mvnw quarkus:dev`, `cd Frontend && npm run dev`) and walk through:
1. Fresh load → redirected to `/login` (no auto-signed-in demo).
2. Sign up as a new fan → lands on `/`, home page renders with live trending/top10/featured data + mock playlists/artists/albums rails, no visual regressions.
3. Click into a seeded artist → artist page renders live.
4. Click into one of that artist's albums → album page renders live, tracklist correct.
5. Click into a track → track page renders live, lyrics preview shows real lyric lines.
6. Reload the browser on the track page → session persists (still logged in), track still loads.
7. Log out → back at `/login`.
8. Log back in with the same credentials → back on `/`, still logged in after a reload.
9. Confirm every other area of the app (library, store, podcasts, events, studio, admin, cart, search) still works exactly as before — untouched, still mock-backed.

- [ ] **Step 4: Backend verification gate**

Tell the user to run the standard backend gate themselves (per project convention — do not run it yourself):
```bash
bash backend/scripts/verify.sh
```
Expected: green (the only backend change in this slice, CORS config, is additive and config-only).

- [ ] **Step 5: Final commit (if any cleanup was needed) and open the PR**

If Steps 1–4 required fixes, commit them individually per the fix. Once everything is green, open the PR:
```bash
gh pr create --title "feat(frontend): wire auth + catalog to the real backend (slice 1)" --body "$(cat <<'EOF'
## Summary
- Add an API client layer (JWT auth, error parsing) under Frontend/src/lib/api/.
- Wire real login/signup/session (POST /v1/auth/*, GET /v1/me) into auth-context.tsx.
- Wire home, artist, album, and track routes to the real catalog endpoints via TanStack
  Router loaders + TanStack Query, keeping components' synchronous reads (no visual change).
- Add Vite dev proxy + backend CORS for local wiring.
- Everything else (library, cart/checkout, studio, admin, podcasts, events, search,
  notifications) remains on mocks — scoped out per the approved design doc.

Design: docs/superpowers/specs/2026-07-14-frontend-wiring-catalog-slice-design.md
Plan: docs/superpowers/plans/2026-07-14-frontend-wiring-catalog.md

## Test plan
- [x] Frontend unit tests pass (`npm test`)
- [x] Type-check + lint clean
- [x] Manual walkthrough: signup/login/logout/reload-persistence, home/artist/album/track
      render live data with no visual change, 404s show the existing not-found screens
- [ ] Backend `bash backend/scripts/verify.sh` (run by reviewer/user)
EOF
)"
```
