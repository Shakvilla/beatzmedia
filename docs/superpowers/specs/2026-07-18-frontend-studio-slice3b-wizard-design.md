# Studio Slice 3b — Release-Creation Wizard Wiring (design)

**Date:** 2026-07-18
**Area:** Frontend (`Frontend/`) — Studio release-creation wizard
**Backend it consumes:** WU-CAT-5 catalog draft flow (merged in #142)
**Status:** design approved; ready for implementation plan

## Goal

Replace the release-creation wizard's in-memory mock with the real WU-CAT-5
draft flow: **create-draft → multipart upload-attach → PATCH (metadata +
per-track price/order) → submit (finalize)**. The wizard's four steps
(Details → Tracks → Splits → Review) keep their current look and feel; only
the persistence underneath becomes real.

## Scope decisions (locked)

1. **Wire the spine; keep unsupported extras client-only.** The backend
   persists title/type/genre/description/visibility/scheduledAt, attached
   tracks, and per-track price. Everything the backend does *not* support —
   **royalty splits** (deferred to WU-CAT-6), **cover art** (no upload
   endpoint), and **featured artists / label / primary artist / pre-save /
   distribution agreement** — stays exactly as it renders today: collected
   and validated in the UI, but **not sent**. Each such field/section carries
   a `TODO(WU-CAT-6)` or `TODO(cover-upload)` marker.
2. **The server draft is created when the user leaves the Details step**
   (clicks Continue from step 1), so the release id exists before any upload.
   Revisiting Details after creation issues a metadata PATCH, not a second
   create.
3. **Keep both Review-step submit gates unchanged** (no visual change):
   Submit still requires cover art *and* every track's splits to total 100%,
   client-side, even though neither is persisted in 3b. Re-evaluated when
   WU-CAT-6 / cover-upload land.

### Non-goals (explicit)

- Persisting splits (WU-CAT-6), cover-image upload, pre-save links.
- Resuming/hydrating an existing draft — each wizard session creates a fresh
  draft; an abandoned draft simply persists as a `draft` row (visible in the
  releases list, reopenable via Slice 3a, deletable). Orphan-draft
  accumulation is accepted, matching the backend contract ("drafts are
  cheap/deletable").
- A determinate upload-progress bar (see Data flow → Tracks).

## Backend contract (already shipped, WU-CAT-5)

| Method | Path | Body | Result |
|---|---|---|---|
| POST | `/v1/studio/releases` | `{ title?, type, visibility?, scheduledAt?, genre?, description? }` | 201 `StudioReleaseDetail` (`draft`) |
| GET | `/v1/studio/releases/:id` | — | `StudioReleaseDetail` |
| PATCH | `/v1/studio/releases/:id` | `{ title?, genre?, description?, visibility?, scheduledAt?, tracks?: [{trackId, position, priceMinor}] }` | `StudioReleaseDetail` |
| POST | `/v1/studio/releases/:id/tracks` | multipart audio (WAV/FLAC, ≤500 MB) | 201 `UploadedTrack` (`{ id, title, duration, status, progress, src, price, explicit, position }`) |
| DELETE | `/v1/studio/releases/:id/tracks/:trackId` | — | 204 |
| POST | `/v1/studio/releases/:id/submit` | `Idempotency-Key` header | 200 `StudioReleaseDetail` (`in_review`) |

- `type` is required on create; `title` defaults server-side to
  `"Untitled release"`.
- `genre`/`description`/`visibility`/`scheduledAt`/`tracks` are **draft-only**
  (`409 ILLEGAL_TRANSITION` on a non-draft release).
- `tracks` on PATCH **replaces** the whole ordered list; every `trackId` must
  already belong to the release.
- Submit validates the track-count matrix (`single`=1, `ep`=3–6, `album`=7+,
  `mixtape`≥1 → else `422 TRACK_COUNT_INVALID`) and recomputes the bundle
  price.
- The backend `UploadedTrack` view is ~1:1 with the wizard's local
  `UploadedTrack` shape (`{ id, title, duration, status, progress, src,
  price, explicit }`), so mapping is thin.

## Architecture & data flow

### API client — multipart support

`apiFetch` (`Frontend/src/lib/api/client.ts`) is JSON-only today: it always
sets `Content-Type: application/json` and `JSON.stringify`s the body. Extend
it so that when `options.body instanceof FormData`, it (a) does **not** set
`Content-Type` (the browser sets the multipart boundary) and (b) passes the
`FormData` through unmodified. All other behavior (auth header, 401 handling,
204, error-envelope parsing) is unchanged.

### New query-layer functions (`Frontend/src/lib/api/queries/studio.ts`)

Alongside the Slice-3a functions (`studioReleasesQuery`, `studioReleaseQuery`,
`apiRenameRelease`, `apiDeleteRelease`):

- `apiCreateDraft(input: CreateDraftInput): Promise<StudioReleaseDetail>` —
  `POST /studio/releases`.
- `apiUploadTrack(releaseId: string, file: File): Promise<UploadedTrack>` —
  `POST /studio/releases/:id/tracks` with a `FormData` body.
- `apiUpdateRelease(releaseId, patch: UpdateReleaseInput): Promise<StudioReleaseDetail>` —
  `PATCH /studio/releases/:id`.
- `apiSubmitRelease(releaseId, idempotencyKey: string): Promise<StudioReleaseDetail>` —
  `POST /studio/releases/:id/submit` with the `Idempotency-Key` header.

Money is sent as integer minor units (`priceMinor`, pesewas) and read back via
the existing `MoneyView`-unwrapping mapper (`toStudioRelease` from Slice 3a);
a thin `UploadedTrack` mapper reuses that convention.

### `release-draft-context` becomes backend-backed

`Frontend/src/features/studio/release-draft-context.tsx` gains:

- `releaseId: string | null` in state.
- Async actions that call the API and reconcile local state:
  - `createOrUpdateDraft()` — on leaving Details: if `releaseId` is null,
    `apiCreateDraft` and store the id; else `apiUpdateRelease` with current
    metadata.
  - `uploadTrack(file)` — `apiUploadTrack`; appends the returned server track
    (carrying its **server track id**) to `tracks`. On failure, append/flip a
    track to `status: 'error'` for retry/remove.
  - `removeTrack(id)` — `apiDeleteRelease`-style `DELETE /:id/tracks/:trackId`,
    then drop it locally.
  - `flushAndSubmit()` — `apiUpdateRelease` with final `{ metadata, tracks:
    [{trackId, position, priceMinor}] }` (positions from array order, prices
    from the pricing step), then `apiSubmitRelease` with a freshly-generated
    idempotency key.
- The fake `TICK_UPLOADS` reducer action and its progress simulation are
  removed. Reorder/price/metadata edits stay local until the next
  create/update/flush.

Each `UploadedTrack` now stores the backend track id so PATCH `tracks[]` can
reference it. Local-only ids are gone.

### Tracks step

`studio.release.new.tracks.tsx` swaps the simulated upload for a real
multipart `POST` per file. Because `fetch` cannot report upload progress
without dropping to `XMLHttpRequest`, the progress bar becomes **indeterminate**
("uploading…") until the `201` response flips the track to `ready`; this is
the one minor visual change and is a deliberate non-goal to keep the client
simple. Delete calls the real endpoint.

### Wizard chrome (`studio.release.new.tsx`)

`handleContinue` is rewired:

- **Details → Tracks:** `createOrUpdateDraft()` (create on first pass), then
  advance. Surface create errors as toasts.
- **Save draft** button: `createOrUpdateDraft()` (or metadata PATCH once the
  draft exists).
- **Submit (Review):** keep the existing client gates (title, ≥1 track,
  cover art, splits total 100%, agreement accepted — decisions 1 & 3), plus a
  client-side track-count-matrix pre-check mirroring the backend; then
  `flushAndSubmit()`; on success invalidate `studioReleasesQuery`, `reset()`,
  navigate to `/studio/releases`. The `TODO(slice-3b)` stub is removed.

## Error handling

Map backend errors to the existing toast / field-error pattern:

- **create-draft** — `type` is client-guarded (always set from the release
  type); no expected 4xx in normal flow.
- **upload** — `422 UNSUPPORTED_FORMAT` ("Only WAV/FLAC accepted"), `413`
  ("File exceeds 500 MB"), `409 ILLEGAL_TRANSITION` (release no longer a
  draft). Failed upload → track `status: 'error'`.
- **PATCH** — `422 TRACK_NOT_IN_RELEASE | DUPLICATE_TRACK_REF | INVALID_PRICE`,
  `409 ILLEGAL_TRANSITION`.
- **submit** — `422 TRACK_COUNT_INVALID` → friendly type-specific message
  (e.g. "An album needs at least 7 tracks"); client pre-checks the same
  matrix so this is a backstop. `409 IDEMPOTENCY_KEY_CONFLICT |
  ILLEGAL_TRANSITION`. `Idempotency-Key` is always sent, so
  `400 MISSING_IDEMPOTENCY_KEY` should never surface.
- `401` is handled globally by the existing unauthorized handler.

## Testing

Vitest + Testing Library (existing harness; Node 22.17.1 via nvm):

- **`client.test.ts`** — new case: a `FormData` body is passed through with no
  `Content-Type` header and not stringified; JSON bodies still behave as
  before.
- **`studio.ts`** — the four new functions call the right method/path/body/
  headers (mocked `fetch`), including the `Idempotency-Key` on submit and the
  `priceMinor` minor-units convention.
- **mapper** — backend `UploadedTrack` → wizard `UploadedTrack` (money unwrap,
  status passthrough).
- **wizard flow** — with `apiFetch` mocked: leaving Details creates the draft
  once (and PATCHes on revisit); adding a file appends a server track;
  Submit flushes then calls submit and navigates to `/studio/releases`;
  keep-both-gates behavior (cover art + 100% splits still block).

**Gate:** `npm run build` (`tsc -b`, the real typecheck gate) is clean and
lint has 0 new warnings. Live QA against a running Compose stack is run by the
user.

## Files touched

- `Frontend/src/lib/api/client.ts` — multipart branch.
- `Frontend/src/lib/api/queries/studio.ts` — `apiCreateDraft`, `apiUploadTrack`,
  `apiUpdateRelease`, `apiSubmitRelease` (+ input types).
- `Frontend/src/lib/api/mappers.ts` — `UploadedTrack` mapper.
- `Frontend/src/features/studio/release-draft-context.tsx` — `releaseId` +
  async actions; remove `TICK_UPLOADS`.
- `Frontend/src/routes/studio.release.new.tsx` — real create/save/submit.
- `Frontend/src/routes/studio.release.new.tracks.tsx` — real upload/delete,
  indeterminate progress.
- `Frontend/src/routes/studio.release.new.details.tsx`,
  `…splits.tsx`, `…review.tsx` — wired where they touch the new actions;
  client-only sections keep their `TODO` markers.

## Follow-ups (out of this slice)

- **WU-CAT-6** — per-track royalty splits + confirmation flow (backend), then
  wire the Splits step's persistence.
- **Cover-image upload** (backend endpoint) → wire cover art; re-gate submit.
- Pre-save links; resume-an-existing-draft entry point; determinate upload
  progress via XHR if desired.
