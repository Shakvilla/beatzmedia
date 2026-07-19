# Studio Slice 3b — Release-Creation Wizard Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the release-creation wizard's in-memory mock with the real WU-CAT-5 draft flow — create-draft → multipart upload-attach → PATCH (metadata + per-track price/order) → submit — while keeping the wizard's four steps visually unchanged.

**Architecture:** `apiFetch` gains multipart (`FormData`) support; a new set of `studio.ts` functions wraps the four draft-flow endpoints; `release-draft-context` gains a `releaseId` and async actions (`createOrUpdateDraft`, `uploadTrack`, `removeTrack`, `submitRelease`) that call those functions and reconcile local state; the wizard chrome and Tracks step call the async actions instead of mutating in-memory-only state. Splits, cover art, and other unsupported fields stay client-only (shown/validated, never sent), per the approved spec.

**Tech Stack:** React 19 + TanStack Router/Query v5, TypeScript, Vitest + Testing Library. Node 22.17.1 via nvm (`source ~/.nvm/nvm.sh && nvm use 22.17.1`).

## Global Constraints

- **Spec:** `docs/superpowers/specs/2026-07-18-frontend-studio-slice3b-wizard-design.md` (branch `feat/frontend-studio-slice3b-wizard`, base master).
- **Typecheck gate is `npm run build` (`tsc -b`)** — NOT `tsc --noEmit`; the root tsconfig is looser and misses import-path/`noImplicitAny` errors. Lint must add **0 new warnings** (baseline is 214).
- **Money on the wire is integer minor units** (`priceMinor`, pesewas): `priceMinor = Math.round(cedis * 100)`. Money read back is `MoneyView { amount(cedis), currency }` — unwrap `.amount`.
- **Backend metadata mapping (verified against source):** the frontend `draft.visibility` values `'public' | 'scheduled'` match the backend `Visibility` enum db values 1:1 (send as-is). `type` = `draft.releaseType` (`single|ep|album|mixtape`). `scheduledAt` is an ISO string, sent only when `visibility === 'scheduled'` and a `releaseDate` exists.
- **Multipart part name is `file`** (backend `TrackUploadForm.file()`).
- **Endpoints (WU-CAT-5, live on master):**
  - `POST /v1/studio/releases` — body `{ title?, type, visibility?, scheduledAt?, genre?, description? }` → 201 detail (`{ id, … }`).
  - `POST /v1/studio/releases/:id/tracks` — multipart part `file` → 201 `UploadedTrack` (`{ id, title, duration, status, progress, src, price(MoneyView), explicit, position }`).
  - `DELETE /v1/studio/releases/:id/tracks/:trackId` → 204.
  - `PATCH /v1/studio/releases/:id` — body `{ title?, genre?, description?, visibility?, scheduledAt?, tracks?: [{trackId, position, priceMinor}] }`.
  - `POST /v1/studio/releases/:id/submit` — `Idempotency-Key` header → 200 detail; `422 TRACK_COUNT_INVALID` if the count matrix fails (single=1, ep=3–6, album=7+, mixtape≥1).
- **Keep both Review submit gates** (cover art present + every track's splits total 100%) even though neither is persisted — no visual change.
- **Non-goals:** persisting splits, cover-image upload, resume/hydrate an existing draft, determinate upload-progress bar.

## File structure

- `Frontend/src/lib/api/client.ts` — add a `FormData` branch to `apiFetch`.
- `Frontend/src/lib/api/mappers.ts` — `UploadedTrackWire` + `toWizardTrack`.
- `Frontend/src/lib/api/queries/studio.ts` — `apiCreateDraft`, `apiUploadTrack`, `apiUpdateRelease`, `apiSubmitRelease`, `apiDeleteTrack` (+ input types).
- `Frontend/src/lib/studio-data.ts` — `trackCountError` helper (shared client pre-check).
- `Frontend/src/features/studio/release-draft-context.tsx` — `releaseId` + async actions; remove `ADD_TRACKS`/`TICK_UPLOADS`.
- `Frontend/src/routes/studio.release.new.tsx` — real create/save/submit in `handleContinue` + Save-draft.
- `Frontend/src/routes/studio.release.new.tracks.tsx` — real upload/delete; indeterminate progress.
- Tests: `client.test.ts`, `mappers.test.ts`, `queries/studio.test.ts` (create/extend), `features/studio/release-draft-context.test.tsx` (create).

---

### Task 1: `apiFetch` multipart support

**Files:**
- Modify: `Frontend/src/lib/api/client.ts:23-37`
- Test: `Frontend/src/lib/api/client.test.ts`

**Interfaces:**
- Produces: `apiFetch<T>(path, { method, body, idempotencyKey })` — when `body instanceof FormData`, no `Content-Type` header is set and the `FormData` is passed to `fetch` unmodified; JSON bodies behave exactly as before.

- [ ] **Step 1: Write the failing test** — append to `client.test.ts` (inside the `describe('apiFetch')` block):

```ts
it('sends a FormData body without a JSON Content-Type and without stringifying', async () => {
  const fetchMock = mockFetchOnce(201, { id: 'trk-1' })
  const form = new FormData()
  form.append('file', new Blob(['x'], { type: 'audio/wav' }), 'song.wav')

  await apiFetch('/studio/releases/r1/tracks', { method: 'POST', body: form })

  const [, init] = fetchMock.mock.calls[0]
  expect(init.body).toBe(form)                       // passed through, not JSON.stringify'd
  expect(init.headers['Content-Type']).toBeUndefined()
})

it('still sets a JSON Content-Type for object bodies', async () => {
  const fetchMock = mockFetchOnce(200, { ok: true })
  await apiFetch('/x', { method: 'POST', body: { a: 1 } })
  const [, init] = fetchMock.mock.calls[0]
  expect(init.headers['Content-Type']).toBe('application/json')
})
```

- [ ] **Step 2: Run it and watch it fail**

Run: `source ~/.nvm/nvm.sh && nvm use 22.17.1 && cd Frontend && npx vitest run src/lib/api/client.test.ts`
Expected: the FormData test FAILS (body is `"[object FormData]"` / Content-Type is `application/json`).

- [ ] **Step 3: Implement** — replace the header/body construction in `apiFetch` (`client.ts`). New body of the function's top half:

```ts
export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const isForm = options.body instanceof FormData
  const headers: Record<string, string> = {}
  if (!isForm) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey

  const response = await fetch(`${BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body:
      options.body === undefined
        ? undefined
        : isForm
          ? (options.body as FormData)
          : JSON.stringify(options.body),
  })
  // ...rest of the function (401 handling, 204, error envelope, JSON parse) is UNCHANGED
