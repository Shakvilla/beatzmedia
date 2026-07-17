# WU-CAT-5 — Release create flow (draft → upload → finalize) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax. This is a **backend catalog WU** — the owner agent is `backend-engineer`; the `implement-work-unit`, `create-flyway-migration`, and `write-rest-resource` project skills carry the mechanical scaffolding conventions referenced below.

**Goal:** Make the Studio release-creation flow compose end-to-end — create a `draft` release, upload tracks that actually attach to it, edit metadata/order/price, then finalize `draft → in_review`.

**Architecture:** Hexagonal catalog module (`adapter → application → domain`). The fix is: real `release` rows with `status='draft'` (not the orphan `release_draft` JSONB table), a draft lifecycle on the `Release` aggregate, and an upload path that appends a `ReleaseTrack` to its release. `saveRelease(Release)` already upserts the full `release_track` list, so aggregate mutations persist by re-saving.

**Tech Stack:** Java 25, Quarkus 3.36, Hibernate/JPA, PostgreSQL 16, Flyway, JUnit 5 + Testcontainers, ArchUnit.

## Global Constraints

- **Hexagonal dependency rule:** `adapter → application → domain`. `Release`/`ReleaseTrack` stay **framework-free** (no Jakarta/Quarkus/Hibernate imports). Never call `Instant.now()` in domain — take `now` from the `Clock` port.
- **Money** in integer minor units (pesewas); API serializes `MoneyView { amount(cedis, 2dp), currency:"GHS" }` via `MoneyView.ofMinor(long)`. Per-track price is `release_track.price_minor`.
- **INV-5** list price via the existing `Release.computeListPrice(type, tracks, bundleDiscountPct)` (singles: no discount). **INV-12** track count at finalize: `single`=1, `ep`=3–6, `album`=7+, `mixtape`≥1. **INV-10** audit every privileged mutation (`CREATE_DRAFT`, `SUBMIT_RELEASE`) in the same transaction. **Idempotency-Key** on finalize only.
- **Draft-only mutation:** `addTrack`/`removeTrack`/`replaceTracks`/metadata edits (except title) throw `IllegalTransitionException` unless `status == draft`. Title stays editable on any status (preserves Slice 3a rename).
- **Splits are OUT of scope** (deferred to WU-CAT-6) — `ReleaseTrack` is unchanged (`trackId, position, priceMinor`), no `split_entry` writes, `TrackDraftView` has no `splits`.
- **`StudioReleaseView` (list) is unchanged.** `GET /:id` returns the additive superset `StudioReleaseDetailView`.
- **DoD:** unit + integration (Testcontainers) + contract tests pass; ArchUnit green; Flyway applies on empty DB; `docker compose up` healthy; coverage gate; Spotless clean; catalog ADD updated in the same PR. Verify with `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh` (run by the user per convention).
- All Java commands via `./backend/mvnw`. Next Flyway version is **V970** (confirm at implementation time with `bash backend/scripts/next-migration-version.sh`).

## File Structure

- `catalog/domain/Release.java` — mutable draft aggregate + `createDraft`/`addTrack`/`removeTrack`/`replaceTracks`/`submit`/`updateMetadata` + `genre`/`description`.
- `catalog/adapter/out/persistence/ReleaseEntity.java` + `JpaCatalogRepository.java` — `genre`/`description` columns mapped; `reconstitute` carries them; `deleteTrack`.
- `backend/src/main/resources/db/migration/V970__catalog_release_genre_description.sql` — two columns.
- `catalog/application/port/in/StudioReleaseDetailView.java` + `TrackDraftView.java` — new views; a mapper (release → detail).
- `catalog/application/port/in/CreateReleaseDraft.java` + `service/CreateReleaseDraftService.java` — create draft.
- `catalog/application/service/UploadReleaseTrackService.java` — **attach** the uploaded track to its release (the core fix).
- `catalog/application/port/in/UpdateRelease.java` + `service/UpdateReleaseService.java` — extended draft edit.
- `catalog/application/port/in/RemoveReleaseTrack.java` + `service/RemoveReleaseTrackService.java` — delete a draft track.
- `catalog/application/port/in/FinalizeRelease.java` + `service/FinalizeReleaseService.java` — `POST /:id/submit`.
- `catalog/adapter/in/rest/StudioReleaseResource.java` — repurpose `POST`, extend `PATCH`, add `DELETE /:id/tracks/:trackId` + `POST /:id/submit`; `GET /:id` returns detail view.
- Retire `SubmitRelease`/`SubmitReleaseService` (all-in-one submit) — replaced by create-draft + finalize.
- Docs: ADR-29 in `00-system-architecture.md §9`, `API-CONTRACT.md`, `docs/architecture/catalog.md`; register **WU-CAT-5** in `backend/.project/backlog.yaml`.

