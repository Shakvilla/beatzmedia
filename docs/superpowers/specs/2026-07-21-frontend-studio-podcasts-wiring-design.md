# Frontend Studio Podcasts Wiring — Design

**Date:** 2026-07-21
**Slice:** Studio surface completion — podcasts (create/manage)
**Branch:** `feat/frontend-studio-podcasts` off `master`
**Backend:** WU-STU-2 (`studio` module podcast shows/episodes) — already merged, contract-documented. No backend change.

## Goal

Swap the two `studio.podcasts` routes from the mock `useStudio()` episode store to the live
WU-STU-2 endpoints, with **zero visual change**. Episodes leave the shared studio context (the same
extraction already done for releases); payouts state stays on the mock in `useStudio()` until the
next slice.

## Context

- `Frontend/src/routes/studio.podcasts.index.tsx` — lists "Your shows" (`getStudioShows()`) and
  "Episodes" (`useStudio().episodes`), with per-row Play/Edit/View/Delete menu. Delete calls
  `useStudio().removeEpisode`. Edit/Play/View are no-op toasts today.
- `Frontend/src/routes/studio.podcasts.new.tsx` — new-episode form: audio upload (client
  `URL.createObjectURL` preview + duration probe), show select with inline **"+ New show…"**,
  title/description, cover image (client preview), visibility (`public|scheduled` + date),
  monetization (`premium` + price + early-access). Submit builds a `StudioEpisode` and calls
  `useStudio().addEpisode`, then navigates back.
- `Frontend/src/features/studio/studio-context.tsx` — shared store holding **both** episodes and
  payouts (balance/transactions/methods), seeded from mocks + localStorage-persisted. Its own doc
  comment anticipates the swap: "When the real API lands, swap the seed + persistence for
  fetches/mutations; call sites keep using `useStudio()`."

