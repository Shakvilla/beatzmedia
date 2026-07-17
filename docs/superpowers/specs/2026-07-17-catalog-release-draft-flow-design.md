# WU-CAT-5 — Studio release create flow (draft → upload → finalize) Design

**Status:** Approved (brainstorm) — ready for implementation planning
**Date:** 2026-07-17
**Module:** `catalog`
**WU:** WU-CAT-5 (new; `depends_on` the release-lifecycle WUs WU-CAT-2/3/4)
**Owner:** backend-engineer

## Problem

The Studio "new release" wizard (frontend) has never been wired because the backend's create-release path **does not compose end-to-end**. Reading the two services confirms:

- **`POST /v1/studio/releases` (submit)** — `SubmitReleaseService` calls `Release.create(...)`, which **hard-codes `ReleaseStatus.in_review`** and requires the full track list up front (it validates `single` = exactly 1 track immediately). It does *not* verify the referenced `trackId`s exist.
- **`POST /v1/studio/releases/:id/tracks` (upload)** — `UploadReleaseTrackService` requires the release to **already exist** (`repo.findRelease(id).orElseThrow`), then mints its **own** new `trackId` (`ids.newId()`) and persists a **standalone** stub `Track` — it **never attaches** that track to the release's `ReleaseTrack` list.

So: upload needs a release id, submit needs trackIds, and even sequenced, the uploaded audio's `Track` is never linked to the release. There is **no draft-create, no finalize, and nothing that transitions to `draft`** (only `DeleteReleaseService` even references the `draft` status). The wizard was a pure client-side mock, so this gap was never exercised.

This WU makes the flow composable and correct. The **frontend wizard wiring is out of scope** — it becomes a follow-up slice once this lands.

## Goal

A coherent creator flow:

```
create draft (metadata) → upload tracks (attached to the draft) → edit (order/price/splits) → finalize (→ in_review)
```

which also mirrors the wizard's four steps (Details → Tracks → Pricing/Splits → Review).

## Endpoints

All under `@Path("/v1/studio/releases")`, `@RolesAllowed("artist")`, artist id from `jwt.getSubject()` (IDOR-safe — no artist path/query param), on the existing `StudioReleaseResource`.

### 1. `POST /v1/studio/releases` — create draft **(repurposed)**