---

## Task 1: Domain — `Release` draft lifecycle + genre/description

**Files:** Modify `catalog/domain/Release.java`; Test `catalog/domain/ReleaseTest.java`.

**Interfaces produced:**
- `Release.createDraft(String id, String artistId, String title, ReleaseType type, Visibility visibility, Instant scheduledAt, String genre, String description, Instant now)` → status `draft`, empty tracks, `listPriceMinor` 0.
- `void addTrack(ReleaseTrack t, Instant now)`, `void removeTrack(String trackId, Instant now)`, `void replaceTracks(List<ReleaseTrack> tracks, Instant now)` — draft-only.
- `void updateMetadata(String title, String genre, String description, Visibility visibility, Instant scheduledAt, Instant now)` — draft-only (title also via existing `updateTitle`, any status).
- `void submit(int bundleDiscountPct, Instant now)` — `draft → in_review`, recompute `listPriceMinor`.
- `String getGenre()`, `String getDescription()`. `reconstitute(...)` gains `genre`, `description` params.

- [ ] **Step 1: Make aggregate fields mutable + add genre/description.** In `Release.java`, change `private final List<ReleaseTrack> tracks;` → `private List<ReleaseTrack> tracks;`, `private final long listPriceMinor;` → `private long listPriceMinor;`, `private final Visibility visibility;` → `private Visibility visibility;`, and add `private String genre;`, `private String description;`. Thread `genre`/`description` through the private constructor and `reconstitute(...)` (append the two params at the end); `create(...)` (legacy) passes `null, null`. Add getters `getGenre()`, `getDescription()`. Keep `computeListPrice` as-is.

- [ ] **Step 2: Write the failing domain tests.** Add to `ReleaseTest.java`:

```java
private static final Instant NOW = Instant.parse("2026-07-17T10:00:00Z");
private static Release draft() {
  return Release.createDraft("r1", "art1", "Untitled release", ReleaseType.single,
      Visibility.PUBLIC, null, "Afrobeats", "My bio", NOW);
}

@Test void createDraft_startsInDraftWithNoTracksAndZeroPrice() {
  Release r = draft();
  assertEquals(ReleaseStatus.draft, r.getStatus());
  assertTrue(r.getTracks().isEmpty());
  assertEquals(0L, r.getListPriceMinor());
  assertEquals("Afrobeats", r.getGenre());
}

@Test void addAndRemoveTrack_onDraft() {
  Release r = draft();
  r.addTrack(new ReleaseTrack("t1", 0, 250L), NOW);
  assertEquals(1, r.getTracks().size());
  r.removeTrack("t1", NOW);
  assertTrue(r.getTracks().isEmpty());
}

@Test void trackMutation_rejectedWhenNotDraft() {
  Release r = draft();
  r.addTrack(new ReleaseTrack("t1", 0, 250L), NOW);
  r.submit(24, NOW);                          // -> in_review
  assertThrows(IllegalTransitionException.class,
      () -> r.addTrack(new ReleaseTrack("t2", 1, 250L), NOW));
  assertThrows(IllegalTransitionException.class, () -> r.removeTrack("t1", NOW));
  assertThrows(IllegalTransitionException.class, () -> r.replaceTracks(List.of(), NOW));
}

@Test void submit_transitionsToInReviewAndRecomputesPrice() {
  Release r = draft();
  r.replaceTracks(List.of(new ReleaseTrack("t1", 0, 250L)), NOW);
  r.submit(24, NOW);
  assertEquals(ReleaseStatus.in_review, r.getStatus());
  assertEquals(250L, r.getListPriceMinor());   // single: no bundle discount
}

@Test void submit_rejectedWhenNotDraft() {
  Release r = draft();
  r.addTrack(new ReleaseTrack("t1", 0, 250L), NOW);
  r.submit(24, NOW);
  assertThrows(IllegalTransitionException.class, () -> r.submit(24, NOW));
}
```

