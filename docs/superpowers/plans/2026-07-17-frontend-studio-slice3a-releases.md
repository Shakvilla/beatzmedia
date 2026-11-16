# Studio Slice 3a — Releases list / detail / delete / rename Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the Studio **Releases list** and **release detail/manage** screens to the live catalog backend (`GET /v1/studio/releases`, `GET/PATCH/DELETE /v1/studio/releases/:id`), and remove `releases` from the `studio-context.tsx` god-context. No intended visual change.

**Architecture:** Same query idiom as Slices 1–2 (`queries/studio.ts` + `mappers.ts` + `apiFetch`, reusing `unwrapPage`). Continues the incremental god-context decomposition: after this slice the context holds only `episodes` (Slice 4) and payout state (Slice 5). **The create wizard is explicitly OUT of scope** — it needs multipart-upload infra + product decisions and is a separate slice (3b). This slice only stubs the wizard's publish call so the context can be cleanly shrunk.

**Tech Stack:** React 19 + React Compiler, TanStack Query v5, TanStack Router (file routes, loader + ensureQueryData), TypeScript, Vitest.

## Global Constraints

- **The real typecheck gate is `npm run build`** (`tsc -b && vite build`), NOT `npx tsc --noEmit`. The root tsconfig used by `tsc --noEmit` is looser and silently passes import-path and `noImplicitAny` errors that the build catches. Every task verifies with `npm run build` before commit. (A benign `chunk size > 500 kB` Vite warning is pre-existing, not a failure.)
- **Node 22.17.1** via nvm on PATH for all `npm`/`vitest`/`build` commands (default shell node is v10 and crashes the toolchain: `source ~/.nvm/nvm.sh && nvm use 22.17.1`).
- **`apiFetch`** prepends `/v1`; paths written WITHOUT `/v1`. Supports `{ method, body }`, returns parsed JSON (or `undefined` on 204), throws `ApiError(status, code, message, field?)` on non-2xx.
- **MoneyView → plain number.** `StudioReleaseView.revenue`/`price` are `MoneyView { amount: <cedis, 2dp>, currency: "GHS" }`; the frontend `StudioRelease.revenue`/`price` are `number` (cedis). The mapper unwraps `.amount` — the same plain-number precedent as settings' `trackPrice`. `MoneyView.amount` is already in **major units (cedis)** — do NOT divide by 100.
- **Enum wire format matches the frontend unions exactly** — backend `ReleaseType` (`single|ep|album|mixtape`) and `ReleaseStatus` (`draft|in_review|scheduled|live|takedown`) are lowercase Java enums serialized by name. No case mapping. **But `takedown` has no frontend equivalent** — guard status lookups so an unexpected status can't crash `STATUS_META[status]`.
- **Reuse `unwrapPage`** from `lib/api/pagination.ts` (`unwrapPage(page, map)`), as store/events/podcasts queries already do. Backend `PageView<T>` = `{ items, page, size, total }` = `PageWire<T>`.
- **React Compiler is enabled** — no new manual `useMemo`/`useCallback`. Existing ones (e.g. the detail page's `useMemo` finding the release, the list's memos) either stay or are replaced by query reads; do not add new memoization.
- **No `@testing-library/jest-dom`** — assert with `toBe`/`toEqual`/`toMatchObject`.
- **Lint baseline is 214.** No new problems.

---

## Backend contract (verified — `catalog` module, `StudioReleaseResource`, `@RolesAllowed("artist")`, artist id from `jwt.getSubject()`)

