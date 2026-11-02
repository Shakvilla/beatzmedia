# Artist release and track editing design

**Status:** Design approved for drafts, submitted releases, and live releases. Live-release edits are reviewed while the current version stays live. The existing track list is fixed after initial submission.

## Goal

Give artists one reliable place to edit release details and existing track details across the release lifecycle. Changes to already-published material must not alter what fans hear or see until an administrator approves the edit.

## Current state

- The artist release manager is at `Frontend/src/routes/studio.release.$releaseId.tsx`.
- It renders inputs for title, price, date, download settings, availability, and tracks, but `save()` currently sends only title and downloadable. Some other controls are client-only and report success without persisting.
- `PATCH /v1/studio/releases/:id` is implemented in `StudioReleaseResource` and `UpdateReleaseService`. Title and downloadable are mutable in any status; genre, description, visibility, schedule, and track list are draft-only.
- Track list ownership, ordering, and per-release pricing live in `release_track`; track title, media URL, and image live in `track`.
- Release approval transitions are handled by `PublishReleaseService`; approved releases are projected into fan-facing album/track catalogues by publication observers.
- Artwork upload paths already exist for release artwork and per-track artwork. Track-specific artwork is required to finalize multi-track releases.

## Product behavior

Artists may edit release title, genre, description, release date/visibility, release cover, and the title, artwork, audio, price, and order of each existing track. They may not add or remove tracks from a submitted release. The initial draft wizard continues to support adding/removing tracks while assembling the first submission.

| Release state | Edit behavior | Fan visibility |
|---|---|---|
| Draft | Save directly; existing initial-upload workflow remains | Not public |
| In review | Update the pending submission; moderation reviews latest saved state | Not public |
| Live or scheduled | Create/update a pending revision; submit for moderation | Existing approved version remains active |

For a live revision, approval activates the proposed release and track metadata/media together. Rejection leaves the active version unchanged and returns a visible reason to the artist. One pending revision per release is allowed to prevent conflicting approvals. Existing purchase grants remain linked to the same track IDs and retain access when approved audio is replaced.

## Architecture

Introduce a release revision aggregate rather than mutating active `release`, `release_track`, and `track` rows. Persist a revision header (release ID, artist ID, state, submitted/reviewed timestamps, moderator/rejection reason) and revision track rows containing the editable per-release fields and uploaded media asset references. Creating a revision copies the current active values; updates only touch the revision. This preserves stable public reads until approval.

Keep track IDs stable. Store proposed track titles, artwork URLs, audio asset references, position, and price in the revision rows. Store proposed release fields and cover URL on the revision header/details. Approval performs one transactional promotion of these fields into active release and track data, recalculates the bundle price, then emits the existing catalog projection events so search, album and fan read models refresh. Rejection records a reason and closes the pending revision without changing active rows.

Draft editing can continue using current update endpoints. Submitted-release editing should use explicit revision endpoints and a shared view that returns active data plus pending revision status. Authorization checks must verify both release ownership and track membership; media is uploaded under revision ownership and remains unreferenced by active catalog rows until approved.

## API and frontend

Add artist endpoints to read/create/update/submit a revision, upload revision audio/artwork, and read revision status. Add an admin moderation detail/action path that shows active and proposed values side by side and supports approve/reject with a reason. Preserve existing release endpoints for first submission and draft editing.

Expand the release manager into the lifecycle editor. Load active and pending data, track dirty fields, upload images/audio through the media API, display save/submission/review states, disable duplicate revision creation while pending, and surface moderation feedback. Use server responses as the source of truth; remove local-only fake publication and availability actions. In-review releases should show that editing updates the pending submission rather than creating a public revision.

## Validation and failure behavior

- Enforce owner access for every read and mutation; hide cross-artist resource existence consistently.
- Lock the submitted track membership, while permitting reorder and edits to existing tracks.
- Validate track membership, upload media type/size, track price limits, release price calculation, and required track artwork.
- Reject a second live revision while one is pending; use optimistic versioning so concurrent changes cannot overwrite a newer revision.
- If upload or save fails, retain the artist's local edits and show a retryable error. Failed moderation must preserve the active live release.
- Keep an audit trail for revision create, submit, approve, reject, and promotion.

## Delivery plan

1. Add revision domain model, persistence, migration, repository ports, and lifecycle tests.
2. Add artist revision read/update/submit APIs and media uploads; test ownership, fixed track membership, and pending-revision guards.
3. Add moderator comparison and approve/reject actions; verify atomic promotion and existing event/read-model refresh behavior.
4. Wire the artist release manager for drafts, in-review releases, and live revisions; remove misleading local-only controls.
5. Verify purchase access remains stable, public catalogue remains unchanged before approval, and approved changes refresh album, track, and search views.

## Acceptance criteria

- Artists can edit the defined release and track fields in all supported states.
- Submitted releases cannot gain or lose tracks through editing; the initial draft creation flow still works.
- Live edits do not affect fan-facing data until approval; the prior live version remains usable while review is pending or rejected.
- Approved changes update album, track, image, audio, price, ordering, and search projections consistently.
- Artists receive actionable rejection feedback and cannot create overlapping pending revisions.
- Existing ownership grants continue to resolve to the same track identities after an approved audio replacement.