```

- [ ] **Step 4: Run tests to green**

Run: `npx vitest run src/lib/api/client.test.ts`
Expected: PASS (all cases, including the pre-existing ones).

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/lib/api/client.ts Frontend/src/lib/api/client.test.ts
git commit -m "feat(studio): apiFetch supports multipart FormData bodies (slice 3b)"
```

---

### Task 2: Draft-flow query functions + track mapper

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts` (append after the Studio-releases block, ~line 586)
- Modify: `Frontend/src/lib/api/queries/studio.ts`
- Modify: `Frontend/src/lib/studio-data.ts` (append `trackCountError`)
- Test: `Frontend/src/lib/api/mappers.test.ts`, `Frontend/src/lib/api/queries/studio.test.ts` (create)

**Interfaces:**
- Consumes: `apiFetch` (Task 1) with `FormData` support; `UploadedTrack` type from `release-draft-context`; `ReleaseType` from `studio-data`.
- Produces:
  - `toWizardTrack(w: UploadedTrackWire): UploadedTrack`
  - `apiCreateDraft(input: CreateDraftInput): Promise<string>` (returns the new release id)
  - `apiUploadTrack(releaseId: string, file: File): Promise<UploadedTrack>`
  - `apiUpdateRelease(releaseId: string, patch: UpdateReleaseInput): Promise<void>`
  - `apiSubmitRelease(releaseId: string, idempotencyKey: string): Promise<void>`
  - `apiDeleteTrack(releaseId: string, trackId: string): Promise<void>`
  - `CreateDraftInput`, `UpdateReleaseInput`, `TrackPatch` types
  - `trackCountError(type: ReleaseType, n: number): string | null`

- [ ] **Step 1: Write the failing mapper test** — append to `mappers.test.ts`:

```ts
import { toWizardTrack } from './mappers'

describe('toWizardTrack', () => {
  it('unwraps MoneyView price and passes status/duration through', () => {
    const t = toWizardTrack({
      id: 'trk-1', title: 'Intro', duration: 181, status: 'ready', progress: 100,
      src: '/audio/trk-1.m3u8', price: { amount: 2.5, currency: 'GHS' }, explicit: false, position: 0,
    })
    expect(t).toEqual({
      id: 'trk-1', title: 'Intro', duration: 181, status: 'ready',
      progress: 100, src: '/audio/trk-1.m3u8', price: 2.5, explicit: false,
    })
  })

  it('coerces an unknown status to uploading and null src/price to safe defaults', () => {
    const t = toWizardTrack({
      id: 'trk-2', title: 'X', duration: 0, status: 'transcoding', progress: 0,
      src: null as unknown as string, price: null as unknown as { amount: number; currency: string },
      explicit: false, position: 1,
    })
    expect(t.status).toBe('uploading')
    expect(t.src).toBe('')
    expect(t.price).toBe(0)
  })
})
```

- [ ] **Step 2: Run it and watch it fail**

Run: `cd Frontend && npx vitest run src/lib/api/mappers.test.ts`
Expected: FAIL — `toWizardTrack` is not exported.

- [ ] **Step 3: Implement the mapper** — append to `mappers.ts`. Add a **type-only** import at the top of the file (with the other imports):

```ts
import type { UploadedTrack } from '../../features/studio/release-draft-context'
```

Then append:

```ts
// ── Studio release wizard: upload-attach track ────────────────────
// UploadedTrackView from POST /studio/releases/:id/tracks. price is a
// MoneyView; the wizard's UploadedTrack wants plain cedis. status is
// narrowed to the wizard's union.
export interface UploadedTrackWire {
  id: string
  title: string
  duration: number
  status: string
  progress: number
  src: string | null
  price: { amount: number; currency: string }
  explicit: boolean
  position: number
}

export function toWizardTrack(w: UploadedTrackWire): UploadedTrack {
  const status = w.status === 'ready' || w.status === 'error' ? w.status : 'uploading'
  return {
    id: w.id,
    title: w.title,
    duration: w.duration ?? 0,
    status,
    progress: w.progress ?? 100,
    src: w.src ?? '',
    price: w.price?.amount ?? 0,
    explicit: w.explicit ?? false,
  }
}
```

- [ ] **Step 4: Run the mapper test to green**

Run: `npx vitest run src/lib/api/mappers.test.ts`
Expected: PASS.

- [ ] **Step 5: Add `trackCountError` to `studio-data.ts`** — append near the other release helpers (after `isMultiTrack`):

```ts
/**
 * Client mirror of the backend track-count matrix (WU-CAT-5 submit). Returns a
 * human message when the count is invalid for the type, or null when valid.
 */