- **`GET /v1/studio/releases?status=&page=&size=`** → 200 `PageView<StudioReleaseView>`. `status` optional (`live|scheduled|in_review|draft|takedown`, `-`→`_` tolerated); `page` default 0, `size` default 20.
- **`GET /v1/studio/releases/:id`** → 200 `StudioReleaseView`; 404 if not the caller's release.
- **`PATCH /v1/studio/releases/:id`** (JSON `{ title }`) → 200 `StudioReleaseView`. **Only the title is updatable via this endpoint.**
- **`DELETE /v1/studio/releases/:id`** → 204. **Only `draft`/`in_review` releases are deletable**; deleting a `live` release fails (backend `ReleaseLiveException`) — the mapper surfaces it as an `ApiError`; handle it (toast + no navigation).
- `StudioReleaseView` = `{ id, title, type: ReleaseType, status: ReleaseStatus, date: string, trackCount: number, streams: number, revenue: MoneyView, price: MoneyView }`. **Field-for-field with the frontend `StudioRelease` except `revenue`/`price` are `MoneyView` (unwrap `.amount`).**
- **Not exposed to artists (no endpoint):** publish/unpublish (status change is admin-gated: submit → `in_review` → admin approves → `live`), and price/date edits. `POST /releases` (submit) and `POST /releases/:id/tracks` (multipart upload) are the **create wizard's** endpoints — **Slice 3b, out of scope here.**

### Documented limitations for this slice (design decisions, flagged to the user)

1. **The detail page's price / release-date / publish-unpublish controls have no artist endpoint.** Only **rename** (PATCH title) and **delete** persist. Price/date/status edits update the local draft so the UI behaves, but are **not persisted** (revert on refetch) — the same Category-A/B pattern as Slice 2 settings. Publish/unpublish is admin-gated by design; per-track price lives in the (deferred) wizard/track model, not on the release. Documented in-code (TODO) and in the PR.
2. **The list fetches up to `size=100` and computes the summary client-side.** The page shows all releases with client-side filter/search + a totals summary, so it fetches one large page rather than paginating. If a creator ever exceeds 100 releases the tail is dropped from the summary — `log`/comment this cap rather than silently truncating. (Realistic creator counts are far below this; true pagination is a follow-up.)
3. **The create wizard is not wired.** Its "Publish" currently calls the god-context's `addRelease` (a mock prepend). Since this slice removes `addRelease`, the wizard's publish is stubbed to invalidate the releases query + navigate (real submit is Slice 3b). The wizard's client-side steps keep working as a mock.

---

## File Structure

- **Modify** `Frontend/src/lib/api/mappers.ts` — `StudioReleaseWire` + `toStudioRelease` (unwrap MoneyView).
- **Modify** `Frontend/src/lib/api/queries/studio.ts` — `studioReleasesQuery()`, `studioReleaseQuery(id)`, `apiRenameRelease(id, title)`, `apiDeleteRelease(id)`.
- **Modify** `Frontend/src/lib/api/queries/studio.test.ts` — tests for the release query/mapper + mutations.
- **Modify** `Frontend/src/routes/studio.releases.tsx` — loader + `useSuspenseQuery` + optimistic delete.
- **Modify** `Frontend/src/routes/studio.release.$releaseId.tsx` — loader + `useSuspenseQuery(studioReleaseQuery(id))`; rename via PATCH; delete via DELETE (handle live-delete error); price/date/status session-local; guard unknown status.
- **Modify** `Frontend/src/routes/studio.index.tsx` — read releases from the query (keep balance/transactions from context).
- **Modify** `Frontend/src/routes/studio.release.new.tsx` — replace `addRelease(...)` publish with invalidate + navigate.
- **Modify** `Frontend/src/features/studio/studio-context.tsx` — remove `releases`, `addRelease`, `updateRelease`, `removeRelease`.

---

## Task 1: Release mappers, queries, and mutations

**Files:** Modify `mappers.ts`, `queries/studio.ts`; Test `queries/studio.test.ts`.

**Interfaces produced:**
- `toStudioRelease(w: StudioReleaseWire): StudioRelease`
- `studioReleasesQuery(): queryOptions` (key `['studio','releases']`), `studioReleaseQuery(id: string): queryOptions` (key `['studio','release', id]`)
- `apiRenameRelease(id: string, title: string): Promise<StudioRelease>`, `apiDeleteRelease(id: string): Promise<void>`

- [ ] **Step 1: Mapper.** In `mappers.ts`, extend the `../studio-data` import to add `StudioRelease` (and `ReleaseType`, `ReleaseStatus` if referenced), then append:

```ts
// ── Studio releases ───────────────────────────────────────────────
// StudioReleaseView matches StudioRelease except revenue/price are
// MoneyView { amount(cedis), currency }; the frontend type wants plain
// cedis numbers (same precedent as settings' trackPrice), so unwrap .amount.
export interface StudioReleaseWire {
  id: string
  title: string
  type: StudioRelease['type']
  status: StudioRelease['status']
  date: string
  trackCount: number
  streams: number
  revenue: { amount: number; currency: string }
  price: { amount: number; currency: string }
}
export function toStudioRelease(w: StudioReleaseWire): StudioRelease {
  return {
    id: w.id,
    title: w.title,
    type: w.type,
    status: w.status,
    date: w.date,
    trackCount: w.trackCount,
    streams: w.streams,
    revenue: w.revenue?.amount ?? 0,
    price: w.price?.amount ?? 0,
  }
}
```

- [ ] **Step 2: Queries + mutations.** In `queries/studio.ts`, extend imports (`unwrapPage`, `PageWire` from `../pagination`; `StudioRelease` from `../../studio-data`; `toStudioRelease`, `StudioReleaseWire` from `../mappers`) and append:

```ts
/** `GET /v1/studio/releases` — the creator's releases (one large page; summary is client-side). */
export function studioReleasesQuery() {
  return queryOptions({
    queryKey: ['studio', 'releases'],
    queryFn: async () =>
      unwrapPage(await apiFetch<PageWire<StudioReleaseWire>>('/studio/releases?size=100'), toStudioRelease),
  })
}

/** `GET /v1/studio/releases/:id` — one release (404s if not the caller's). */
export function studioReleaseQuery(id: string) {
  return queryOptions({
    queryKey: ['studio', 'release', id],
    queryFn: async () => toStudioRelease(await apiFetch<StudioReleaseWire>(`/studio/releases/${id}`)),
  })
}

/** `PATCH /v1/studio/releases/:id` — rename (title is the only updatable field). */
export function apiRenameRelease(id: string, title: string): Promise<StudioRelease> {
  return apiFetch<StudioReleaseWire>(`/studio/releases/${id}`, { method: 'PATCH', body: { title } })
    .then(toStudioRelease)
}

/** `DELETE /v1/studio/releases/:id` — draft/in_review only; throws ApiError for live. */
export function apiDeleteRelease(id: string): Promise<void> {
  return apiFetch<void>(`/studio/releases/${id}`, { method: 'DELETE' })
}
```

- [ ] **Step 3: Tests.** Append to `studio.test.ts` (import the four new symbols). Cover: the list query requests `/studio/releases?size=100` and unwraps + maps (assert `revenue`/`price` are the unwrapped `.amount` numbers, `type`/`status` pass through); `studioReleaseQuery('r1')` requests `/studio/releases/r1`; `apiRenameRelease('r1','New')` PATCHes `{title:'New'}`; `apiDeleteRelease('r1')` DELETEs.

```ts
const releaseWire = {
  id: 'r1', title: 'Iron Boy', type: 'album', status: 'in_review', date: 'Jul 14, 2026',
  trackCount: 14, streams: 0, revenue: { amount: 0, currency: 'GHS' }, price: { amount: 2.5, currency: 'GHS' },
}

describe('studioReleasesQuery', () => {
  it('requests one large page and unwraps + maps money to numbers', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({ items: [releaseWire], page: 0, size: 100, total: 1 })
    const rows = await studioReleasesQuery().queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/studio/releases?size=100')
    expect(rows[0]).toEqual({ id: 'r1', title: 'Iron Boy', type: 'album', status: 'in_review', date: 'Jul 14, 2026', trackCount: 14, streams: 0, revenue: 0, price: 2.5 })
  })
})

describe('studioReleaseQuery / mutations', () => {
  it('gets one release by id', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(releaseWire)
    const r = await studioReleaseQuery('r1').queryFn!({} as never)
    expect(client.apiFetch).toHaveBeenCalledWith('/studio/releases/r1')
    expect(r.price).toBe(2.5)
  })
  it('renames via PATCH title-only', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue({ ...releaseWire, title: 'New' })
    await apiRenameRelease('r1', 'New')
    expect(client.apiFetch).toHaveBeenCalledWith('/studio/releases/r1', { method: 'PATCH', body: { title: 'New' } })
  })
  it('deletes via DELETE', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(undefined)
    await apiDeleteRelease('r1')
    expect(client.apiFetch).toHaveBeenCalledWith('/studio/releases/r1', { method: 'DELETE' })
  })
})
```