- [ ] **Step 3: Run tests — expect FAIL** (methods undefined). `./backend/mvnw -pl catalog test -Dtest=ReleaseTest` (adjust module path to the project's layout). Expected: compile failure / missing methods.

- [ ] **Step 4: Implement the transitions.** Add to `Release.java`:

```java
public static Release createDraft(String id, String artistId, String title, ReleaseType type,
    Visibility visibility, Instant scheduledAt, String genre, String description, Instant now) {
  Release r = new Release(id, artistId, title, type, ReleaseStatus.draft, visibility,
      scheduledAt, null, 0L, now, now, List.of());
  r.genre = genre;
  r.description = description;
  return r;
}

private void requireDraft(String op) {
  if (this.status != ReleaseStatus.draft) throw new IllegalTransitionException(this.status, op);
}

public void addTrack(ReleaseTrack t, Instant now) {
  requireDraft("ADD_TRACK");
  var next = new java.util.ArrayList<>(this.tracks);
  next.add(t);
  this.tracks = List.copyOf(next);
  this.updatedAt = now;
}

public void removeTrack(String trackId, Instant now) {
  requireDraft("REMOVE_TRACK");
  this.tracks = this.tracks.stream().filter(rt -> !rt.trackId().equals(trackId)).toList();
  this.updatedAt = now;
}

public void replaceTracks(List<ReleaseTrack> tracks, Instant now) {
  requireDraft("REPLACE_TRACKS");
  this.tracks = List.copyOf(tracks);
  this.updatedAt = now;
}

public void updateMetadata(String title, String genre, String description,
    Visibility visibility, Instant scheduledAt, Instant now) {
  requireDraft("UPDATE_METADATA");
  if (title != null) this.title = title;
  this.genre = genre;
  this.description = description;
  if (visibility != null) this.visibility = visibility;
  this.scheduledAt = scheduledAt;
  this.updatedAt = now;
}

public void submit(int bundleDiscountPct, Instant now) {
  requireDraft("SUBMIT");
  this.listPriceMinor = computeListPrice(this.type, this.tracks, bundleDiscountPct);
  this.status = ReleaseStatus.in_review;
  this.updatedAt = now;
}
```

Make `computeListPrice` non-`static` OR keep static and call `computeListPrice(this.type, this.tracks, bundleDiscountPct)` (it's already `private static` — call with args). Add `getGenre()`/`getDescription()`.

- [ ] **Step 5: Run tests — expect PASS.** `./backend/mvnw ... -Dtest=ReleaseTest`. All green. Spotless: `./backend/mvnw spotless:apply`.

- [ ] **Step 6: Commit** `feat(catalog): WU-CAT-5 Release draft lifecycle (createDraft/addTrack/removeTrack/submit) + genre/description`.

---

## Task 2: Migration + persistence mapping for genre/description

**Files:** Create `db/migration/V970__catalog_release_genre_description.sql`; Modify `catalog/adapter/out/persistence/ReleaseEntity.java`, `JpaCatalogRepository.java`; Test — an integration round-trip (Task 4's IT covers it, but add a focused assertion here).

**Interfaces:** Consumes Task 1's `Release.getGenre()/getDescription()` + `reconstitute(...)` new params.

- [ ] **Step 1: Migration.** Confirm the version (`bash backend/scripts/next-migration-version.sh`), then create `V970__catalog_release_genre_description.sql`:

```sql
ALTER TABLE release ADD COLUMN genre       TEXT;
ALTER TABLE release ADD COLUMN description TEXT;
```

(Follow the `create-flyway-migration` skill for header/comment conventions. The `release.status` CHECK already permits `'draft'` — no change.)

- [ ] **Step 2: Entity fields + mapping.** In `ReleaseEntity.java` add `public String genre;` `public String description;` (column-mapped). In `JpaCatalogRepository.saveRelease`, after `e.listPriceMinor = ...` add `e.genre = release.getGenre(); e.description = release.getDescription();`. Wherever the repo calls `Release.reconstitute(...)` (find the loader), pass `e.genre, e.description` as the two new trailing args.

- [ ] **Step 3: Verify build + a focused round-trip.** Add to the catalog persistence IT (or Task 4's IT) an assertion: save a draft with `genre="Afrobeats"`, reload, assert `getGenre()` round-trips. Run `./backend/mvnw ... -Dtest=<catalog persistence IT>`. Expected: PASS.

- [ ] **Step 4: Commit** `feat(catalog): WU-CAT-5 persist release genre/description (V970)`.

---

## Task 3: `StudioReleaseDetailView` + `TrackDraftView` + detail mapper

**Files:** Create `catalog/application/port/in/StudioReleaseDetailView.java`, `TrackDraftView.java`; add a `toDetailView(Release, List<Track>)` helper (in a small `ReleaseViewMapper` or reuse the existing view-building location). Test `ReleaseViewMapperTest`.

**Interfaces produced:**
- `record StudioReleaseDetailView(String id, String title, ReleaseType type, ReleaseStatus status, String date, int trackCount, long streams, MoneyView revenue, MoneyView price, String genre, String description, String visibility, String scheduledAt, List<TrackDraftView> tracks)`.
- `record TrackDraftView(String trackId, String title, int duration, String status, int position, MoneyView price)`.
- `StudioReleaseDetailView toDetailView(Release r, List<Track> tracks)`.

- [ ] **Step 1: Write the DTOs** exactly as above (records). `date` = `r.getCreatedAt() != null ? r.getCreatedAt().toString() : "—"`; `visibility` = `r.getVisibility().toDbValue()`; `scheduledAt` = `r.getScheduledAt() != null ? r.getScheduledAt().toString() : null`; `revenue` = `MoneyView.ofMinor(0L)`; `price` = `MoneyView.ofMinor(r.getListPriceMinor())`.

- [ ] **Step 2: Failing mapper test** (`ReleaseViewMapperTest`): build a draft with one `ReleaseTrack("t1",0,250)` and a matching `Track` stub (title "Soja", duration 210, status "uploading"), assert `toDetailView` yields `tracks[0] = TrackDraftView("t1","Soja",210,"uploading",0, MoneyView.ofMinor(250))`, `genre`/`description` passthrough, `price = MoneyView.ofMinor(listPrice)`.

- [ ] **Step 3: Implement `toDetailView`** — join each `ReleaseTrack` (position/price/trackId) with the `Track` (title/duration/status) fetched via `repo.tracksByIds(trackIds)` or `findTrack`; order by `position`. If a `Track` row is missing, fall back to title `""`, duration 0, status `"uploading"`.

- [ ] **Step 4: Run test — PASS.** Spotless apply.

- [ ] **Step 5: Commit** `feat(catalog): WU-CAT-5 StudioReleaseDetailView + TrackDraftView + mapper`.

---

## Task 4: Create draft — port + service + repurpose `POST /v1/studio/releases`

**Files:** Create `catalog/application/port/in/CreateReleaseDraft.java`, `service/CreateReleaseDraftService.java`; Modify `StudioReleaseResource.java`; **retire** `SubmitRelease.java` + `SubmitReleaseService.java` (+ their tests). Test `CreateReleaseDraftServiceTest`, `catalog/it/CreateDraftIT`.

**Interfaces produced:**
- `interface CreateReleaseDraft { StudioReleaseDetailView create(CreateDraftCommand c); record CreateDraftCommand(ArtistId artistId, String title, ReleaseType type, Visibility visibility, Instant scheduledAt, String genre, String description) {} }`

- [ ] **Step 1: Port + service (TDD).** `CreateReleaseDraftServiceTest`: with a fake `CatalogRepository` + `IdGenerator` + `Clock` + `AuditWriter`, `create(...)` returns a `draft` detail view, calls `repo.saveRelease` with a `draft` release, and appends a `CREATE_DRAFT` `AuditEntry`. Implement `CreateReleaseDraftService`:

```java
@ApplicationScoped
public class CreateReleaseDraftService implements CreateReleaseDraft {
  // inject CatalogRepository repo, IdGenerator ids, Clock clock, AuditWriter audit, (mapper deps)
  @Override @Transactional
  public StudioReleaseDetailView create(CreateDraftCommand c) {
    String id = ids.newId();
    Instant now = clock.now();
    String title = (c.title() == null || c.title().isBlank()) ? "Untitled release" : c.title();
    Release r = Release.createDraft(id, c.artistId().value(), title, c.type(),
        c.visibility(), c.scheduledAt(), c.genre(), c.description(), now);
    repo.saveRelease(r);
    audit.append(new AuditEntry(ids.newId(), c.artistId().value(), "CREATE_DRAFT",
        "Release", id, AuditType.CATALOG, null, now));
    return toDetailView(r, List.of());
  }
}
```

- [ ] **Step 2: Repurpose the REST `POST`.** In `StudioReleaseResource`, replace the `submit(...)` handler with create-draft. Body `CreateDraftBody(String title, String type, String visibility, String scheduledAt, String genre, String description)`; `type` required (`ReleaseType.valueOf`), `visibility` default `PUBLIC` via `Visibility.fromDbValue`, `scheduledAt` parsed to `Instant` (nullable). Return `Response.status(201).entity(createReleaseDraft.create(cmd)).build()`. Remove the `Idempotency-Key` requirement here (drafts aren't idempotent-keyed). Follow the `write-rest-resource` skill for validation/error-envelope conventions.

- [ ] **Step 3: Retire the old all-in-one submit.** Delete `SubmitRelease`, `SubmitReleaseService`, `SubmitReleaseServiceTest`, and remove the `submitRelease` injection from `StudioReleaseResource`. (The finalize in Task 8 replaces its role.)

- [ ] **Step 4: Integration test** `CreateDraftIT` (Testcontainers): `POST /v1/studio/releases {title,type:"single"}` → 201, body `status:"draft"`, empty `tracks`; a row exists with `status='draft'`.

- [ ] **Step 5: Run unit + IT — PASS.** Spotless.

- [ ] **Step 6: Commit** `feat(catalog): WU-CAT-5 create draft endpoint (repurpose POST /studio/releases); retire all-in-one submit`.

---

## Task 5: Upload attaches the track to its release (the core fix)

**Files:** Modify `catalog/application/service/UploadReleaseTrackService.java`; Test `catalog/it/UploadAttachIT` (+ a unit assertion).

**Interfaces:** Consumes Task 1's `Release.addTrack`, `PlatformSettingsProvider` (default track price).

- [ ] **Step 1: Failing IT.** `UploadAttachIT`: create a draft, `POST /:id/tracks` (multipart WAV) → 201; then `GET /:id` shows the uploaded track in `tracks` with the returned `trackId`. (Currently fails — the track is orphaned.)

- [ ] **Step 2: Attach on upload.** In `UploadReleaseTrackService.upload(...)`, after `repo.saveTrack(stubTrack)` and before returning, append a `ReleaseTrack` to the release and re-save (guarding draft-only):

```java
if (release.getStatus() != ReleaseStatus.draft) {
  throw new IllegalTransitionException(release.getStatus(), "UPLOAD_TRACK");
}
long defaultPriceMinor = settings.current().defaultTrackPriceMinor(); // confirm accessor name
int position = release.getTracks().size();
release.addTrack(new ReleaseTrack(trackId, position, defaultPriceMinor), clock.now());
repo.saveRelease(release);
```

Inject `PlatformSettingsProvider settings` + `Clock clock` if not already present. Confirm the settings accessor for the default track price (grep `PlatformSettings`); if none exists, default to `0L` and note it.

- [ ] **Step 3: Non-draft guard test.** Extend the IT: uploading to an `in_review` release (finalize it first, Task 8) → 409 `ILLEGAL_TRANSITION`. (If Task 8 isn't merged yet in isolation, assert against a directly-persisted in_review release.)

- [ ] **Step 4: Run IT — PASS.** Spotless.

- [ ] **Step 5: Commit** `fix(catalog): WU-CAT-5 attach uploaded track to its draft release`.

---

## Task 6: Extend `PATCH /v1/studio/releases/:id` (metadata + track list)

**Files:** Modify `catalog/application/port/in/UpdateRelease.java`, `service/UpdateReleaseService.java`, `StudioReleaseResource.java`. Test `UpdateReleaseServiceTest`, `catalog/it/UpdateDraftIT`.

**Interfaces produced:**
- `UpdateReleaseCommand(String title, String genre, String description, String visibility, Instant scheduledAt, List<TrackRef> tracks)` where `record TrackRef(String trackId, int position, long priceMinor) {}`. `tracks == null` ⇒ leave tracks untouched.

- [ ] **Step 1: Failing service test.** `UpdateReleaseServiceTest`: (a) title-only patch on an `in_review` release succeeds (uses `updateTitle`, any status); (b) a patch with `tracks` on a non-draft → `IllegalTransitionException`; (c) on a draft, `tracks` replaces the list and metadata is applied; (d) a `TrackRef.trackId` not currently on the release → `TrackNotInReleaseException` (422 `TRACK_NOT_IN_RELEASE`).

- [ ] **Step 2: Implement.** `UpdateReleaseService.update(...)`:

```java
Release r = repo.findRelease(id).orElseThrow(() -> new ReleaseNotFoundException(id.value()));
requireOwner(r, artistId);
Instant now = clock.now();
if (cmd.tracks() != null) {                       // draft-only
  Set<String> existing = r.getTracks().stream().map(ReleaseTrack::trackId).collect(toSet());
  for (var t : cmd.tracks())
    if (!existing.contains(t.trackId())) throw new TrackNotInReleaseException(t.trackId());
  r.replaceTracks(cmd.tracks().stream()
      .map(t -> new ReleaseTrack(t.trackId(), t.position(), t.priceMinor())).toList(), now);
}
if (cmd.genre() != null || cmd.description() != null || cmd.visibility() != null || cmd.scheduledAt() != null) {
  r.updateMetadata(cmd.title() != null ? cmd.title() : r.getTitle(), cmd.genre(), cmd.description(),
      cmd.visibility() != null ? Visibility.fromDbValue(cmd.visibility()) : r.getVisibility(),
      cmd.scheduledAt(), now);   // draft-only
} else if (cmd.title() != null) {
  r.updateTitle(cmd.title(), now);                // any status
}
repo.saveRelease(r);
return toDetailView(r, repo.tracksByIds(r.getTracks().stream().map(ReleaseTrack::trackId).toList()));
```

Add `TrackNotInReleaseException` (domain) mapped to 422 `TRACK_NOT_IN_RELEASE` in the exception mapper.

- [ ] **Step 3: REST body.** Extend `UpdateReleaseBody` to `{ title, genre, description, visibility, scheduledAt, tracks: [{trackId, position, priceMinor}] }`; map to the command; return `StudioReleaseDetailView`.

- [ ] **Step 4: IT `UpdateDraftIT`** — create draft → upload 1 track → PATCH price+order+genre → `GET /:id` reflects them; PATCH tracks on an in_review release → 409.

- [ ] **Step 5: Run — PASS.** Spotless. **Commit** `feat(catalog): WU-CAT-5 extend PATCH for draft metadata + track list`.

---

## Task 7: `DELETE /v1/studio/releases/:id/tracks/:trackId`

**Files:** Create `catalog/application/port/in/RemoveReleaseTrack.java`, `service/RemoveReleaseTrackService.java`; add `deleteTrack(TrackId)` to `CatalogRepository` + `JpaCatalogRepository`; Modify `StudioReleaseResource.java`. Test `RemoveReleaseTrackServiceTest`, extend `UpdateDraftIT`.

**Interfaces produced:** `interface RemoveReleaseTrack { void remove(ReleaseId releaseId, ArtistId artistId, TrackId trackId); }`; `void CatalogRepository.deleteTrack(TrackId id)`.

- [ ] **Step 1: Failing test.** `RemoveReleaseTrackServiceTest`: removes the `ReleaseTrack` from a draft and calls `repo.deleteTrack` for the orphaned stub; non-draft → `IllegalTransitionException`; unknown track → `ReleaseNotFoundException`/404 (or a no-op 204 — pick 404 for an unknown trackId not on the release).

- [ ] **Step 2: Implement service** — load release, `requireOwner`, `r.removeTrack(trackId, now)` (draft-only), `repo.saveRelease(r)`, `repo.deleteTrack(new TrackId(trackId))`. Implement `deleteTrack` in `JpaCatalogRepository` (`em.remove` the `Track` by id if present).

- [ ] **Step 3: REST** — `@DELETE @Path("/{id}/tracks/{trackId}")` → `removeReleaseTrack.remove(...)` → `Response.noContent().build()`.

- [ ] **Step 4: IT** — upload 2 tracks to a draft, DELETE one, `GET /:id` shows 1; DELETE on a non-draft → 409.

- [ ] **Step 5: Run — PASS.** Spotless. **Commit** `feat(catalog): WU-CAT-5 delete draft track endpoint`.

---

## Task 8: Finalize — `POST /v1/studio/releases/:id/submit`

**Files:** Create `catalog/application/port/in/FinalizeRelease.java`, `service/FinalizeReleaseService.java`; Modify `StudioReleaseResource.java`. Test `FinalizeReleaseServiceTest`, `catalog/it/FinalizeReleaseIT`.

**Interfaces produced:** `interface FinalizeRelease { StudioReleaseDetailView finalize(ReleaseId id, ArtistId artistId, String idempotencyKey); }`.

- [ ] **Step 1: Failing tests.** `FinalizeReleaseServiceTest`: a draft `single` with exactly 1 track finalizes to `in_review` with the recomputed list price + a `SUBMIT_RELEASE` audit entry; a `single` with 0 or 2 tracks → `TrackCountInvalidException` (422); a non-draft → `IllegalTransitionException` (409); the same idempotency key twice returns the same view without re-transitioning.

- [ ] **Step 2: Implement** (reuse the INV-12 count check + idempotency pattern from the retired `SubmitReleaseService`):

```java
@ApplicationScoped
public class FinalizeReleaseService implements FinalizeRelease {
  // inject CatalogRepository repo, PlatformSettingsProvider settings, IdGenerator ids, Clock clock, AuditWriter audit
  @Override @Transactional
  public StudioReleaseDetailView finalize(ReleaseId id, ArtistId artistId, String key) {
    Optional<Release> seen = repo.findReleaseByIdempotencyKey(key);
    if (seen.isPresent()) return toDetailView(seen.get(), tracksOf(seen.get()));
    Release r = repo.findRelease(id).orElseThrow(() -> new ReleaseNotFoundException(id.value()));
    requireOwner(r, artistId);
    validateTrackCount(r.getType(), r.getTracks().size());   // INV-12 matrix
    Instant now = clock.now();
    r.submit(settings.current().bundleDiscountPct(), now);   // draft -> in_review + recompute price
    repo.saveReleaseWithIdempotencyKey(r, key);
    audit.append(new AuditEntry(ids.newId(), artistId.value(), "SUBMIT_RELEASE",
        "Release", r.getId(), AuditType.CATALOG, null, now));
    return toDetailView(r, tracksOf(r));
  }
}
```

`validateTrackCount`: `single`==1, `ep` 3–6, `album` >=7, `mixtape` >=1, else `TrackCountInvalidException`.

- [ ] **Step 3: REST** — `@POST @Path("/{id}/submit")` with `@HeaderParam("Idempotency-Key")`; blank key → 400 `MISSING_IDEMPOTENCY_KEY` (mirror the old submit's guard); return 200 `StudioReleaseDetailView`.

- [ ] **Step 4: IT `FinalizeReleaseIT`** — full round trip: create draft → upload 1 track → submit → 200 `status:"in_review"`, list price correct; edit (PATCH tracks) after submit → 409; single with 2 tracks → 422; missing key → 400.

- [ ] **Step 5: Run — PASS.** Spotless. **Commit** `feat(catalog): WU-CAT-5 finalize release (POST /:id/submit) draft -> in_review`.

---

## Task 9: `GET /:id` detail view, contract tests, docs, backlog

**Files:** Modify `StudioReleaseResource.java` (GET returns detail); `catalog/it/` contract test; `backend/docs/00-system-architecture.md`, `API-CONTRACT.md`, `docs/architecture/catalog.md`, `backend/.project/backlog.yaml`.

- [ ] **Step 1: `GET /:id` → `StudioReleaseDetailView`.** Change the `get(...)` handler to return the detail view (via `GetRelease` returning the richer view, or map in the resource). The **list** endpoint keeps returning `PageView<StudioReleaseView>` unchanged.

- [ ] **Step 2: Contract test.** Extend the catalog contract test: `StudioReleaseView` (list) shape byte-for-byte unchanged; `StudioReleaseDetailView` is present-and-additive on `GET /:id`; `tracks[].price` serializes as `{amount,currency}`; enums lowercase. Follow the `contract-conformance` skill.

- [ ] **Step 3: Register WU-CAT-5** in `backend/.project/backlog.yaml` (catalog phase, `depends_on` the release-lifecycle WUs, owner `backend-engineer`, status `in_progress`).

- [ ] **Step 4: Docs.** ADR-29 in `00-system-architecture.md §9` (repurpose `POST /studio/releases`→draft; the `draft → upload-attached → finalize` flow supersedes the non-composable one-shot submit; upload now attaches tracks). Document the five endpoints + `StudioReleaseDetailView`/`TrackDraftView` in `API-CONTRACT.md`. As-built note in `docs/architecture/catalog.md`.

- [ ] **Step 5: Full gate (user runs).** `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh` — ArchUnit green, Flyway on empty DB, coverage, Spotless, all tests. Manual smoke: create draft → upload a real WAV → confirm attached in `GET /:id` → set price/order via PATCH → submit → `in_review` with correct list price; single-with-2 → 422; edit-after-submit → 409.

- [ ] **Step 6: Commit** `docs(catalog): WU-CAT-5 GET detail view, contract tests, ADR-29, API-CONTRACT, backlog` and open the PR per `open-pull-request` (base `master`; **do not** use `open-pr.sh` if it stages local-only files — mirror the manual `gh pr create --base master` used for the frontend PRs).

---

## Self-Review (author checklist)

- **Spec coverage:** create draft (T4), upload-attach (T5), PATCH metadata+tracks (T6), delete track (T7), finalize (T8), GET detail + docs (T9), domain (T1), schema/persistence (T2), views (T3). Splits/cover/featured/label explicitly out. ✓
- **Type consistency:** `createDraft`/`addTrack`/`removeTrack`/`replaceTracks`/`updateMetadata`/`submit` signatures identical across T1 and their callers (T4/T5/T6/T8). `StudioReleaseDetailView`/`TrackDraftView`/`TrackRef`/`CreateDraftCommand`/`UpdateReleaseCommand` names consistent. `saveRelease` reused for all track mutations (no bespoke persistence). ✓
- **No placeholders:** real code for the domain FSM and the two novel services; mechanical REST/entity scaffolding references the backend skills with exact field lists. The `V970`/settings-accessor names are flagged "confirm at implementation time" (real, not vague). ✓
- **Invariants:** INV-5 (submit recompute), INV-12 (finalize count matrix), INV-10 (CREATE_DRAFT/SUBMIT_RELEASE audit), idempotency on finalize, draft-only guards — each mapped to a task + test. ✓

## Position

WU-CAT-5 (this) unblocks: **WU-CAT-6** (per-track splits + confirmation), **Studio Slice 3b-frontend** (wizard wiring: real multipart upload + step wiring to these endpoints), and later cover-image upload.