export function trackCountError(type: ReleaseType, n: number): string | null {
  switch (type) {
    case 'single': return n === 1 ? null : 'A single must have exactly 1 track.'
    case 'ep': return n >= 3 && n <= 6 ? null : 'An EP needs 3–6 tracks.'
    case 'album': return n >= 7 ? null : 'An album needs at least 7 tracks.'
    case 'mixtape': return n >= 1 ? null : 'A mixtape needs at least 1 track.'
    default: return null
  }
}
```

- [ ] **Step 6: Write the failing studio.ts test** — create `Frontend/src/lib/api/queries/studio.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { apiFetch } from '../client'
import {
  apiCreateDraft, apiUploadTrack, apiUpdateRelease, apiSubmitRelease, apiDeleteTrack,
} from './studio'

vi.mock('../client', () => ({ apiFetch: vi.fn() }))
const mockApiFetch = vi.mocked(apiFetch)

beforeEach(() => mockApiFetch.mockReset())

describe('studio draft-flow functions', () => {
  it('apiCreateDraft POSTs metadata and returns the new id', async () => {
    mockApiFetch.mockResolvedValue({ id: 'rel-9' })
    const id = await apiCreateDraft({ title: 'Iron Boy', type: 'album', visibility: 'public' })
    expect(id).toBe('rel-9')
    expect(mockApiFetch).toHaveBeenCalledWith('/studio/releases', {
      method: 'POST', body: { title: 'Iron Boy', type: 'album', visibility: 'public' },
    })
  })

  it('apiUploadTrack sends a FormData with a "file" part and maps the response', async () => {
    mockApiFetch.mockResolvedValue({
      id: 'trk-1', title: 'Intro', duration: 120, status: 'ready', progress: 100,
      src: '/a.m3u8', price: { amount: 2.5, currency: 'GHS' }, explicit: false, position: 0,
    })
    const file = new File(['x'], 'intro.wav', { type: 'audio/wav' })
    const t = await apiUploadTrack('rel-9', file)

    const [path, opts] = mockApiFetch.mock.calls[0]
    expect(path).toBe('/studio/releases/rel-9/tracks')
    expect(opts.method).toBe('POST')
    expect(opts.body).toBeInstanceOf(FormData)
    expect((opts.body as FormData).get('file')).toBe(file)
    expect(t).toMatchObject({ id: 'trk-1', price: 2.5, status: 'ready' })
  })

  it('apiUpdateRelease PATCHes the patch body', async () => {
    mockApiFetch.mockResolvedValue({})
    await apiUpdateRelease('rel-9', { title: 'X', tracks: [{ trackId: 't1', position: 0, priceMinor: 250 }] })
    expect(mockApiFetch).toHaveBeenCalledWith('/studio/releases/rel-9', {
      method: 'PATCH', body: { title: 'X', tracks: [{ trackId: 't1', position: 0, priceMinor: 250 }] },
    })
  })

  it('apiSubmitRelease POSTs with an Idempotency-Key header', async () => {
    mockApiFetch.mockResolvedValue({})
    await apiSubmitRelease('rel-9', 'key-abc')
    expect(mockApiFetch).toHaveBeenCalledWith('/studio/releases/rel-9/submit', {
      method: 'POST', idempotencyKey: 'key-abc',
    })
  })

  it('apiDeleteTrack DELETEs the track path', async () => {
    mockApiFetch.mockResolvedValue(undefined)
    await apiDeleteTrack('rel-9', 'trk-1')
    expect(mockApiFetch).toHaveBeenCalledWith('/studio/releases/rel-9/tracks/trk-1', { method: 'DELETE' })
  })
})
```

- [ ] **Step 7: Run it and watch it fail**

Run: `npx vitest run src/lib/api/queries/studio.test.ts`
Expected: FAIL — the five functions are not exported.

- [ ] **Step 8: Implement the studio.ts functions** — in `studio.ts`, extend the mapper import and add the new imports + functions. Update the import from `'../mappers'` to also pull `toWizardTrack, type UploadedTrackWire`, and add a type-only import of `ReleaseType` and `UploadedTrack`:

```ts
import type { ReleaseType } from '../../studio-data'
import type { UploadedTrack } from '../../../features/studio/release-draft-context'
import {
  // ...existing named imports...
  toStudioRelease, type StudioReleaseWire, toWizardTrack, type UploadedTrackWire,
} from '../mappers'
```

Append at the end of `studio.ts`:

```ts
// ── Release create-flow (WU-CAT-5 draft flow) ─────────────────────

export interface CreateDraftInput {
  title?: string
  type: ReleaseType
  genre?: string
  description?: string
  visibility?: 'public' | 'scheduled'
  scheduledAt?: string
}

export interface TrackPatch { trackId: string; position: number; priceMinor: number }

export interface UpdateReleaseInput {
  title?: string
  genre?: string
  description?: string
  visibility?: 'public' | 'scheduled'
  scheduledAt?: string
  tracks?: TrackPatch[]
}

/** `POST /v1/studio/releases` — create a metadata-only draft; returns the new id. */
export function apiCreateDraft(input: CreateDraftInput): Promise<string> {
  return apiFetch<{ id: string }>('/studio/releases', { method: 'POST', body: input }).then((r) => r.id)
}

/** `POST /v1/studio/releases/:id/tracks` — multipart upload-attach; part name is "file". */
export function apiUploadTrack(releaseId: string, file: File): Promise<UploadedTrack> {
  const form = new FormData()
  form.append('file', file)
  return apiFetch<UploadedTrackWire>(`/studio/releases/${releaseId}/tracks`, { method: 'POST', body: form })
    .then(toWizardTrack)
}