- [ ] **Step 4:** `npx vitest run src/lib/api/queries/studio.test.ts` — all pass. `npm run build` — green.
- [ ] **Step 5:** Commit `feat(frontend): studio release mappers, queries, rename/delete mutations`.

---

## Task 2: Releases list page (`studio.releases.tsx`)

**Files:** Modify `Frontend/src/routes/studio.releases.tsx`.

- [ ] **Step 1: Loader + data source.** Add the loader and replace `const { releases, removeRelease } = useStudio()`:

```tsx
import { useSuspenseQuery, useQueryClient } from '@tanstack/react-query'
import { studioReleasesQuery, apiDeleteRelease } from '../lib/api/queries/studio'
// remove: import { useStudio } from '../features/studio/studio-context'

export const Route = createFileRoute('/studio/releases')({
  loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(studioReleasesQuery()),
  component: /* existing component name */,
})
```

Inside the component:

```tsx
const queryClient = useQueryClient()
const { data: releases } = useSuspenseQuery(studioReleasesQuery())
```

- [ ] **Step 2: Optimistic delete.** Replace `const remove = (id) => { removeRelease(id); toast('Release deleted', 'success') }` with:

```tsx
const remove = async (id: string) => {
  const key = studioReleasesQuery().queryKey
  const previous = queryClient.getQueryData(key)
  queryClient.setQueryData(key, (rows: typeof releases | undefined) => rows?.filter((r) => r.id !== id))
  try {
    await apiDeleteRelease(id)
    toast('Release deleted', 'success')
  } catch {
    queryClient.setQueryData(key, previous)
    toast('Only drafts and in-review releases can be deleted.', 'error')
  }
}
```

- [ ] **Step 3:** Confirm no `useStudio` remains: `grep -n useStudio src/routes/studio.releases.tsx` → none. `npm run build` green.
- [ ] **Step 4:** Commit `feat(frontend): wire studio releases list to /v1/studio/releases`.

---

## Task 3: Release detail / manage page (`studio.release.$releaseId.tsx`)

**Files:** Modify `Frontend/src/routes/studio.release.$releaseId.tsx`.

- [ ] **Step 1: Loader + single-release query.** Fetch the release by id so the page works on direct navigation/refresh:

```tsx
import { useSuspenseQuery, useQueryClient } from '@tanstack/react-query'
import { studioReleaseQuery, studioReleasesQuery, apiRenameRelease, apiDeleteRelease } from '../lib/api/queries/studio'
// remove: import { useStudio } from '../features/studio/studio-context'

export const Route = createFileRoute('/studio/release/$releaseId')({
  loader: ({ context: { queryClient }, params: { releaseId } }) =>
    queryClient.ensureQueryData(studioReleaseQuery(releaseId)),
  component: ReleaseManage,
})
```

Inside `ReleaseManage`, replace the `useStudio`/`useMemo(find)` block:

```tsx
const queryClient = useQueryClient()
const { data: release } = useSuspenseQuery(studioReleaseQuery(releaseId))
const [draft, setDraft] = useState<StudioRelease>(release)
```