### Backend surface (WU-STU-2, `StudioPodcastResource`, `@RolesAllowed("artist")`, base `/v1/studio/podcasts`)

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/shows` | — | `PodcastShowView[]` |
| POST | `/shows` | JSON `{title, category}` | `PodcastShowView` (201) — **not used by UI** |
| GET | `/episodes` | — | `EpisodeView[]` |
| POST | `/episodes` | **multipart**: `audio` file part + `data` JSON part; requires `Idempotency-Key` | `EpisodeView` (201) |
| PATCH | `/episodes/{id}` | JSON `UpdateEpisodeBody` | `EpisodeView` — **deferred** |
| DELETE | `/episodes/{id}` | — | 204 |

Wire shapes (both near-identical to the frontend types):
- `PodcastShowView(String id, String title, String category)` ≡ frontend `StudioPodcastShow`.
- `EpisodeView(String id, String showId, String showTitle, String title, int duration,
  String status, boolean premium, BigDecimal price, String publishedAt, long plays)` ≡ frontend
  `StudioEpisode` (only `price` differs: `BigDecimal` wire → `number`).
- `CreateEpisodeBody(String showId, NewShowBody newShow, String title, String description,
  String cover, String visibility, String date, boolean premium, BigDecimal price,
  Boolean earlyAccess)`, `NewShowBody(String title, String category)`. `showId` **XOR** `newShow`.
  `date` is ISO-8601, required + strictly future when `visibility=scheduled`. `cover` is a plain
  URL string, **not** a second file part.
- **`duration` and `status` are NOT in `CreateEpisodeBody`** — the backend derives `duration`
  server-side from the uploaded audio and `status` from `visibility` (public→published,
  scheduled→scheduled). The returned `EpisodeView` carries the authoritative values. The form's
  client-side `URL.createObjectURL` duration probe stays **preview-only** (the "reading…" label) and
  is never sent.

API-CONTRACT.md already documents all of these (lines 263–267). `apiFetch` (`lib/api/client.ts`)
already supports `FormData` bodies (skips the JSON `Content-Type`) and an `idempotencyKey` option —
proven by the release-wizard upload (slice 3b).

## Architecture

Same idiom as the merged studio slices (TanStack `queryOptions` + `apiFetch<Wire>` + `mappers.toX`;
mutations = async `apiFetch` fns + `queryClient.invalidateQueries` on success).

- **New `Frontend/src/lib/api/queries/podcasts-studio.ts`** (keep the consumer `podcasts.ts`
  untouched):
  - `studioShowsQuery()` → `GET /studio/podcasts/shows` → `toStudioShow[]`.
  - `studioEpisodesQuery()` → `GET /studio/podcasts/episodes` → `toStudioEpisode[]`.
  - `apiCreateEpisode(form)` → builds `FormData` (`audio` Blob part + `data` JSON string part),
    `POST /studio/podcasts/episodes` with `idempotencyKey: crypto.randomUUID()` → `toStudioEpisode`.
  - `apiDeleteEpisode(id)` → `DELETE /studio/podcasts/episodes/{id}`.
- **Mappers** in `Frontend/src/lib/api/mappers.ts`: `toStudioShow` (identity over `{id,title,category}`),
  `toStudioEpisode` (`price` `number(wire)`; `status` string → `EpisodeStatus`; rest identity).
  Corresponding `*Wire` types.
- **`studio.podcasts.index.tsx`**: replace `getStudioShows()` + `useStudio().episodes/removeEpisode`
  with `useQuery(studioShowsQuery())` / `useQuery(studioEpisodesQuery())` and a delete mutation
  (`apiDeleteEpisode` → invalidate both lists). Loading/empty states preserved (empty = both lists
  empty). No JSX/class changes.
- **`studio.podcasts.new.tsx`**: on submit, assemble the `CreateEpisodeBody` JSON (mapping the form:
  inline new-show → `newShow{title,category}` and `showId=null`; else `showId` + `newShow=null`;
  `visibility`, `date` → ISO-8601 when scheduled, `premium`, `price`, `earlyAccess`, `cover` as the
  client preview URL or null) plus the audio `File`, call `apiCreateEpisode`, invalidate the lists,
  toast, navigate back. Preserve the disabled/`canSubmit` logic and all JSX.
- **`studio-context.tsx`**: remove `episodes`, `addEpisode`, `removeEpisode` and their seed/persist;
  keep payouts (`balance`/`transactions`/`methods` + their actions) exactly as-is. Surgical
  extraction, mirroring how releases were removed from this context.

## Out of scope

- **Episode edit** — the index "Edit" action stays the current no-op toast; wiring `PATCH` means a
  new edit screen (a visual change). Deferred by decision.
- **Standalone `POST /shows`** — no "create show" UI exists (shows are created only inline on first
  episode publish). YAGNI: no query fn.
- **Payouts** — next slice; `useStudio()` keeps serving `balance/transactions/methods`.
- **Cover image upload as a file** — backend takes `cover` as a URL string; the form's cover picker
  stays a client-only preview, unchanged behavior.

## Testing & gate

- Co-located Vitest `podcasts-studio.test.ts`: query URLs; `apiCreateEpisode` builds the right
  `FormData` (`audio` + `data` parts) and sets an `Idempotency-Key`; delete URL/method. Mapper cases
  added to `mappers.test.ts`. RTL uses `toBeTruthy()`/`queryByText` (no `@testing-library/jest-dom`).
- No visual change: JSX and classes preserved; only data sources swapped.
- Mapper outputs match `Frontend/src/types/index.ts` exactly; money stays represented as the UI
  expects (episode `price` is a plain cedi `number`, as today).
- Gate (from `Frontend/`, Node 22 via nvm): `npm run build` (real typecheck: `tsc -b`) +
  `npx vitest run` green; no NEW `npm run lint` errors (repo has ~214 pre-existing). Live QA against
  the `docker compose` stack: create an episode (with audio upload + inline new show), see it appear,
  delete it. One PR: `feat/frontend-studio-podcasts`.