/** `PATCH /v1/studio/releases/:id` — metadata + wholesale track list (draft-only). */
export function apiUpdateRelease(releaseId: string, patch: UpdateReleaseInput): Promise<void> {
  return apiFetch<unknown>(`/studio/releases/${releaseId}`, { method: 'PATCH', body: patch }).then(() => undefined)
}

/** `POST /v1/studio/releases/:id/submit` — finalize draft → in_review. */
export function apiSubmitRelease(releaseId: string, idempotencyKey: string): Promise<void> {
  return apiFetch<unknown>(`/studio/releases/${releaseId}/submit`, { method: 'POST', idempotencyKey }).then(() => undefined)
}

/** `DELETE /v1/studio/releases/:id/tracks/:trackId` — remove a draft track. */
export function apiDeleteTrack(releaseId: string, trackId: string): Promise<void> {
  return apiFetch<void>(`/studio/releases/${releaseId}/tracks/${trackId}`, { method: 'DELETE' })
}
```

- [ ] **Step 9: Run studio.ts + mapper tests to green**

Run: `npx vitest run src/lib/api/queries/studio.test.ts src/lib/api/mappers.test.ts`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add Frontend/src/lib/api/mappers.ts Frontend/src/lib/api/mappers.test.ts \
        Frontend/src/lib/api/queries/studio.ts Frontend/src/lib/api/queries/studio.test.ts \
        Frontend/src/lib/studio-data.ts
git commit -m "feat(studio): draft-flow query functions + track mapper + count matrix (slice 3b)"
```

---

### Task 3: Backend-backed `release-draft-context`

**Files:**
- Modify: `Frontend/src/features/studio/release-draft-context.tsx` (full rewrite of state/reducer/provider)
- Test: `Frontend/src/features/studio/release-draft-context.test.tsx` (create)

**Interfaces:**
- Consumes: `apiCreateDraft`, `apiUploadTrack`, `apiUpdateRelease`, `apiSubmitRelease`, `apiDeleteTrack`, `CreateDraftInput`, `UpdateReleaseInput` (Task 2).
- Produces (new context value):
  - state `draft.releaseId: string | null`
  - `createOrUpdateDraft(): Promise<void>` — create on first call (stores `releaseId`), else PATCH metadata.
  - `uploadTrack(file: File): Promise<void>` — add placeholder, ensure draft, upload, replace-or-error.
  - `removeTrack(id: string): Promise<void>` — drop locally; DELETE remotely if the track has a server id.
  - `submitRelease(): Promise<void>` — PATCH final metadata + `tracks[]`, then submit.
  - unchanged sync actions: `setField`, `updateTrack`, `moveTrack`, `reorderTracks`, `setAllPrices`, `setTrackSplits`, `applySplitsToAll`, `reset`.
  - **removed:** `addTracks`, `tickUploads`.
- The `UploadedTrack` and `SplitEntry` types keep their current shapes (exported, unchanged).

- [ ] **Step 1: Write the failing context test** — create `release-draft-context.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { ReleaseDraftProvider, useReleaseDraft } from './release-draft-context'
import * as studio from '../../lib/api/queries/studio'

vi.mock('../../lib/api/queries/studio')

const wrapper = ({ children }: { children: ReactNode }) => (
  <ReleaseDraftProvider initial={{ releaseType: 'single', title: 'Soja', price: 2.5 }}>{children}</ReleaseDraftProvider>
)

beforeEach(() => vi.resetAllMocks())

describe('release-draft-context', () => {
  it('createOrUpdateDraft creates once, stores the id, then PATCHes on the next call', async () => {
    vi.mocked(studio.apiCreateDraft).mockResolvedValue('rel-1')
    vi.mocked(studio.apiUpdateRelease).mockResolvedValue()
    const { result } = renderHook(() => useReleaseDraft(), { wrapper })

    await act(async () => { await result.current.createOrUpdateDraft() })
    expect(studio.apiCreateDraft).toHaveBeenCalledOnce()
    expect(result.current.draft.releaseId).toBe('rel-1')

    await act(async () => { await result.current.createOrUpdateDraft() })
    expect(studio.apiCreateDraft).toHaveBeenCalledOnce()          // not called again
    expect(studio.apiUpdateRelease).toHaveBeenCalledOnce()
  })

  it('uploadTrack adds a placeholder then replaces it with the server track', async () => {
    vi.mocked(studio.apiCreateDraft).mockResolvedValue('rel-1')
    vi.mocked(studio.apiUploadTrack).mockResolvedValue({
      id: 'trk-9', title: 'Soja', duration: 180, status: 'ready', progress: 100, src: '/a', price: 0, explicit: false,
    })
    const { result } = renderHook(() => useReleaseDraft(), { wrapper })

    await act(async () => { await result.current.uploadTrack(new File(['x'], 'soja.wav')) })

    await waitFor(() => expect(result.current.draft.tracks).toHaveLength(1))
    expect(result.current.draft.tracks[0].id).toBe('trk-9')
    expect(result.current.draft.tracks[0].status).toBe('ready')
    expect(result.current.draft.tracks[0].price).toBe(2.5)        // wizard's chosen price preserved
  })

  it('submitRelease flushes tracks then submits', async () => {
    vi.mocked(studio.apiCreateDraft).mockResolvedValue('rel-1')
    vi.mocked(studio.apiUploadTrack).mockResolvedValue({
      id: 'trk-9', title: 'Soja', duration: 180, status: 'ready', progress: 100, src: '/a', price: 0, explicit: false,
    })
    vi.mocked(studio.apiUpdateRelease).mockResolvedValue()
    vi.mocked(studio.apiSubmitRelease).mockResolvedValue()
    const { result } = renderHook(() => useReleaseDraft(), { wrapper })

    await act(async () => { await result.current.uploadTrack(new File(['x'], 'soja.wav')) })
    await act(async () => { await result.current.submitRelease() })

    expect(studio.apiUpdateRelease).toHaveBeenCalledWith('rel-1', expect.objectContaining({
      tracks: [{ trackId: 'trk-9', position: 0, priceMinor: 250 }],
    }))
    expect(studio.apiSubmitRelease).toHaveBeenCalledWith('rel-1', expect.any(String))
  })
})
```