Currently creates an `in_review` release with all tracks. **Repurposed** to create a metadata-only **draft**. Safe: nothing real calls the old behavior (the wizard was a mock; Slice 3a's list/detail never POST). The semantic change is recorded in an ADR.

- Request `CreateDraftBody`: `{ title?: string, type: "single"|"ep"|"album"|"mixtape", visibility?: "public"|"scheduled", scheduledAt?: string|null, genre?: string|null, description?: string|null }`. `type` required; `title` defaults to `"Untitled release"` if blank; `visibility` defaults to `scheduled`.
- Response: **201** `StudioReleaseDetailView` with `status: "draft"`, empty `tracks`.
- No track-count validation here (drafts may be incomplete).

### 2. `POST /v1/studio/releases/:id/tracks` — upload + **attach** (fixed)

Multipart WAV/FLAC (existing validation: content-type in {wav, x-wav, flac, x-flac}, ≤ 500 MB). **Fix:** after creating the stub `Track`, **append a `ReleaseTrack`** (the new `trackId`, `position` = end of list, `priceMinor` = the platform default track price) to the release it was uploaded to, and persist the release. Draft-only — uploading to a non-draft release → **409 `ILLEGAL_TRANSITION`**.

- Response: **201** `UploadedTrackView` (existing shape; `position` added).

### 3. `PATCH /v1/studio/releases/:id` — update draft **(extended)**

Extends the current title-only PATCH. Body `UpdateReleaseBody`: `{ title?, genre?, description?, visibility?, scheduledAt?, tracks?: TrackRefBody[] }` where `TrackRefBody = { trackId, position, priceMinor, splits: SplitRefBody[] }` and `SplitRefBody = { name, email, role, percent, confirmation }`.

- If `tracks` is present, it **replaces** the draft's ordered track list (positions, per-track prices, splits) — the coarse "save the whole draft" shape the wizard's client-side draft model produces. `tracks` edits are **draft-only** → non-draft returns **409 `ILLEGAL_TRANSITION`**.
- `title` alone remains editable on any status (preserves Slice 3a's rename on `in_review`/`live`).
- Every `trackId` in `tracks` must already belong to this release (else **422 `TRACK_NOT_IN_RELEASE`**).
- Response: **200** `StudioReleaseDetailView`.

### 4. `DELETE /v1/studio/releases/:id/tracks/:trackId` — remove a draft track (new)

Draft-only. Removes the `ReleaseTrack` from the release (and may delete the orphaned stub `Track`). Non-draft → **409**; unknown track → **404**.

- Response: **204**.

### 5. `POST /v1/studio/releases/:id/submit` — finalize (new)

Header **`Idempotency-Key` required** (the terminal side-effect; missing → 400 `MISSING_IDEMPOTENCY_KEY`, mirroring today's submit). Validates track count (INV-12) + split sums, recomputes list price (INV-5), transitions **draft → in_review**, appends a `SUBMIT_RELEASE` audit entry — all in one `@Transactional`. Not-draft → **409 `ILLEGAL_TRANSITION`**.

- Response: **200** `StudioReleaseDetailView` with `status: "in_review"`.

### Existing endpoints (unchanged behavior)

`GET /v1/studio/releases` (list), `GET /:id`, `DELETE /:id`. **`GET /:id` now returns `StudioReleaseDetailView`** (additive — the extra `genre`/`description`/`visibility`/`scheduledAt`/`tracks` fields are ignored by Slice 3a's frontend, which reads only the `StudioReleaseView` subset).

## View DTOs

- **`StudioReleaseView`** (list; **unchanged**) — `{ id, title, type, status, date, trackCount, streams, revenue: MoneyView, price: MoneyView }`.
- **`StudioReleaseDetailView`** (new; superset) — `StudioReleaseView` **+** `{ genre: string|null, description: string|null, visibility: string, scheduledAt: string|null, tracks: TrackDraftView[] }`.
- **`TrackDraftView`** — `{ trackId, title, duration: number (sec), status: string, position: number, price: MoneyView, splits: SplitView[] }`.
- **`SplitView`** — `{ name, email, role, percent, confirmation }`.

## Domain (`Release.java`)

Add, alongside the existing `create`/`approveScheduled`/`approveImmediate`/`goLive`/`takedown`/`reinstate`:

- **`createDraft(id, artistId, title, type, visibility, scheduledAt, genre, description, now)`** → status `draft`, empty tracks, `listPriceMinor` 0.
- **`addTrack(ReleaseTrack)`** / **`removeTrack(trackId)`** / **`replaceTracks(List<ReleaseTrack>)`** — **draft-only**; else `IllegalTransitionException`.
- **`submit(now)`** — **draft → in_review**; recomputes `listPriceMinor` via the existing `computeListPrice(type, tracks, bundleDiscountPct)`; else `IllegalTransitionException`.
- `genre` / `description` become nullable fields on the aggregate, settable while `draft` (and via `title`-style metadata edit).

Track mutations and metadata edits are rejected once past `draft` — the release is immutable to the creator during `in_review`/`live` except for the title (Slice 3a) and the admin lifecycle transitions.

## Schema (Flyway, forward-only)

`V<next>__catalog_release_draft.sql` (allocate via `backend/scripts/next-migration-version.sh`):

```sql
ALTER TABLE release ADD COLUMN genre       TEXT;
ALTER TABLE release ADD COLUMN description TEXT;
```

Track attach/remove uses the existing `release_track` table (no new table). Confirm `release_track` has `position` + `price_minor` columns; if `release.status` has a CHECK constraint, ensure `draft` is permitted (the enum already includes it).

## Invariants & validation

- **INV-12 — track count by type**, enforced at **finalize only** (drafts may be partial): `single` = 1, `ep` = 3–6, `album` = 7+, `mixtape` = ≥1. Violations → **422 `TRACK_COUNT_INVALID`** (existing code). Per-track split percentages must sum ≤ 100 → **422 `SPLIT_OVER_100`** (existing).
- **INV-5 — list price** = Σ track `priceMinor` with the platform bundle discount; recomputed at finalize (reuse `computeListPrice`). Money stays integer minor units (pesewas); API serializes `MoneyView { amount(cedis), currency:"GHS" }`.
- **INV-10 — audit**: `CREATE_DRAFT` and `SUBMIT_RELEASE` `AuditEntry`s, appended in the same transaction as the mutation.
- **Idempotency** on `POST /:id/submit` only (via the existing idempotency-key mechanism). Draft create/edit are cheap and deletable — no key.
- **Transcode not gated** — finalize accepts tracks still in `uploading` status (the media module transcodes asynchronously; admin approval is the real content gate). Deliberate default; revisitable if admins want ready-only submission.

## Out of scope (explicit — documented, not silently dropped)

- **`coverImage` upload** — no endpoint; releases keep the generated gradient placeholder the detail page already renders. Deferred to a future media WU.
- **`featuredArtists`, `label`** — deferred (no columns this WU).
- **`primaryArtist`** (always the JWT artist), **`agreementAccepted`**, **`presaveGenerated`** — client-only UX state, never persisted.
- **The frontend wizard wiring** — separate follow-up slice (real multipart upload path + step wiring); this WU is backend-only.
- **Transcode-ready gating on finalize** — deferred (see above).

## Testing (project Definition of Done)

- **Unit** (`Release` domain): `createDraft` yields `draft`; `addTrack`/`removeTrack`/`replaceTracks` succeed on `draft` and throw `IllegalTransitionException` otherwise; `submit` transitions `draft → in_review` and recomputes list price; the full INV-12 count matrix (single/ep/album/mixtape bounds) + split-sum validation.
- **Integration** (Testcontainers, `catalog/it/`): full round trip — create draft → multipart upload (asserts the `ReleaseTrack` is **attached** to the release) → PATCH price/splits/order → `POST /:id/submit` → `in_review` with the correct computed list price; the delete-track path; **edit-after-submit → 409 `ILLEGAL_TRANSITION`**; missing idempotency key on submit → 400; `single` with 0 or 2 tracks at submit → 422.
- **Contract**: the new/changed endpoint shapes validate against `API-CONTRACT.md` and `Frontend/src/types` / the studio-data release shapes; `StudioReleaseView` (list) is byte-for-byte unchanged; `StudioReleaseDetailView` documented as an additive superset.
- **ArchUnit** (hexagonal deps), **Flyway-on-empty-DB**, **coverage gate**, **Spotless** — standard.

## Docs & registration

- **Register WU-CAT-5** in `backend/.project/backlog.yaml` (catalog phase, `depends_on` the release-lifecycle WUs), owner `backend-engineer`, before starting.
- **ADR** in `backend/docs/00-system-architecture.md §9`: `POST /v1/studio/releases` repurposed from direct-`in_review` creation to draft creation; the create flow is now `draft → upload-attached → finalize`, superseding the non-composable one-shot submit; upload now attaches tracks to their release.
- **`API-CONTRACT.md`**: document the five endpoints + `StudioReleaseDetailView`/`TrackDraftView`/`SplitView`; note `POST /studio/releases` now returns a `draft`.
- **Catalog module ADD** (`backend/docs/architecture/catalog.md`): as-built note in the same PR.

## Verification

- `bash backend/scripts/next-migration-version.sh` before writing the migration.
- Standard gate (run by the user per convention): `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh`.
- Manual: create a draft, upload a real WAV, confirm the track is attached (`GET /:id` shows it in `tracks`), set price/splits, submit, confirm `in_review` + list price; confirm a `single` with 2 tracks is rejected at submit, and editing after submit 409s.

## Follow-ups (separate slices)

1. **Frontend release wizard wiring** (Studio Slice 3b-frontend) — real multipart upload path (`apiFetch` is JSON-only; needs FormData + XHR for progress), step-by-step wiring to these endpoints, "Save draft" via PATCH, submit via `/:id/submit`.
2. **Cover-image upload** (media WU) + `featuredArtists`/`label` columns, if product wants them.
3. **Transcode-ready gating** on finalize, if admins want it.
