# Frontend Studio Podcasts Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Swap the two `studio.podcasts` routes from the mock `useStudio()` episode store to the live WU-STU-2 endpoints (`/v1/studio/podcasts/*`), with no visual change.

**Architecture:** Same idiom as the merged studio slices — TanStack `queryOptions` + `apiFetch<Wire>` + `mappers.toX` for reads; mutations = async `apiFetch` fns + `queryClient.invalidateQueries` on success. New query module `lib/api/queries/podcasts-studio.ts`; episodes are extracted from `features/studio/studio-context.tsx` (payouts stay).

**Tech Stack:** React 19 + TanStack Query v5 + TanStack Router, TypeScript, Vitest + RTL, Vite proxy `/v1` → backend. Node 22 via nvm.

**Spec:** `docs/superpowers/specs/2026-07-21-frontend-studio-podcasts-wiring-design.md`

## Global Constraints

- **No visual change.** Data-source swap only; JSX/classes preserved. The frontend is the functional spec.
- Mapper outputs match `Frontend/src/types`/`lib/studio-data.ts` types exactly: `StudioPodcastShow {id,title,category}` and `StudioEpisode {id,showId,showTitle,title,duration,status,premium,price,publishedAt,plays}`. Episode `price` is a plain cedi `number`.
- `apiFetch` (`lib/api/client.ts`) prepends `BASE_URL='/v1'` — query paths are written WITHOUT `/v1`. It already supports `FormData` bodies (skips JSON `Content-Type`) and an `idempotencyKey` option.
- `POST /studio/podcasts/episodes` is **multipart**: an `audio` file part + a `data` JSON part, and **requires an `Idempotency-Key` header**. `CreateEpisodeBody` has **no `duration`/`status`** — the server derives them; never send them. `cover` is a URL string, not a file part.
- `showId` **XOR** `newShow` in the create body. `date` sent as ISO-8601 only when `visibility=scheduled`.
- No `@testing-library/jest-dom` → RTL uses `toBeTruthy()` / `queryByText`.
- All commands from `Frontend/`. Test: `npx vitest run <path>`. Real typecheck gate: `npm run build` (`tsc -b`) — NOT `tsc --noEmit`. Lint: `npm run lint` (repo has ~214 pre-existing errors; add no NEW ones).
- Branch: `feat/frontend-studio-podcasts` off master (already created; spec committed).
- Keep the consumer `lib/api/queries/podcasts.ts` UNTOUCHED — this is a new sibling module.

---

## File Structure

- `Frontend/src/lib/api/mappers.ts` (modify) — add `StudioPodcastShowWire`/`toStudioShow`, `EpisodeWire`/`toStudioEpisode`.
- `Frontend/src/lib/api/mappers.test.ts` (modify) — cases for the two new mappers.
- `Frontend/src/lib/api/queries/podcasts-studio.ts` (new) — `studioShowsQuery`, `studioEpisodesQuery`, `apiCreateEpisode`, `apiDeleteEpisode`, plus the `NewEpisodeInput` input type.
- `Frontend/src/lib/api/queries/podcasts-studio.test.ts` (new) — query URLs, FormData assembly + idempotency, delete.
- `Frontend/src/routes/studio.podcasts.index.tsx` (modify) — read shows/episodes from queries; delete via mutation.
- `Frontend/src/routes/studio.podcasts.new.tsx` (modify) — submit via `apiCreateEpisode`.
- `Frontend/src/features/studio/studio-context.tsx` (modify) — remove episodes; keep payouts.

**Task order rationale:** mappers → query layer → wire index → wire new → remove episodes from context. Each task compiles green: the context keeps `episodes`/`addEpisode`/`removeEpisode` until Tasks 3–4 have removed all consumers, so Task 5's deletion is safe.

---

## Task 1: Episode + show mappers

**Files:**
- Modify: `Frontend/src/lib/api/mappers.ts`
- Test: `Frontend/src/lib/api/mappers.test.ts`

**Interfaces:**
- Consumes: `StudioPodcastShow`, `StudioEpisode`, `EpisodeStatus` from `../../studio-data`.
- Produces: `type StudioPodcastShowWire`, `toStudioShow`, `type EpisodeWire`, `toStudioEpisode`.