- [ ] **Step 2: Run it and watch it fail**

Run: `cd Frontend && npx vitest run src/features/studio/release-draft-context.test.tsx`
Expected: FAIL — `createOrUpdateDraft`/`uploadTrack`/`submitRelease` don't exist and `draft.releaseId` is undefined.

- [ ] **Step 3: Rewrite `release-draft-context.tsx`** with the full file below:

```tsx
/**
 * Release-draft store for the new-release wizard, backed by the WU-CAT-5
 * draft flow. Holds the in-progress release as the creator moves across the
 * wizard's four steps (Details → Tracks → Splits → Review). The server draft
 * is created on leaving Details; tracks are uploaded for real; splits / cover
 * art / other extras stay client-only (not sent) until their backends land.
 */

import {
  createContext, useContext, useMemo, useReducer, useRef, type ReactNode,
} from 'react'
import type { Genre } from '../../types'
import type { ReleaseType } from '../../lib/studio-data'
import {
  apiCreateDraft, apiUploadTrack, apiUpdateRelease, apiSubmitRelease, apiDeleteTrack,
  type CreateDraftInput, type UpdateReleaseInput,
} from '../../lib/api/queries/studio'

/** How a collaborator's share has been confirmed. */
export type SplitConfirmation = 'self' | 'confirmed' | 'pending' | 'auto'

/** One collaborator's royalty share of a track (client-only until WU-CAT-6). */
export interface SplitEntry {
  id: string
  name: string
  email: string
  role: string
  percent: number
  confirmation: SplitConfirmation
}

/** A track staged in the release draft. */
export interface UploadedTrack {
  id: string
  title: string
  duration: number
  status: 'uploading' | 'ready' | 'error'
  progress: number
  src: string
  price: number
  explicit: boolean
}

export interface ReleaseDraft {
  /** Server draft id once created (on leaving Details); null before then. */
  releaseId: string | null
  releaseType: ReleaseType
  title: string
  primaryArtist: string
  featuredArtists: string
  label: string
  releaseDate: string
  genre: Genre | ''
  description: string
  coverImage: string | null
  visibility: 'public' | 'scheduled'
  tracks: UploadedTrack[]
  price: number
  splits: Record<string, SplitEntry[]>
  agreementAccepted: boolean
  presaveGenerated: boolean
}

const initialDraft: ReleaseDraft = {
  releaseId: null,
  releaseType: 'single',
  title: '',
  primaryArtist: '',
  featuredArtists: '',
  label: '',
  releaseDate: '',
  genre: '',
  description: '',
  coverImage: null,
  visibility: 'scheduled',
  tracks: [],
  price: 2.5,
  splits: {},
  agreementAccepted: false,
  presaveGenerated: false,
}

type DraftAction =
  | { type: 'SET_FIELD'; field: keyof ReleaseDraft; value: ReleaseDraft[keyof ReleaseDraft] }
  | { type: 'SET_RELEASE_ID'; id: string }
  | { type: 'ADD_PLACEHOLDER'; track: UploadedTrack }
  | { type: 'REPLACE_TRACK'; tempId: string; track: UploadedTrack }
  | { type: 'MARK_TRACK_ERROR'; id: string }
  | { type: 'UPDATE_TRACK'; id: string; patch: Partial<UploadedTrack> }
  | { type: 'REMOVE_TRACK'; id: string }
  | { type: 'MOVE_TRACK'; id: string; dir: -1 | 1 }
  | { type: 'REORDER_TRACKS'; from: number; to: number }
  | { type: 'SET_ALL_PRICES'; price: number }
  | { type: 'SET_TRACK_SPLITS'; trackId: string; splits: SplitEntry[] }
  | { type: 'APPLY_SPLITS_TO_ALL'; splits: SplitEntry[] }
  | { type: 'RESET' }

function reducer(state: ReleaseDraft, action: DraftAction): ReleaseDraft {
  switch (action.type) {
    case 'SET_FIELD':
      return { ...state, [action.field]: action.value }
    case 'SET_RELEASE_ID':
      return { ...state, releaseId: action.id }
    case 'ADD_PLACEHOLDER':
      return { ...state, tracks: [...state.tracks, action.track] }
    case 'REPLACE_TRACK':
      return {
        ...state,
        tracks: state.tracks.map((t) => (t.id === action.tempId ? action.track : t)),
      }
    case 'MARK_TRACK_ERROR':
      return {
        ...state,
        tracks: state.tracks.map((t) => (t.id === action.id ? { ...t, status: 'error' } : t)),
      }
    case 'UPDATE_TRACK':
      return {
        ...state,
        tracks: state.tracks.map((t) => (t.id === action.id ? { ...t, ...action.patch } : t)),
      }
    case 'REMOVE_TRACK':
      return { ...state, tracks: state.tracks.filter((t) => t.id !== action.id) }
    case 'MOVE_TRACK': {
      const i = state.tracks.findIndex((t) => t.id === action.id)
      const j = i + action.dir
      if (i === -1 || j < 0 || j >= state.tracks.length) return state
      const tracks = [...state.tracks]
      ;[tracks[i], tracks[j]] = [tracks[j], tracks[i]]
      return { ...state, tracks }
    }
    case 'REORDER_TRACKS': {
      const { from, to } = action
      if (from === to || from < 0 || to < 0 || from >= state.tracks.length || to >= state.tracks.length) return state
      const tracks = [...state.tracks]
      const [moved] = tracks.splice(from, 1)
      tracks.splice(to, 0, moved)
      return { ...state, tracks }
    }
    case 'SET_ALL_PRICES':
      return { ...state, tracks: state.tracks.map((t) => ({ ...t, price: action.price })) }
    case 'SET_TRACK_SPLITS':
      return { ...state, splits: { ...state.splits, [action.trackId]: action.splits } }
    case 'APPLY_SPLITS_TO_ALL': {
      const next: Record<string, SplitEntry[]> = {}
      for (const t of state.tracks) {
        next[t.id] = action.splits.map((s) => ({ ...s, id: `${t.id}-${s.id}` }))
      }
      return { ...state, splits: next }
    }
    case 'RESET':
      return initialDraft
    default:
      return state
  }
}

/** Map the wizard draft → create-draft body. visibility values match the backend 1:1. */
function toCreateInput(d: ReleaseDraft): CreateDraftInput {
  return {
    title: d.title.trim() || undefined,
    type: d.releaseType,
    genre: d.genre || undefined,
    description: d.description.trim() || undefined,
    visibility: d.visibility,
    scheduledAt:
      d.visibility === 'scheduled' && d.releaseDate ? new Date(d.releaseDate).toISOString() : undefined,
  }
}

/** Map the wizard draft → PATCH metadata (no tracks). */
function toMetaPatch(d: ReleaseDraft): UpdateReleaseInput {
  return {
    title: d.title.trim() || undefined,
    genre: d.genre || undefined,
    description: d.description.trim() || undefined,
    visibility: d.visibility,
    scheduledAt:
      d.visibility === 'scheduled' && d.releaseDate ? new Date(d.releaseDate).toISOString() : undefined,
  }
}

const isServerId = (id: string) => !id.startsWith('tmp-')

interface ReleaseDraftContextValue {
  draft: ReleaseDraft
  setField: <K extends keyof ReleaseDraft>(field: K, value: ReleaseDraft[K]) => void
  updateTrack: (id: string, patch: Partial<UploadedTrack>) => void
  removeTrack: (id: string) => Promise<void>
  moveTrack: (id: string, dir: -1 | 1) => void
  reorderTracks: (from: number, to: number) => void
  setAllPrices: (price: number) => void
  setTrackSplits: (trackId: string, splits: SplitEntry[]) => void
  applySplitsToAll: (splits: SplitEntry[]) => void
  createOrUpdateDraft: () => Promise<void>
  uploadTrack: (file: File) => Promise<void>
  submitRelease: () => Promise<void>
  reset: () => void
}

const ReleaseDraftContext = createContext<ReleaseDraftContextValue | null>(null)

export function ReleaseDraftProvider({ children, initial }: { children: ReactNode; initial?: Partial<ReleaseDraft> }) {
  const [draft, dispatch] = useReducer(reducer, { ...initialDraft, ...initial })

  // Mirror state in a ref so async actions read the latest releaseId/price/tracks
  // even when several run concurrently (e.g. multiple uploads).
  const stateRef = useRef(draft)
  stateRef.current = draft

  const value = useMemo<ReleaseDraftContextValue>(() => {
    const ensureDraft = async (): Promise<string> => {
      if (stateRef.current.releaseId) return stateRef.current.releaseId
      const id = await apiCreateDraft(toCreateInput(stateRef.current))
      stateRef.current = { ...stateRef.current, releaseId: id } // block concurrent double-create
      dispatch({ type: 'SET_RELEASE_ID', id })
      return id
    }

    return {
      draft,
      setField: (field, value) => dispatch({ type: 'SET_FIELD', field, value }),
      updateTrack: (id, patch) => dispatch({ type: 'UPDATE_TRACK', id, patch }),
      moveTrack: (id, dir) => dispatch({ type: 'MOVE_TRACK', id, dir }),
      reorderTracks: (from, to) => dispatch({ type: 'REORDER_TRACKS', from, to }),
      setAllPrices: (price) => dispatch({ type: 'SET_ALL_PRICES', price }),
      setTrackSplits: (trackId, splits) => dispatch({ type: 'SET_TRACK_SPLITS', trackId, splits }),
      applySplitsToAll: (splits) => dispatch({ type: 'APPLY_SPLITS_TO_ALL', splits }),
      reset: () => dispatch({ type: 'RESET' }),

      createOrUpdateDraft: async () => {
        if (!stateRef.current.releaseId) {
          await ensureDraft()
        } else {
          await apiUpdateRelease(stateRef.current.releaseId, toMetaPatch(stateRef.current))
        }
      },

      uploadTrack: async (file) => {
        const tempId = `tmp-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
        dispatch({
          type: 'ADD_PLACEHOLDER',
          track: {
            id: tempId,
            title: file.name.replace(/\.[^.]+$/, ''),
            duration: 0,
            status: 'uploading',
            progress: 0,
            src: URL.createObjectURL(file),
            price: stateRef.current.price,
            explicit: false,
          },
        })
        try {
          const id = await ensureDraft()
          const server = await apiUploadTrack(id, file)
          dispatch({ type: 'REPLACE_TRACK', tempId, track: { ...server, price: stateRef.current.price } })
        } catch {
          dispatch({ type: 'MARK_TRACK_ERROR', id: tempId })
        }
      },

      removeTrack: async (id) => {
        const rid = stateRef.current.releaseId
        dispatch({ type: 'REMOVE_TRACK', id })
        if (rid && isServerId(id)) {
          try {
            await apiDeleteTrack(rid, id)
          } catch {
            // already removed from the UI; server cleanup can happen via the releases list
          }
        }
      },

      submitRelease: async () => {
        const rid = await ensureDraft()
        const s = stateRef.current
        const tracks = s.tracks
          .filter((t) => isServerId(t.id) && t.status !== 'error')
          .map((t, i) => ({ trackId: t.id, position: i, priceMinor: Math.round(t.price * 100) }))
        await apiUpdateRelease(rid, { ...toMetaPatch(s), tracks })
        await apiSubmitRelease(rid, crypto.randomUUID())
      },
    }
  }, [draft])

  return <ReleaseDraftContext.Provider value={value}>{children}</ReleaseDraftContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useReleaseDraft(): ReleaseDraftContextValue {
  const ctx = useContext(ReleaseDraftContext)
  if (!ctx) throw new Error('useReleaseDraft must be used within a <ReleaseDraftProvider>')
  return ctx
}
```

- [ ] **Step 4: Run the context test to green**

Run: `npx vitest run src/features/studio/release-draft-context.test.tsx`
Expected: PASS (create-once, placeholder→replace, submit flush).

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/features/studio/release-draft-context.tsx \
        Frontend/src/features/studio/release-draft-context.test.tsx
git commit -m "feat(studio): backend-backed release-draft context (create/upload/submit) (slice 3b)"
```

