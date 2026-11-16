# Artist release editing implementation plan

Design: [artist release editing](2026-11-02-artist-release-editing-design.md)

## Implementation sequence

### 1. Revision persistence and domain
- Add migration after V994 for `release_revision` and `release_revision_track` with one pending revision per release, status, timestamps, rejection reason, editable release fields, existing track IDs, order, price, title/artwork/audio references.
- Add framework-free revision domain objects and validation for immutable membership, uniqueness of position, price limits, revision lifecycle, and optimistic version.
- Add JPA entities, mappers, and repository port methods for create/read/update/submit/review/promote.

### 2. Artist revision API
- Add artist routes to get current/pending edit state, create/update live revision, upload track artwork/audio to revision, submit revision, and edit submitted non-live releases.
- Enforce artist ownership and verify every track belongs to the release; reject add/remove IDs after submission.
- Keep draft endpoints and initial wizard track upload behavior unchanged.
- Promote an approved revision transactionally into active release, release-track, and track rows; publish existing durable catalog events and update search projections.

### 3. Moderation
- Extend admin catalog detail to include any pending revision and a field-by-field comparison to active data.
- Add moderator approve/reject endpoints; require a nonblank rejection reason and audit both outcomes.
- Reuse the existing first-publication approval flow for new releases; route revision approval to promotion without changing release status from live/scheduled.

### 4. Artist frontend
- Replace the release manager's local-only price/date/availability controls with persisted, state-aware editor fields.
- Edit existing track fields and allow reordering, while keeping submitted membership fixed.
- Add revision status, active-versus-proposed visibility, save/submit states, pending lock, and rejection feedback.
- Invalidate artist, album, track, search, and moderation queries after successful changes.

### 5. Contract and verification
- Update `API-CONTRACT.md` and catalog/admin architecture docs with revision states and endpoints.
- Add domain/service tests for lifecycle, permissions, fixed membership, concurrency, approval/rejection, and active-version isolation.
- Add frontend tests for state-aware editing, pending/rejected revisions, and API mapping.
- Run targeted tests and builds once all components are connected.

## Key references
- `Frontend/src/routes/studio.release.$releaseId.tsx`
- `Frontend/src/routes/admin.catalog.$itemId.tsx`
- `Frontend/src/lib/api/queries/studio.ts`
- `Frontend/src/lib/api/queries/admin-catalog.ts`
- `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/in/rest/StudioReleaseResource.java`
- `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/service/UpdateReleaseService.java`
- `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/service/PublishReleaseService.java`
- `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence/JpaCatalogRepository.java`
- `backend/src/main/java/org/shakvilla/beatzmedia/admin/adapter/in/rest/AdminCatalogResource.java`