**Prep:** Open `lib/studio-data.ts` and confirm the exact `StudioEpisode` / `StudioPodcastShow` / `EpisodeStatus` shapes. Confirm `mappers.ts` import style for the `studio-data` types (see existing `toStudioRelease`).

- [ ] **Step 1: Write failing mapper tests** in `mappers.test.ts`:

```ts
import { toStudioShow, toStudioEpisode } from './mappers'

describe('toStudioShow', () => {
  it('maps id/title/category 1:1', () => {
    expect(toStudioShow({ id: 'sh1', title: 'Konongo Diaries', category: 'Storytelling' }))
      .toEqual({ id: 'sh1', title: 'Konongo Diaries', category: 'Storytelling' })
  })
})

describe('toStudioEpisode', () => {
  it('maps all fields, price wire→number, status passthrough', () => {
    const wire = { id: 'ep1', showId: 'sh1', showTitle: 'Konongo Diaries', title: 'Ep 12',
      duration: 2940, status: 'published', premium: true, price: 5, publishedAt: 'May 02', plays: 18400 }
    expect(toStudioEpisode(wire)).toEqual({
      id: 'ep1', showId: 'sh1', showTitle: 'Konongo Diaries', title: 'Ep 12', duration: 2940,
      status: 'published', premium: true, price: 5, publishedAt: 'May 02', plays: 18400,
    })
  })
  it('coerces a string price to number', () => {
    expect(toStudioEpisode({ id: 'e', showId: 's', showTitle: 'S', title: 'T', duration: 1,
      status: 'draft', premium: false, price: '0', publishedAt: 'x', plays: 0 }).price).toBe(0)
  })
})
```

- [ ] **Step 2: Run to verify fail:** `npx vitest run src/lib/api/mappers.test.ts` → FAIL (not exported).

- [ ] **Step 3: Add mappers** to `mappers.ts` (mirror existing mapper style; `Number(...)` guards the `BigDecimal` JSON, which may arrive as number or string):

```ts
import type { StudioPodcastShow, StudioEpisode, EpisodeStatus } from '../../studio-data'

export interface StudioPodcastShowWire { id: string; title: string; category: string }
export function toStudioShow(w: StudioPodcastShowWire): StudioPodcastShow {
  return { id: w.id, title: w.title, category: w.category }
}

export interface EpisodeWire {
  id: string; showId: string; showTitle: string; title: string; duration: number
  status: string; premium: boolean; price: number | string; publishedAt: string; plays: number
}
export function toStudioEpisode(w: EpisodeWire): StudioEpisode {
  return {
    id: w.id, showId: w.showId, showTitle: w.showTitle, title: w.title, duration: w.duration,
    status: w.status as EpisodeStatus, premium: w.premium, price: Number(w.price),
    publishedAt: w.publishedAt, plays: w.plays,
  }
}
```

- [ ] **Step 4: Run to verify pass:** `npx vitest run src/lib/api/mappers.test.ts` → PASS.

- [ ] **Step 5: Commit** — `git add` the two files; `feat(studio): episode/show wire mappers`.

---

## Task 2: `podcasts-studio.ts` query + mutation layer

**Files:**
- Create: `Frontend/src/lib/api/queries/podcasts-studio.ts`
- Test: `Frontend/src/lib/api/queries/podcasts-studio.test.ts`

**Interfaces:**
- Consumes: `apiFetch`; `toStudioShow`/`StudioPodcastShowWire`, `toStudioEpisode`/`EpisodeWire` (Task 1); `StudioPodcastShow`, `StudioEpisode` types.
- Produces:
  - `studioShowsQuery()` → `queryOptions` for `StudioPodcastShow[]`
  - `studioEpisodesQuery()` → `queryOptions` for `StudioEpisode[]`
  - `interface NewEpisodeInput { audio: File; showId: string | null; newShow: { title: string; category: string } | null; title: string; description: string; cover: string | null; visibility: 'public' | 'scheduled'; date: string | null; premium: boolean; price: number | null; earlyAccess: boolean }`
  - `apiCreateEpisode(input: NewEpisodeInput): Promise<StudioEpisode>`
  - `apiDeleteEpisode(id: string): Promise<void>`