---

### Task 4: Wizard chrome — real create / save / submit

**Files:**
- Modify: `Frontend/src/routes/studio.release.new.tsx`

**Interfaces:**
- Consumes: `useReleaseDraft().{ createOrUpdateDraft, submitRelease, reset }`, `trackCountError` (Task 2/3), `ApiError`.
- Produces: no new exports; rewires `handleContinue` + Save-draft button + adds a `busy` guard.

> This step is UI wiring — verified by `npm run build` + live QA rather than a unit test (the context logic it drives is already covered by Task 3).

- [ ] **Step 1: Update imports** — at the top of `studio.release.new.tsx`, add:

```ts
import { useState } from 'react'
import { ApiError } from '../lib/api/errors'
import { RELEASE_WIZARD_STEPS, releaseTypeLabel, isMultiTrack, trackCountError, type ReleaseStepSlug } from '../lib/studio-data'
```

(Merge the `useState` into the existing `react` import line, and add `trackCountError` to the existing `studio-data` import rather than duplicating it.)

- [ ] **Step 2: Pull the new actions + a busy flag** — in `WizardChrome`, change the `useReleaseDraft()` destructure and add local state:

```ts
const { draft, reset, createOrUpdateDraft, submitRelease } = useReleaseDraft()
const [busy, setBusy] = useState(false)
const errMsg = (e: unknown, fb: string) => (e instanceof ApiError ? e.message : fb)
```