(The loader guarantees `release` is present; the "no longer exists" branch is unreachable on a 200 but keep a guard only if `useSuspenseQuery` can't 404-throw — a 404 throws to the router error boundary, so the manual not-found block can be removed. Verify the router has an error boundary; if unsure, keep a minimal guard.)

- [ ] **Step 2: Rename persists; other edits are session-local.** Replace the handlers:

```tsx
// NOTE: PATCH /v1/studio/releases/:id updates the TITLE only. Price, release
// date, and publish/unpublish (status) have no artist endpoint — publish is
// admin-gated (submit → in_review → admin approves → live), and per-track
// price lives in the create wizard (Slice 3b). Those controls edit the local
// draft so the UI behaves, but only the title is persisted.
const save = async () => {
  const previous = release
  try {
    const saved = await apiRenameRelease(release.id, draft.title)
    queryClient.setQueryData(studioReleaseQuery(release.id).queryKey, { ...saved, price: draft.price, date: draft.date, status: draft.status })
    queryClient.invalidateQueries({ queryKey: studioReleasesQuery().queryKey })
    toast('Release updated', 'success')
  } catch {
    setDraft(previous)
    toast('Could not update the release. Please try again.', 'error')
  }
}
const setStatus = (s: ReleaseStatus, msg: string) => { setDraft((d) => ({ ...d, status: s })); toast(msg, 'success') } // local only
const del = async () => {
  try {
    await apiDeleteRelease(release.id)
    queryClient.invalidateQueries({ queryKey: studioReleasesQuery().queryKey })
    toast('Release deleted', 'success')
    navigate({ to: '/studio/releases' })
  } catch {
    toast('Only drafts and in-review releases can be deleted.', 'error')
  }
}
```

- [ ] **Step 3: Guard unknown status.** `STATUS_META[release.status]` crashes for `takedown` (no frontend entry). Add a fallback:

```tsx
const status = STATUS_META[release.status] ?? { label: release.status, cls: 'bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-300' }
```

- [ ] **Step 4:** `grep -n useStudio src/routes/studio.release.$releaseId.tsx` → none. `npm run build` green.
- [ ] **Step 5:** Commit `feat(frontend): wire release detail rename/delete to backend (price/date/status session-local)`.

---

## Task 4: Dashboard releases → query (`studio.index.tsx`)

**Files:** Modify `Frontend/src/routes/studio.index.tsx`.

- [ ] **Step 1:** Current: `const { balance, transactions, releases } = useStudio()` (+ profile/settings via queries from Slice 2). Move `releases` to the query, keep `balance`/`transactions` on the context; add `studioReleasesQuery` to the loader's `Promise.all`:

```tsx
import { studioProfileQuery, studioSettingsQuery, studioReleasesQuery } from '../lib/api/queries/studio'
// in the loader Promise.all, add: queryClient.ensureQueryData(studioReleasesQuery())
// in the component:
const { balance, transactions } = useStudio()
const { data: releases } = useSuspenseQuery(studioReleasesQuery())
```

Leave the JSX (`inReview`/`drafts`/empty checks reading `releases`) unchanged.

- [ ] **Step 2:** `npm run build` green. Commit `feat(frontend): studio dashboard reads releases from query`.

---

## Task 5: Stub the wizard's publish (`studio.release.new.tsx`)

**Files:** Modify `Frontend/src/routes/studio.release.new.tsx`.

The wizard is Slice 3b; this only removes its dependency on the god-context's `addRelease` so the context can be shrunk. On publish (line ~101), replace the `addRelease({...})` mock prepend with a query invalidation, keeping `reset()` + navigate:

- [ ] **Step 1:** Replace:

```tsx
import { useQueryClient } from '@tanstack/react-query'
import { studioReleasesQuery } from '../lib/api/queries/studio'
// remove: import { useStudio } from '../features/studio/studio-context'  (and the `const { addRelease } = useStudio()`)
const queryClient = useQueryClient()
// ...in the publish handler, replace `addRelease({ ...mock release... })` with:
queryClient.invalidateQueries({ queryKey: studioReleasesQuery().queryKey })
// keep the existing reset() + navigate({ to: '/studio/releases' })
```

Add a `// TODO(slice-3b): wire real POST /v1/studio/releases submit + multipart track upload here.` comment at the publish site. If removing `addRelease` leaves other `useStudio` reads in this file, keep the import for those; otherwise remove it entirely.

- [ ] **Step 2:** `npm run build` green. Commit `chore(frontend): decouple release wizard publish from god-context (real submit deferred to 3b)`.

---

## Task 6: Shrink `studio-context.tsx` — remove releases

**Files:** Modify `Frontend/src/features/studio/studio-context.tsx`.

- [ ] **Step 1: Confirm migration.** `grep -rn "\.releases\b\|addRelease\|updateRelease\|removeRelease" src/routes/studio*.tsx src/components/studio/` — matches should only be query reads / local `releases` from `useSuspenseQuery`, NOT `useStudio()` reads. If a real consumer remains, migrate it first.
- [ ] **Step 2: Remove.** In `studio-context.tsx`: drop `getReleases`, `StudioRelease` from the `../../lib/studio-data` import (keep `getStudioEpisodes`, `StudioEpisode`); remove `releases` from `StudioState`; remove `addRelease`/`updateRelease`/`removeRelease` from `StudioContextValue`; drop them from `seed()`, `hydrate()`, and the `value` object.
- [ ] **Step 3: The real test — `npm run build`.** A build error means a consumer still reads a removed field — fix that consumer (do not re-add the field). Then `npx vitest run` (studio tests pass; the 3 known-unrelated `localStorage.clear` suites stay red) and `npm run lint` (**214**, no new).
- [ ] **Step 4:** Commit `refactor(frontend): drop releases from studio god-context (now query-backed)`.

---

## Task 7: Verify, live QA, and PR

- [ ] **Step 1: Full gate.** `npm run build` green · `npm run lint` at 214 · `npx vitest run src/lib/api/queries/studio.test.ts` all pass. (User runs `verify.sh`/`smoke.sh` if they want the backend gate — this is a frontend-only slice.)
- [ ] **Step 2: Live QA (logged-in creator).**
  - `/studio/releases` → `GET /v1/studio/releases?size=100 → 200`; list renders, filters (all/live/scheduled/in_review/draft) + search work; summary totals show. Delete a **draft** → `DELETE → 204`, row disappears, persists on refresh. Attempt to delete a **live** release → error toast, row stays (backend rejects).
  - `/studio/release/:id` (open one) → `GET /v1/studio/releases/:id → 200`; rename + Save → `PATCH → 200`, persists on refresh; confirm a price/date/status edit does NOT persist across refresh (documented). Deep-link/refresh the detail URL → still renders (loader fetches by id).
  - `/studio` dashboard renders (in-review/draft prompts derive from the live list).
  - `/studio/release/new` wizard still renders and steps through (mock); publishing navigates to the releases list without error.
- [ ] **Step 3: PR.** `git push -u origin feat/frontend-studio-releases`, then `gh pr create --base feat/frontend-studio-profile-settings ...` (stacked on Slice 2 so the diff is only Slice 3a; retarget to master once #139 merges). PR body documents the three limitations and notes the wizard (3b) is deferred. Do NOT use `open-pr.sh` (stages the local-only `application.properties`, targets `main`).

---

## Self-Review (author checklist)

- **Spec coverage:** list (T2), detail rename/delete (T3), dashboard (T4), wizard decouple (T5), context shrink (T6), verify+QA+PR (T7), all backed by the mappers/queries in T1. ✓
- **Type consistency:** `studioReleasesQuery`/`studioReleaseQuery`/`apiRenameRelease`/`apiDeleteRelease`/`toStudioRelease`/`StudioReleaseWire` names identical across T1–T6. MoneyView unwrap in one place (mapper). ✓
- **No placeholders:** every code step has real code; the only prose is the PR body. ✓
- **Decomposition boundary:** context keeps `episodes` (Slice 4) + payout state (Slice 5); only releases removed. Wizard (3b) explicitly deferred, only its publish decoupled. ✓
- **Known limitations surfaced:** detail price/date/status session-local; list size=100 cap; wizard not wired — all documented in-code + PR. ✓

## Studio roadmap position

Slice 1 (analytics + audience) — #138. Slice 2 (profile + settings) — #139. **Slice 3a (this) — releases list/detail/delete/rename.** Deferred: **Slice 3b (create wizard** — multipart upload infra + the 7 unsupported wizard fields + create→upload→submit flow; needs its own brainstorm). Then Slice 4 (podcasts), Slice 5 (payouts — money, security review). After Slice 5 the god-context is empty and deletable.