**Prep:** Read `lib/api/queries/studio.ts` for the exact `queryOptions` + `apiFetch` + `.then(mapper)` idiom and the `apiDeleteRelease` shape. Confirm `crypto.randomUUID` is used elsewhere (release-wizard upload) for the idempotency key.

- [ ] **Step 1: Write failing tests** `podcasts-studio.test.ts` (mock `../client`):

```ts
import { vi, describe, it, expect, beforeEach } from 'vitest'
import * as client from '../client'
import { studioShowsQuery, studioEpisodesQuery, apiCreateEpisode, apiDeleteEpisode } from './podcasts-studio'

vi.mock('../client', () => ({ apiFetch: vi.fn() }))
const apiFetch = vi.mocked(client.apiFetch)

const EP = { id: 'ep1', showId: 'sh1', showTitle: 'S', title: 'T', duration: 1, status: 'published',
  premium: false, price: 0, publishedAt: 'x', plays: 0 }

beforeEach(() => apiFetch.mockReset())

describe('studioShowsQuery', () => {
  it('GETs /studio/podcasts/shows and maps', async () => {
    apiFetch.mockResolvedValue([{ id: 'sh1', title: 'S', category: 'C' }])
    const res = await studioShowsQuery().queryFn!({} as never)
    expect(apiFetch).toHaveBeenCalledWith('/studio/podcasts/shows')
    expect(res).toEqual([{ id: 'sh1', title: 'S', category: 'C' }])
  })
})

describe('studioEpisodesQuery', () => {
  it('GETs /studio/podcasts/episodes and maps', async () => {
    apiFetch.mockResolvedValue([EP])
    const res = await studioEpisodesQuery().queryFn!({} as never)
    expect(apiFetch).toHaveBeenCalledWith('/studio/podcasts/episodes')
    expect(res[0].id).toBe('ep1')
  })
})

describe('apiCreateEpisode', () => {
  it('POSTs multipart with audio + data parts and an Idempotency-Key', async () => {
    apiFetch.mockResolvedValue(EP)
    const audio = new File(['x'], 'ep.mp3', { type: 'audio/mpeg' })
    await apiCreateEpisode({ audio, showId: 'sh1', newShow: null, title: 'T', description: 'D',
      cover: null, visibility: 'public', date: null, premium: false, price: null, earlyAccess: false })
    const [path, opts] = apiFetch.mock.calls[0]
    expect(path).toBe('/studio/podcasts/episodes')
    expect(opts.method).toBe('POST')
    expect(opts.body).toBeInstanceOf(FormData)
    const fd = opts.body as FormData
    expect(fd.get('audio')).toBeInstanceOf(File)
    const data = JSON.parse(fd.get('data') as string)
    expect(data).toMatchObject({ showId: 'sh1', newShow: null, title: 'T', visibility: 'public' })
    expect(data.duration).toBeUndefined()
    expect(data.status).toBeUndefined()
    expect(typeof opts.idempotencyKey).toBe('string')
    expect((opts.idempotencyKey as string).length).toBeGreaterThan(0)
  })

  it('sends newShow + null showId + ISO date when scheduling a new show', async () => {
    apiFetch.mockResolvedValue(EP)
    const audio = new File(['x'], 'ep.mp3', { type: 'audio/mpeg' })
    await apiCreateEpisode({ audio, showId: null, newShow: { title: 'New', category: 'Storytelling' },
      title: 'T', description: '', cover: null, visibility: 'scheduled', date: '2030-01-02',
      premium: true, price: 5, earlyAccess: true })
    const data = JSON.parse((apiFetch.mock.calls[0][1].body as FormData).get('data') as string)
    expect(data.showId).toBeNull()
    expect(data.newShow).toEqual({ title: 'New', category: 'Storytelling' })
    expect(data.date).toBe('2030-01-02T00:00:00.000Z')
    expect(data.premium).toBe(true)
    expect(data.price).toBe(5)
  })
})

describe('apiDeleteEpisode', () => {
  it('DELETEs by id', async () => {
    apiFetch.mockResolvedValue(undefined)
    await apiDeleteEpisode('ep1')
    expect(apiFetch).toHaveBeenCalledWith('/studio/podcasts/episodes/ep1', { method: 'DELETE' })
  })
})
```