- [ ] **Step 3: Replace `handleContinue`** with the async version:

```ts
const handleContinue = async () => {
  if (busy) return

  if (slug === 'details') {
    if (!draft.title.trim()) { toast('Add a release title to continue', 'error'); return }
    setBusy(true)
    try { await createOrUpdateDraft() }
    catch (e) { toast(errMsg(e, 'Could not save draft'), 'error'); return }
    finally { setBusy(false) }
    goTo('tracks'); return
  }

  if (slug === 'tracks') {
    if (draft.tracks.length === 0) { toast('Upload at least one track to continue', 'error'); return }
    if (draft.tracks.some((t) => t.status === 'uploading')) { toast('Wait for uploads to finish', 'error'); return }
    goTo('splits'); return
  }

  if (slug === 'splits') {
    const bad = draft.tracks.find((t) => (draft.splits[t.id] ?? []).reduce((s, e) => s + e.percent, 0) !== 100)
    if (bad) { toast(`Splits for “${bad.title || 'a track'}” must total 100%`, 'error'); return }
    goTo('review'); return
  }

  // review (isLast) — keep both client gates (cover art + splits), plus the count matrix
  if (!draft.coverImage) { toast('Add cover art before submitting', 'error'); return }
  if (draft.tracks.length === 0) { toast('Upload at least one track', 'error'); return }
  const badSplit = draft.tracks.find((t) => (draft.splits[t.id] ?? []).reduce((s, e) => s + e.percent, 0) !== 100)
  if (badSplit) { toast(`Splits for “${badSplit.title || 'a track'}” must total 100%`, 'error'); return }
  if (!draft.agreementAccepted) { toast('Accept the distribution agreement to submit', 'error'); return }
  const countErr = trackCountError(draft.releaseType, draft.tracks.length)
  if (countErr) { toast(countErr, 'error'); return }

  setBusy(true)
  try {
    await submitRelease()
  } catch (e) {
    toast(errMsg(e, 'Could not submit release'), 'error')
    return
  } finally {
    setBusy(false)
  }
  queryClient.invalidateQueries({ queryKey: studioReleasesQuery().queryKey })
  toast('Release submitted for review', 'success')
  reset()
  navigate({ to: '/studio/releases' })
}
```

- [ ] **Step 4: Wire the Save-draft button** — replace its `onClick`:

```tsx
<button
  onClick={async () => {
    if (busy) return
    setBusy(true)
    try { await createOrUpdateDraft(); toast('Draft saved', 'success') }
    catch (e) { toast(errMsg(e, 'Could not save draft'), 'error') }
    finally { setBusy(false) }
  }}
  className="h-11 px-5 rounded-full bg-gray-100 dark:bg-white/10 text-beatz-dark-bg dark:text-white font-bold text-sm hover:bg-gray-200 dark:hover:bg-white/15 transition-colors"
>
  Save draft
</button>
```

- [ ] **Step 5: Disable the primary button while busy** — on the Continue/Submit `<button>`, add `disabled={busy}` and a disabled style, e.g. append `disabled:opacity-50 disabled:hover:scale-100` to its className and set `disabled={busy}`.

- [ ] **Step 6: Typecheck + lint**

Run: `cd Frontend && npm run build && npm run lint`
Expected: build clean; lint 0 new warnings (≤214 total).

- [ ] **Step 7: Commit**

```bash
git add Frontend/src/routes/studio.release.new.tsx
git commit -m "feat(studio): wizard chrome drives real create/save/submit (slice 3b)"
```

---

### Task 5: Tracks step — real upload / delete

**Files:**
- Modify: `Frontend/src/routes/studio.release.new.tracks.tsx`

**Interfaces:**
- Consumes: `useReleaseDraft().{ uploadTrack, removeTrack, updateTrack, moveTrack, reorderTracks, setAllPrices, setField }`.
- Produces: no exports; swaps the simulated upload for real multipart uploads and indeterminate progress.

> UI wiring — verified by `npm run build` + live QA (the upload/replace/error logic is covered by Task 3).

- [ ] **Step 1: Update the destructure + remove the fake-upload machinery** — in `UploadTracksStep`, change:

```ts
const { draft, uploadTrack, removeTrack, updateTrack, moveTrack, reorderTracks, setAllPrices, setField } = useReleaseDraft()
```

Delete `makeTrack`, the `hasUploading`/`useEffect(tickUploads)` block, and the `readMeta` function entirely (duration now comes from the server response).

- [ ] **Step 2: Rewrite `ingest`** to upload for real (sequential to preserve order; single mode replaces the existing track first):

```ts
const ingest = async (files: FileList | null) => {
  if (!files || files.length === 0) return
  const list = multi ? Array.from(files) : [files[0]]
  if (!multi) { await Promise.all(draft.tracks.map((t) => removeTrack(t.id))) } // single → replace
  for (const f of list) await uploadTrack(f)
}
```

- [ ] **Step 3: Update the two `ingest` call sites** to swallow the promise — the `onChange`/`onDrop` handlers already ignore return values, so no change is needed there; but `removeTrack` is now async — the JSX handlers (`onRemove={removeTrack}`, `onRemove={() => onRemove(track.id)}`, etc.) call it fire-and-forget, which is fine (React ignores the returned promise). Leave them as-is.

- [ ] **Step 4: Make the uploading pill indeterminate** — replace the `'uploading'` branch of `StatusPill`:

```tsx
if (track.status === 'uploading') {
  return <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300">uploading…</span>
}
```

- [ ] **Step 5: Typecheck + lint**

Run: `cd Frontend && npm run build && npm run lint`
Expected: build clean (no references to removed `addTracks`/`tickUploads`); lint 0 new warnings.

- [ ] **Step 6: Full frontend test run**

Run: `cd Frontend && npx vitest run`
Expected: all green (new + existing).

- [ ] **Step 7: Commit**

```bash
git add Frontend/src/routes/studio.release.new.tracks.tsx
git commit -m "feat(studio): tracks step uploads/deletes against the real backend (slice 3b)"
```

---

### Task 6: Live QA, verify gate, and PR

**Files:** none (verification + PR).

- [ ] **Step 1: Build + lint + unit tests once more**

Run: `cd Frontend && npm run build && npm run lint && npx vitest run`
Expected: build clean, 0 new lint warnings, all tests pass.

- [ ] **Step 2: Live QA against a running stack** — the user brings up the backend (`docker compose up` from repo root, or `cd backend && ./mvnw quarkus:dev`) and the frontend dev server, signs in as an artist, and walks the wizard:
  - Details → Continue creates a `draft` (visible via network POST `/v1/studio/releases`).
  - Tracks: upload a WAV → row appears, flips to "ready"; remove a track hits DELETE.
  - Splits → Review → Submit: `PATCH` with `tracks[]` then `POST /submit`; lands on `/studio/releases` with the new release in `in_review`.
  - Error paths: submit with the wrong track count for the type surfaces the friendly matrix message; a non-WAV/FLAC upload surfaces "Only WAV/FLAC accepted".

- [ ] **Step 3: Open the PR**

```bash
git push -u origin feat/frontend-studio-slice3b-wizard
gh pr create --base master --head feat/frontend-studio-slice3b-wizard \
  --title "feat(studio): slice 3b — wire release-creation wizard to WU-CAT-5 draft flow" \
  --body "Wires the release-creation wizard to the real draft flow (create-draft → multipart upload → PATCH → submit). Splits/cover/pre-save stay client-only (WU-CAT-6 / cover-upload follow-ups). Spec: docs/superpowers/specs/2026-07-18-frontend-studio-slice3b-wizard-design.md.

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
```

---

## Follow-ups (out of this slice)

- **WU-CAT-6** — per-track royalty splits + confirmation flow (backend), then wire the Splits step's persistence and re-gate submit on real split data.
- **Cover-image upload** endpoint → wire cover art; re-gate submit on a persisted cover.
- Pre-save links; resume-an-existing-draft entry point; determinate upload progress via `XMLHttpRequest`.