- [ ] **Step 2: Run to verify fail:** `npx vitest run src/lib/api/queries/podcasts-studio.test.ts` → FAIL (module missing).

- [ ] **Step 3: Implement** `podcasts-studio.ts`:

```ts
import { queryOptions } from '@tanstack/react-query'
import type { StudioPodcastShow, StudioEpisode } from '../../studio-data'
import { apiFetch } from '../client'
import {
  toStudioShow, type StudioPodcastShowWire,
  toStudioEpisode, type EpisodeWire,
} from '../mappers'

/** `GET /v1/studio/podcasts/shows` — the signed-in creator's shows. */
export function studioShowsQuery() {
  return queryOptions({
    queryKey: ['studio', 'podcast-shows'],
    queryFn: async () =>
      (await apiFetch<StudioPodcastShowWire[]>('/studio/podcasts/shows')).map(toStudioShow),
  })
}

/** `GET /v1/studio/podcasts/episodes` — the creator's episodes (with plays + status). */
export function studioEpisodesQuery() {
  return queryOptions({
    queryKey: ['studio', 'podcast-episodes'],
    queryFn: async () =>
      (await apiFetch<EpisodeWire[]>('/studio/podcasts/episodes')).map(toStudioEpisode),
  })
}

export interface NewEpisodeInput {
  audio: File
  showId: string | null
  newShow: { title: string; category: string } | null
  title: string
  description: string
  cover: string | null
  visibility: 'public' | 'scheduled'
  date: string | null
  premium: boolean
  price: number | null
  earlyAccess: boolean
}

/**
 * `POST /v1/studio/podcasts/episodes` — multipart create. The `audio` file part carries the upload;
 * the `data` part is the JSON body. `duration`/`status` are server-derived and deliberately omitted.
 * A fresh `Idempotency-Key` guards against double-submit.
 */
export function apiCreateEpisode(input: NewEpisodeInput): Promise<StudioEpisode> {
  const data = {
    showId: input.showId,
    newShow: input.newShow,
    title: input.title,
    description: input.description,
    cover: input.cover,
    visibility: input.visibility,
    date: input.visibility === 'scheduled' && input.date ? new Date(input.date).toISOString() : null,
    premium: input.premium,
    price: input.premium ? input.price : null,
    earlyAccess: input.earlyAccess,
  }
  const form = new FormData()
  form.append('audio', input.audio)
  form.append('data', JSON.stringify(data))
  return apiFetch<EpisodeWire>('/studio/podcasts/episodes', {
    method: 'POST', body: form, idempotencyKey: crypto.randomUUID(),
  }).then(toStudioEpisode)
}

/** `DELETE /v1/studio/podcasts/episodes/:id`. */
export function apiDeleteEpisode(id: string): Promise<void> {
  return apiFetch<void>(`/studio/podcasts/episodes/${id}`, { method: 'DELETE' })
}
```

- [ ] **Step 4: Run to verify pass:** `npx vitest run src/lib/api/queries/podcasts-studio.test.ts` → PASS.

- [ ] **Step 5: Commit** — `feat(studio): podcasts-studio query + mutation layer`.

---

## Task 3: Wire `studio.podcasts.index.tsx` to queries

**Files:**
- Modify: `Frontend/src/routes/studio.podcasts.index.tsx`

**Interfaces:**
- Consumes: `studioShowsQuery`, `studioEpisodesQuery`, `apiDeleteEpisode` (Task 2); `useQuery`, `useQueryClient` from `@tanstack/react-query`.

**Prep:** Read the whole current file. It uses `useStudio().episodes` + `removeEpisode` and `getStudioShows()`. The empty-state branch checks `episodes.length === 0 && shows.length === 0`. `EpisodeRow`/`StatusPill`/`Header` and all classes must stay byte-identical.

- [ ] **Step 1: Replace the data sources.** Remove `import { getStudioShows, ... } from '../lib/studio-data'` for the data (keep the `type StudioEpisode, type EpisodeStatus` type imports — still used by `EpisodeRow`/`StatusPill`). Remove `useStudio` usage. Add:

```ts
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { studioShowsQuery, studioEpisodesQuery, apiDeleteEpisode } from '../lib/api/queries/podcasts-studio'
```

In `StudioPodcasts()`:

```ts
const queryClient = useQueryClient()
const { data: shows = [] } = useQuery(studioShowsQuery())
const { data: episodes = [] } = useQuery(studioEpisodesQuery())

const onDelete = async (id: string) => {
  await apiDeleteEpisode(id)
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: studioEpisodesQuery().queryKey }),
    queryClient.invalidateQueries({ queryKey: studioShowsQuery().queryKey }),
  ])
  toast('Episode deleted', 'success')
}
```

Wire the row's `onDelete={() => onDelete(e.id)}` (drop the old `removeEpisode` + toast pair). Keep `keep StudioEpisode`/`EpisodeStatus` type imports. The empty-state check (`episodes.length === 0 && shows.length === 0`) is unchanged — with `data = []` defaults it holds while loading (renders empty-state briefly, then fills; acceptable, no visual regression vs. the mock which was instant).

- [ ] **Step 2: Typecheck:** `npm run build` → PASS (no unused-import / type errors).

- [ ] **Step 3: Smoke test the render.** `npx vitest run` (whole suite) still green; no test targets this route directly (route components aren't unit-tested in this repo — verified by absence of `studio.podcasts.*.test.tsx`). Confirm no NEW lint errors: `npm run lint` diff.

- [ ] **Step 4: Commit** — `feat(studio): wire podcasts index to live shows/episodes + delete`.

---

## Task 4: Wire `studio.podcasts.new.tsx` submit to `apiCreateEpisode`

**Files:**
- Modify: `Frontend/src/routes/studio.podcasts.new.tsx`

**Interfaces:**
- Consumes: `apiCreateEpisode`, `studioShowsQuery`, `studioEpisodesQuery` (Task 2); `useQuery`, `useQueryClient`.

**Prep:** Read the whole current file. The show `<select>` is populated from `getStudioShows()`; the form keeps `File` objects only as object URLs today (`onAudio` sets `{ src, name, duration }` from `URL.createObjectURL`). To upload, the raw `File` must be retained. Add a `useRef<File | null>` (or extend the audio state) to hold the actual `File` alongside the preview. Keep every class and the `canSubmit` logic.

- [ ] **Step 1: Retain the audio File.** Change `onAudio` to also stash the `File`:

```ts
const audioFileRef = useRef<File | null>(null)
const onAudio = (f?: File) => {
  if (!f) return
  audioFileRef.current = f
  const src = URL.createObjectURL(f)
  setAudio({ src, name: f.name, duration: 0 })
  // ...existing duration probe unchanged...
}
```

(Clearing audio via the X button also resets `audioFileRef.current = null`.)

- [ ] **Step 2: Replace the show select data source.** Swap `const shows = getStudioShows()` for `const { data: shows = [] } = useQuery(studioShowsQuery())`. Add the imports; drop `getStudioShows` (keep `STUDIO_PODCAST_CATEGORIES`, and drop `type StudioEpisode` if now unused). Add `useQuery`, `useQueryClient`, and the `apiCreateEpisode`/`studioEpisodesQuery` imports.

- [ ] **Step 3: Rewrite `submit`** to call the API (make it `async`, add an in-flight guard to prevent double-submit):

```ts
const [submitting, setSubmitting] = useState(false)
const submit = async () => {
  const file = audioFileRef.current
  if (!canSubmit || !file) { toast('Add an episode title, audio and a show to publish', 'error'); return }
  setSubmitting(true)
  try {
    await apiCreateEpisode({
      audio: file,
      showId: showId === NEW_SHOW ? null : showId,
      newShow: showId === NEW_SHOW ? { title: newShowTitle.trim(), category: newShowCat } : null,
      title: title.trim(),
      description: description.trim(),
      cover, // client preview URL or null — backend takes a URL string
      visibility,
      date: visibility === 'scheduled' ? date : null,
      premium,
      price: premium ? price : null,
      earlyAccess,
    })
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: studioEpisodesQuery().queryKey }),
      queryClient.invalidateQueries({ queryKey: studioShowsQuery().queryKey }),
    ])
    toast(visibility === 'scheduled' ? 'Episode scheduled' : 'Episode published 🎙️', 'success')
    navigate({ to: '/studio/podcasts' })
  } catch (e) {
    toast(e instanceof Error ? e.message : 'Could not publish the episode', 'error')
  } finally {
    setSubmitting(false)
  }
}
```

Update the Publish/Schedule button `disabled={!canSubmit || submitting}` (keep its exact classes/label logic). Remove the `addEpisode`/`useStudio` usage and the local `StudioEpisode` object construction.

- [ ] **Step 4: Typecheck:** `npm run build` → PASS.

- [ ] **Step 5:** `npx vitest run` whole suite green; `npm run lint` no NEW errors.

- [ ] **Step 6: Commit** — `feat(studio): wire new-episode submit to multipart create`.

---

## Task 5: Remove episodes from `studio-context.tsx`

**Files:**
- Modify: `Frontend/src/features/studio/studio-context.tsx`

**Interfaces:**
- After Tasks 3–4 there are NO remaining consumers of `episodes`/`addEpisode`/`removeEpisode`. Verify: `grep -rn "addEpisode\|removeEpisode\|\.episodes" Frontend/src` returns only the context file itself.

**Prep:** Read the whole file. `StudioState` holds `episodes` + payouts. Remove episodes from the interface, `seed`, `hydrate`, the `useMemo` value, and drop the `getStudioEpisodes`/`StudioEpisode` import. Keep `balance`/`transactions`/`methods` and all payout actions exactly.

- [ ] **Step 1: Verify no consumers remain:**

```bash
grep -rn "addEpisode\|removeEpisode\|useStudio().*episode\|\.episodes" Frontend/src
```
Expected: only `studio-context.tsx` (its own definitions). If a route still references them, that route's task (3/4) is incomplete — go back.

- [ ] **Step 2: Delete the episode slice.** Remove `episodes` from `StudioState` + `StudioContextValue`, from `seed()` (drop `getStudioEpisodes()`), from `hydrate()` (drop the `episodes` line), and remove `addEpisode`/`removeEpisode` from the `useMemo`. Drop the `import { getStudioEpisodes, type StudioEpisode } from '../../lib/studio-data'` line. Leave the `PERSIST_KEY` and payout persistence intact.

- [ ] **Step 3: Typecheck:** `npm run build` → PASS.

- [ ] **Step 4:** `npx vitest run` whole suite green; `npm run lint` no NEW errors.

- [ ] **Step 5: Commit** — `refactor(studio): drop episodes from studio-context (now query-backed)`.

---

## Task 6: Live QA + verification gate + PR

**Files:** none (verification only).

- [ ] **Step 1: Full frontend gate** (from `Frontend/`, Node 22 via nvm):
  - `npm run build` → clean (real `tsc -b` typecheck + Vite build).
  - `npx vitest run` → all green.
  - `npm run lint` → no NEW errors vs. master baseline.

- [ ] **Step 2: Live QA** against the running stack (`docker compose up` from repo root; frontend `npm run dev`, signed in as an artist):
  - `/studio/podcasts` renders the creator's real shows + episodes (empty state if none).
  - New episode with audio upload + inline "+ New show" → publishes → appears in the list; the new show appears under "Your shows".
  - Schedule a future-dated episode → shows `scheduled` pill.
  - Delete an episode → row disappears and stays gone after refresh.
  - No visual difference from the mock version.

- [ ] **Step 3: Push + PR.** Push `feat/frontend-studio-podcasts`; open a PR titled `feat(studio): wire studio podcasts to WU-STU-2 (create/list/delete)`, body summarizing the data-swap, the deferred edit, no-backend-change, and the test/QA evidence. NEVER stage `backend/src/main/resources/application.properties` or `backend/docker-compose.yml`.

- [ ] **Step 4:** After CI green + review, squash-merge (strict branch protection — auto-merge only when all required checks pass).

---

## Self-Review notes

- **Spec coverage:** shows list (T2/T3), episodes list (T2/T3), create w/ audio + inline new show + idempotency (T2/T4), delete (T2/T3), context extraction (T5), edit deferred (untouched — no task, correct). ✓
- **Type consistency:** `toStudioEpisode`/`EpisodeWire` (T1) consumed by `studioEpisodesQuery` + `apiCreateEpisode` (T2); `NewEpisodeInput` (T2) consumed by `new.tsx` submit (T4) — field names match. ✓
- **No placeholders:** every code step carries complete code. ✓
