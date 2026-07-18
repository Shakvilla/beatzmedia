# WU-CAT-6 — Split Persistence + Finalize Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist per-track collaborator royalty splits through the wizard's existing draft `PATCH`, read them back on release detail views, and validate the per-track split sum at finalize — so the shipped `hasPendingSplits` go-live guard and downstream payout subdivision have real `split_entry` data.

**Architecture:** Backend-only, catalog module, strict hexagonal (`adapter → application → domain`). The `Release` aggregate gains a `List<SplitEntry>` loaded on the detail read path and validated at `submit()`. Splits are written through a **dedicated** repository method (`saveTrackSplits`) decoupled from `saveRelease`, so no unrelated save (upload, scheduler go-live) can ever wipe them. Splits ride nested inside the existing `tracks[]` PATCH payload.

**Tech Stack:** Java 25, Quarkus 3.36, Hibernate ORM (JPA/`EntityManager`), PostgreSQL 16 (`split_entry` table already shipped in V305), JUnit 5 + Testcontainers, Maven (`./backend/mvnw`).

## Global Constraints

- **Implicit remainder** — `split_entry` holds **only collaborators**; the creator's share = `100 − Σ(collaborators)` and is **never stored**. No `self` row is persisted.
- **Interim confirmation = `pending`** — every collaborator persisted by this WU is stored `confirmation = "pending"` (domain `SplitConfirmation.pending`). The service sets it; it is **not** a client input field.
- **Splits nested in `tracks[]`** — the PATCH carries splits inside each track entry; a split can never reference a track absent from the release.
- **Replace-set semantics per track** — for a track whose incoming `splits` is `null`, its existing splits are **untouched**; a non-null list (incl. empty `[]`) **replaces** that track's collaborator set wholesale. Splits only arrive on the `tracks != null` PATCH path (they are nested in tracks), so they are inherently draft-only.
- **`SPLIT_OVER_100` (422)** is reserved for the **finalize per-track sum invariant** (INV-12, `Σ(collaborator percent) ≤ 100`). Per-row `percent` 0–100 is a **standard field-validation 422** (Bean Validation `@Min(0) @Max(100)` on the REST DTO), never `SPLIT_OVER_100`.
- **INV-12 pending rule gates go-live, not submit.** `submit()` (draft→in_review) validates only the sum. The existing `CatalogRepository.hasPendingSplits` guard (WU-CAT-4) blocks in_review→live; this WU adds **no** go-live code.
- **No new migration.** `split_entry` (PK `id`; `track_id` FK `ON DELETE CASCADE`; `percent CHECK 0–100`; `confirmation CHECK` incl. `pending`), `idx_split_track`, and `SplitEntryEntity` (`catalog/adapter/out/persistence/SplitEntryEntity.java`) all already exist (V305). Confirm with `bash backend/scripts/next-migration-version.sh` but write none.
- **Hexagonal** — all changes are catalog-internal; domain stays framework-free (no Jakarta/Quarkus/Hibernate imports in `domain/`); no cross-module imports.
- **INV-10 audit** — the split write happens inside `UpdateReleaseService`'s existing `UPDATE_RELEASE` audit entry (same `@Transactional`); no new audit path.
- **Verification** — `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh` are run by **the user** (IntelliJ JPS races the build); the implementer runs targeted Maven test commands only. Branch: `feat/WU-CAT-6-split-persistence` (already checked out).
- **Two `CatalogRepository` implementors** must both compile after any port change: `JpaCatalogRepository` (production) and `catalog/fakes/FakeCatalogRepository` (test).

---

## File Structure

- `catalog/domain/Release.java` — add `List<SplitEntry> splits` field + accessor; `submit()` per-track Σ≤100 guard; a backward-compatible `reconstitute` overload carrying splits. (Task 1)
- `catalog/application/port/out/CatalogRepository.java` — new `saveTrackSplits(String trackId, List<SplitEntry>)`. (Task 2)
- `catalog/adapter/out/persistence/JpaCatalogRepository.java` — implement `saveTrackSplits`; load splits in `findRelease`; `toReleaseDomain` uses the splits-carrying `reconstitute`. (Task 2)
- `catalog/fakes/FakeCatalogRepository.java` — implement `saveTrackSplits` (in-memory) + return stored splits from `findRelease`. (Task 2)
- `catalog/application/port/in/UpdateRelease.java` — `TrackRef.splits` + `SplitRef`. (Task 3)
- `catalog/application/service/UpdateReleaseService.java` — map `SplitRef → SplitEntry(pending)` and call `saveTrackSplits`. (Task 3)
- `catalog/application/port/in/TrackDraftView.java` — `splits` + `SplitView`. (Task 4)
- `catalog/application/service/ReleaseViewMapper.java` — build `SplitView` per track from `release.getSplits()`. (Task 4)
- `catalog/adapter/in/rest/StudioReleaseResource.java` — `TrackRefBody.splits` + `SplitRefBody` (Bean Validation) + PATCH mapping. (Task 4)
- Docs/backlog/contract + verify + PR. (Task 5)

---

### Task 1: Domain — `Release` holds splits and validates the sum at `submit()`

**Files:**
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/domain/Release.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/domain/ReleaseSplitsTest.java` (create)

**Interfaces:**
- Consumes: existing `SplitEntry(String id, String trackId, String name, String email, String role, int percent, SplitConfirmation confirmation)` and `SplitOver100Exception(String message)`, both already in `catalog/domain/`.
- Produces:
  - `List<SplitEntry> Release.getSplits()` — the release's collaborator splits (empty unless loaded).
  - `Release.reconstitute(...14 existing args..., List<SplitEntry> splits)` — new overload; the existing 14-arg `reconstitute` still works and delegates with `List.of()`.
  - `Release.submit(int bundleDiscountPct, Instant now)` now additionally throws `SplitOver100Exception` when any track's `Σ percent > 100`.

- [ ] **Step 1: Write the failing test** — create `ReleaseSplitsTest.java`:

```java
package org.shakvilla.beatzmedia.catalog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ReleaseSplitsTest {

  private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

  private SplitEntry split(String trackId, int percent) {
    return new SplitEntry("s-" + trackId + "-" + percent, trackId, "Producer",
        "prod@example.com", "Producer", percent, SplitConfirmation.pending);
  }

  private Release draftWithTrackAndSplits(List<SplitEntry> splits) {
    return Release.reconstitute(
        "r1", "artist-1", "Title", ReleaseType.single, ReleaseStatus.draft,
        Visibility.PUBLIC, null, null, 0L, NOW, NOW,
        List.of(new ReleaseTrack("t1", 0, 250L)), null, null, splits);
  }

  @Test
  void reconstitute_carriesSplits_andDefaultOverloadIsEmpty() {
    Release withSplits = draftWithTrackAndSplits(List.of(split("t1", 30)));
    assertEquals(1, withSplits.getSplits().size());

    Release legacy = Release.reconstitute(
        "r2", "artist-1", "T", ReleaseType.single, ReleaseStatus.draft,
        Visibility.PUBLIC, null, null, 0L, NOW, NOW,
        List.of(new ReleaseTrack("t1", 0, 250L)), null, null);
    assertTrue(legacy.getSplits().isEmpty());
  }

  @Test
  void submit_allowsPerTrackSumUnderOrEqual100() {
    // 40 + 60 = 100 on t1 (creator remainder 0) — valid (<= 100).
    Release r = draftWithTrackAndSplits(List.of(split("t1", 40), split("t1", 60)));
    r.submit(24, NOW); // no throw
    assertEquals(ReleaseStatus.in_review, r.getStatus());
  }

  @Test
  void submit_allowsSumBelow100_creatorHoldsRemainder() {
    Release r = draftWithTrackAndSplits(List.of(split("t1", 30))); // creator implicit 70
    r.submit(24, NOW);
    assertEquals(ReleaseStatus.in_review, r.getStatus());
  }

  @Test
  void submit_rejectsPerTrackSumOver100() {
    Release r = draftWithTrackAndSplits(List.of(split("t1", 60), split("t1", 60))); // 120
    assertThrows(SplitOver100Exception.class, () -> r.submit(24, NOW));
  }

  @Test
  void submit_validatesEachTrackIndependently() {
    Release r = Release.reconstitute(
        "r3", "artist-1", "Album", ReleaseType.ep, ReleaseStatus.draft,
        Visibility.PUBLIC, null, null, 0L, NOW, NOW,
        List.of(new ReleaseTrack("t1", 0, 250L), new ReleaseTrack("t2", 1, 250L),
            new ReleaseTrack("t3", 2, 250L)),
        null, null,
        List.of(split("t1", 50), split("t2", 200))); // t1 ok, t2 over
    assertThrows(SplitOver100Exception.class, () -> r.submit(24, NOW));
  }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `cd backend && ./mvnw -q test -Dtest=ReleaseSplitsTest`
Expected: COMPILE FAILURE — `reconstitute` has no 15-arg overload and `getSplits()` does not exist.

- [ ] **Step 3: Add the splits field, accessor, overload, and submit guard** in `Release.java`.

3a. Add the field after `private String description;`:

```java
  private String description;
  private List<SplitEntry> splits;
```

3b. Add `List<SplitEntry> splits` as the final parameter of the **private constructor** and assign it defensively. Change the constructor signature's last param and body:

```java
      String genre,
      String description,
      List<SplitEntry> splits) {
    // ... existing assignments ...
    this.genre = genre;
    this.description = description;
    this.splits = List.copyOf(splits);
  }
```

3c. Every existing call to the private constructor must pass splits. In `create(...)`, `createDraft(...)`, and the existing 14-arg `reconstitute(...)`, append `List.of()` as the final constructor argument. For example in `createDraft`:

```java
    return new Release(
        id, artistId, title, type, ReleaseStatus.draft, visibility,
        scheduledAt, null, 0L, now, now, List.of(), genre, description, List.of());
```

(Apply the same `, List.of()` append to the `new Release(...)` calls inside `create` and the existing `reconstitute`.)

3d. Add the new splits-carrying `reconstitute` overload directly below the existing one:

```java
  /** Reconstitute a release including its persisted collaborator splits (WU-CAT-6). */
  public static Release reconstitute(
      String id,
      String artistId,
      String title,
      ReleaseType type,
      ReleaseStatus status,
      Visibility visibility,
      Instant scheduledAt,
      Instant wentLiveAt,
      long listPriceMinor,
      Instant createdAt,
      Instant updatedAt,
      List<ReleaseTrack> tracks,
      String genre,
      String description,
      List<SplitEntry> splits) {
    return new Release(
        id, artistId, title, type, status, visibility,
        scheduledAt, wentLiveAt, listPriceMinor, createdAt, updatedAt, tracks, genre, description,
        splits);
  }
```

3e. Add the accessor next to the other getters:

```java
  public List<SplitEntry> getSplits() { return splits; }
```

3f. Add the INV-12 sum guard **at the top of `submit(...)`**, before the transition/price recompute. Insert:

```java
  public void submit(int bundleDiscountPct, Instant now) {
    validateSplitSums();
    // ... existing submit body ...
  }

  /** INV-12: for each track, Σ(collaborator percent) ≤ 100 (creator holds the remainder). */
  private void validateSplitSums() {
    java.util.Map<String, Integer> byTrack = new java.util.HashMap<>();
    for (SplitEntry s : splits) {
      byTrack.merge(s.trackId(), s.percent(), Integer::sum);
    }
    for (var e : byTrack.entrySet()) {
      if (e.getValue() > 100) {
        throw new SplitOver100Exception(
            "Split percentages for track " + e.getKey() + " sum to " + e.getValue() + " (> 100)");
      }
    }
  }
```

- [ ] **Step 4: Run the test to green**

Run: `cd backend && ./mvnw -q test -Dtest=ReleaseSplitsTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Compile the whole module to confirm no caller broke**

Run: `cd backend && ./mvnw -q -o compile`
Expected: BUILD SUCCESS (the 14-arg `reconstitute` still exists, so `JpaCatalogRepository.toReleaseDomain` still compiles).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/catalog/domain/Release.java \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/domain/ReleaseSplitsTest.java
git commit -m "feat(catalog): WU-CAT-6 Release holds splits + finalize sum guard"
```

---

### Task 2: Persistence — dedicated `saveTrackSplits` write + load splits on `findRelease`

**Files:**
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/out/CatalogRepository.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence/JpaCatalogRepository.java`
- Modify: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/fakes/FakeCatalogRepository.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence/SplitPersistenceIT.java` (create)

**Interfaces:**
- Consumes: `Release.getSplits()` / the splits `reconstitute` overload (Task 1); existing `SplitEntryEntity` (fields `id, trackId, name, email, role, int percent, String confirmation`).
- Produces: `void CatalogRepository.saveTrackSplits(String trackId, List<SplitEntry> splits)` — wholesale replace of one track's `split_entry` rows; `findRelease(...)` now returns a `Release` whose `getSplits()` is populated from `split_entry`.

- [ ] **Step 1: Write the failing integration test** — create `SplitPersistenceIT.java`:

```java
package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.SplitConfirmation;
import org.shakvilla.beatzmedia.catalog.domain.SplitEntry;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;

import io.quarkus.test.junit.QuarkusTest;

/**
 * WU-CAT-6: the split_entry write path. Persists a draft with one track + collaborator splits,
 * re-reads via findRelease, and asserts the replace-set semantics.
 */
@QuarkusTest
@Tag("it")
class SplitPersistenceIT {

  @Inject CatalogRepository repo;

  private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

  private SplitEntry split(String id, String trackId, int percent) {
    return new SplitEntry(id, trackId, "Producer", "prod@example.com", "Producer",
        percent, SplitConfirmation.pending);
  }

  @Transactional
  void seedReleaseWithTrack(String releaseId, String trackId, String artistId) {
    // A real track row must exist (split_entry.track_id FK -> track(id)).
    repo.saveTrack(Track.stub(new TrackId(trackId), trackId + " title", new ArtistId(artistId)));
    Release r = Release.reconstitute(
        releaseId, artistId, "T", ReleaseType.single, org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus.draft,
        Visibility.PUBLIC, null, null, 0L, NOW, NOW,
        List.of(new ReleaseTrack(trackId, 0, 250L)), null, null, List.of());
    repo.saveRelease(r);
  }

  @Test
  @Transactional
  void saveTrackSplits_persists_and_findRelease_readsBack() {
    seedReleaseWithTrack("rel-sp-1", "trk-sp-1", "artist-sp-1");

    repo.saveTrackSplits("trk-sp-1", List.of(split("sp-a", "trk-sp-1", 30), split("sp-b", "trk-sp-1", 20)));

    Release read = repo.findRelease(new ReleaseId("rel-sp-1")).orElseThrow();
    assertEquals(2, read.getSplits().size());
    assertEquals(50, read.getSplits().stream().mapToInt(SplitEntry::percent).sum());
    assertTrue(read.getSplits().stream().allMatch(s -> s.confirmation() == SplitConfirmation.pending));
  }

  @Test
  @Transactional
  void saveTrackSplits_replacesWholesale_andEmptyClears() {
    seedReleaseWithTrack("rel-sp-2", "trk-sp-2", "artist-sp-2");

    repo.saveTrackSplits("trk-sp-2", List.of(split("x", "trk-sp-2", 40)));
    repo.saveTrackSplits("trk-sp-2", List.of(split("y", "trk-sp-2", 10))); // replace
    Release afterReplace = repo.findRelease(new ReleaseId("rel-sp-2")).orElseThrow();
    assertEquals(1, afterReplace.getSplits().size());
    assertEquals(10, afterReplace.getSplits().get(0).percent());

    repo.saveTrackSplits("trk-sp-2", List.of()); // clear
    Release afterClear = repo.findRelease(new ReleaseId("rel-sp-2")).orElseThrow();
    assertTrue(afterClear.getSplits().isEmpty());
  }
}
```

> Note: if `Track.stub(...)` does not exist with that signature, use whatever factory the existing upload tests use to create a `Track` row (grep `saveTrack(` in `backend/src/test`); the only requirement is a persisted `track` row whose id matches the split's `track_id` so the FK holds.

- [ ] **Step 2: Run it and watch it fail**

Run: `cd backend && ./mvnw -q test -Dtest=SplitPersistenceIT`
Expected: COMPILE FAILURE — `repo.saveTrackSplits` does not exist.

- [ ] **Step 3: Add the port method** in `CatalogRepository.java`, next to `saveRelease`:

```java
  /**
   * WU-CAT-6 — wholesale-replace one track's collaborator splits in {@code split_entry}. Deletes
   * the track's existing rows and inserts {@code splits} (empty list clears them). Decoupled from
   * {@link #saveRelease} so an unrelated save (upload, scheduler go-live) can never wipe splits.
   */
  void saveTrackSplits(String trackId, List<SplitEntry> splits);
```

Add the import `import org.shakvilla.beatzmedia.catalog.domain.SplitEntry;` if not present.

- [ ] **Step 4: Implement it in `JpaCatalogRepository.java`** (mirror the existing `release_track` remove-then-persist pattern), placed near `saveRelease`:

```java
  @Override
  @Transactional
  public void saveTrackSplits(String trackId, List<SplitEntry> splits) {
    // Remove managed entities (not a bulk JPQL DELETE) so the L1 cache stays in sync.
    List<SplitEntryEntity> existing = em.createQuery(
            "SELECT s FROM SplitEntryEntity s WHERE s.trackId = :tid", SplitEntryEntity.class)
        .setParameter("tid", trackId)
        .getResultList();
    existing.forEach(em::remove);
    em.flush();
    for (SplitEntry s : splits) {
      SplitEntryEntity e = new SplitEntryEntity();
      e.id = s.id();
      e.trackId = s.trackId();
      e.name = s.name();
      e.email = s.email();
      e.role = s.role();
      e.percent = s.percent();
      e.confirmation = s.confirmation().name();
      em.persist(e);
    }
  }
```

Add imports for `SplitEntry` and `jakarta.transaction.Transactional` if not already present (the class already uses `EntityManager`).

- [ ] **Step 5: Load splits in `findRelease` and thread them through `toReleaseDomain`.**

5a. Change `toReleaseDomain` to accept and pass splits:

```java
  private Release toReleaseDomain(
      ReleaseEntity e, List<ReleaseTrackEntity> trackEntities, List<SplitEntry> splits) {
    List<ReleaseTrack> tracks = trackEntities.stream()
        .map(rt -> new ReleaseTrack(rt.trackId, rt.pk.position, rt.priceMinor))
        .toList();
    return Release.reconstitute(
        e.id, e.artistId, e.title, ReleaseType.valueOf(e.type), ReleaseStatus.valueOf(e.status),
        Visibility.fromDbValue(e.visibility), e.scheduledAt, e.wentLiveAt, e.listPriceMinor,
        e.createdAt, e.updatedAt, tracks, e.genre, e.description, splits);
  }
```

5b. In `findRelease`, load the release's splits (by its track ids) and pass them:

```java
  @Override
  public Optional<Release> findRelease(ReleaseId id) {
    ReleaseEntity e = em.find(ReleaseEntity.class, id.value());
    if (e == null) return Optional.empty();
    List<ReleaseTrackEntity> tracks = em.createQuery(
            "SELECT rt FROM ReleaseTrackEntity rt WHERE rt.pk.releaseId = :rid ORDER BY rt.pk.position",
            ReleaseTrackEntity.class)
        .setParameter("rid", id.value())
        .getResultList();
    List<SplitEntry> splits = loadSplitsForTracks(
        tracks.stream().map(rt -> rt.trackId).toList());
    return Optional.of(toReleaseDomain(e, tracks, splits));
  }

  /** Load split_entry rows for the given track ids into domain SplitEntry value objects. */
  private List<SplitEntry> loadSplitsForTracks(List<String> trackIds) {
    if (trackIds.isEmpty()) return List.of();
    return em.createQuery(
            "SELECT s FROM SplitEntryEntity s WHERE s.trackId IN :tids", SplitEntryEntity.class)
        .setParameter("tids", trackIds)
        .getResultList().stream()
        .map(s -> new SplitEntry(s.id, s.trackId, s.name, s.email, s.role, s.percent,
            SplitConfirmation.valueOf(s.confirmation)))
        .toList();
  }
```

5c. The **list** path (`releasesByArtist`) and any other `toReleaseDomain` call site that does **not** need splits must pass `List.of()` (the list view omits splits — avoids N+1). Update those call sites, e.g.:

```java
        .map(e -> toReleaseDomain(e, tracksByRelease.getOrDefault(e.id, List.of()), List.of()))
```

Add imports `SplitEntry`, `SplitConfirmation`.

- [ ] **Step 6: Implement `saveTrackSplits` in the test fake** `FakeCatalogRepository.java` as a side-store plus two test accessors (the fake already has `private final Map<String, Release> releases`, `Map<String, Track> tracks`, etc.). No `findRelease` override is needed — none of this WU's tests read splits back through the fake (the real `findRelease`-loads-splits path is covered by `SplitPersistenceIT` against `JpaCatalogRepository`). Add near the other fields/methods:

```java
  private final Map<String, List<SplitEntry>> splitsByTrack = new HashMap<>();
  private final List<String> saveTrackSplitsCalls = new ArrayList<>();

  @Override
  public void saveTrackSplits(String trackId, List<SplitEntry> splits) {
    saveTrackSplitsCalls.add(trackId);
    splitsByTrack.put(trackId, List.copyOf(splits)); // wholesale replace (empty clears)
  }

  /** Test accessor: the splits stored for a track by the last saveTrackSplits call. */
  public List<SplitEntry> splitsFor(String trackId) {
    return splitsByTrack.getOrDefault(trackId, List.of());
  }

  /** Test accessor: trackIds saveTrackSplits was called for, in order. */
  public List<String> saveTrackSplitsCalls() {
    return saveTrackSplitsCalls;
  }
```

Add imports `SplitEntry`, `java.util.ArrayList` if absent (the file already imports `HashMap`, `List`, `Map`).

- [ ] **Step 7: Run the persistence IT + the fake's existing consumers**

Run: `cd backend && ./mvnw -q test -Dtest=SplitPersistenceIT`
Expected: PASS (2 tests).
Run: `cd backend && ./mvnw -q -o test-compile`
Expected: BUILD SUCCESS (both implementors compile).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/out/CatalogRepository.java \
        backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence/JpaCatalogRepository.java \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/fakes/FakeCatalogRepository.java \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence/SplitPersistenceIT.java
git commit -m "feat(catalog): WU-CAT-6 split_entry write path + load on findRelease"
```

---

### Task 3: Application — `UpdateReleaseService` persists nested splits as `pending`

**Files:**
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/in/UpdateRelease.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/service/UpdateReleaseService.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/application/UpdateReleaseServiceTest.java` (extend)

**Interfaces:**
- Consumes: `CatalogRepository.saveTrackSplits` (Task 2); `SplitEntry`, `SplitConfirmation.pending`, `IdGenerator.newId()`.
- Produces: `UpdateReleaseCommand.TrackRef` gains `List<SplitRef> splits`; new `SplitRef(String name, String email, String role, int percent)`. `UpdateReleaseService` writes each track's splits (`pending`) via `saveTrackSplits`.

- [ ] **Step 1: Write the failing test** — add to `UpdateReleaseServiceTest.java`. The fixture already has fields `repo` (`FakeCatalogRepository`) and `service`, an `ARTIST` constant (`artist-1`), and a `draftWithTrack()` helper that creates release `r1` with track `t1` and calls `repo.addRelease(r)`. The `splitsFor`/`saveTrackSplitsCalls` accessors were added to the fake in Task 2. Add these imports at the top: `import org.shakvilla.beatzmedia.catalog.application.port.in.UpdateRelease.SplitRef;`, `import org.shakvilla.beatzmedia.catalog.domain.SplitConfirmation;`, `import static org.junit.jupiter.api.Assertions.assertTrue;`. Then add:

```java
  @Test
  void update_persistsNestedSplits_asPending() {
    draftWithTrack(); // r1 with track t1

    UpdateReleaseCommand cmd = new UpdateReleaseCommand(
        null, null, null, null, null,
        List.of(new TrackRef("t1", 0, 250L,
            List.of(new SplitRef("Producer", "prod@example.com", "Producer", 30)))));

    service.update(new ReleaseId("r1"), ARTIST, cmd);

    var stored = repo.splitsFor("t1");
    assertEquals(1, stored.size());
    assertEquals(30, stored.get(0).percent());
    assertEquals(SplitConfirmation.pending, stored.get(0).confirmation());
    assertEquals("prod@example.com", stored.get(0).email());
  }

  @Test
  void update_nullSplits_doesNotTouchThatTracksSplits() {
    draftWithTrack();
    UpdateReleaseCommand cmd = new UpdateReleaseCommand(
        null, null, null, null, null,
        List.of(new TrackRef("t1", 0, 250L, null))); // splits == null
    service.update(new ReleaseId("r1"), ARTIST, cmd);
    assertTrue(repo.saveTrackSplitsCalls().isEmpty());
  }
```

> These use the 4-arg `TrackRef` form. The file's existing tests use the 3-arg form (`new TrackRef("t1", 0, 500L)`) — Step 3 adds a 3-arg convenience constructor so those keep compiling.

- [ ] **Step 2: Run it and watch it fail**

Run: `cd backend && ./mvnw -q test -Dtest=UpdateReleaseServiceTest`
Expected: COMPILE FAILURE — `TrackRef` has no 4-arg form / `SplitRef` missing.

- [ ] **Step 3: Add `splits` to `TrackRef` (with a 3-arg convenience constructor) + the `SplitRef` record** in `UpdateRelease.java`. The convenience constructor keeps every existing 3-arg `new TrackRef(...)` call site (this test file's other tests, `StudioReleaseResource`'s current mapping) compiling until Task 4 updates the resource:

```java
  /** A single track's order + price within a wholesale track-list replacement (WU-CAT-6: + splits). */
  record TrackRef(String trackId, int position, long priceMinor, List<SplitRef> splits) {
    /** Legacy 3-arg form — no split changes for this track (splits == null → untouched). */
    public TrackRef(String trackId, int position, long priceMinor) {
      this(trackId, position, priceMinor, null);
    }
  }

  /** A collaborator's royalty split of a track (collaborators only — creator is implicit). */
  record SplitRef(String name, String email, String role, int percent) {}
```

(The `splitsFor` / `saveTrackSplitsCalls` accessors were already added to `FakeCatalogRepository` in Task 2 Step 6.)

- [ ] **Step 4: Persist splits in `UpdateReleaseService.update`.** The existing method already builds `ReleaseTrack`s from `command.tracks()` and calls `release.replaceTracks(...)`. Two edits:

4a. Include `splits()` when mapping to `ReleaseTrack` — **no**, `ReleaseTrack` is unchanged. Instead, after `repo.saveRelease(release);` (and still inside the same `@Transactional`), write each track's splits:

```java
    repo.saveRelease(release);

    // WU-CAT-6: persist per-track collaborator splits (pending). Only tracks whose splits list is
    // non-null are touched; null leaves that track's existing splits intact.
    if (command.tracks() != null) {
      for (UpdateRelease.TrackRef t : command.tracks()) {
        if (t.splits() == null) continue;
        List<SplitEntry> entries = t.splits().stream()
            .map(s -> new SplitEntry(
                ids.newId(), t.trackId(), s.name(), s.email(), s.role(), s.percent(),
                SplitConfirmation.pending))
            .toList();
        repo.saveTrackSplits(t.trackId(), entries);
      }
    }
```

Add imports `SplitEntry`, `SplitConfirmation`, and `UpdateRelease` (or reference the nested `TrackRef` already imported).

> The draft-only + track-membership guards already run earlier in this method for the `command.tracks() != null` branch, so splits (which only arrive nested in `tracks`) inherit them — no extra guard needed.

- [ ] **Step 5: Run the service tests to green**

Run: `cd backend && ./mvnw -q test -Dtest=UpdateReleaseServiceTest`
Expected: PASS (existing tests + the 2 new ones).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/in/UpdateRelease.java \
        backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/service/UpdateReleaseService.java \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/application/UpdateReleaseServiceTest.java \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/fakes/FakeCatalogRepository.java
git commit -m "feat(catalog): WU-CAT-6 UpdateRelease persists nested splits as pending"
```

---

### Task 4: REST + view — nested `splits` in the PATCH body and detail response

**Files:**
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/in/TrackDraftView.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/service/ReleaseViewMapper.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/in/rest/StudioReleaseResource.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/adapter/in/rest/StudioReleaseSplitsIT.java` (create)

**Interfaces:**
- Consumes: `UpdateRelease.TrackRef.splits` / `SplitRef` (Task 3); `release.getSplits()` (Task 1); `hasPendingSplits` (existing).
- Produces: `TrackDraftView` gains `List<SplitView> splits`; new `SplitView(String id, String name, String email, String role, int percent, String confirmation)`. `StudioReleaseResource.TrackRefBody` gains `List<SplitRefBody> splits`; new `SplitRefBody(String name, String email, String role, @Min(0) @Max(100) int percent)`.

- [ ] **Step 1: Write the failing REST integration test** — create `StudioReleaseSplitsIT.java`. Model it on the existing studio-release REST IT in the same package (grep for `@TestHTTPEndpoint` / the draft-create helper used by WU-CAT-5 ITs and reuse the auth + create-draft + upload-track helpers). It must assert:

```java
package org.shakvilla.beatzmedia.catalog.adapter.in.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * WU-CAT-6 REST round trip: PATCH nested splits -> GET returns them; finalize with Σ>100 -> 422
 * SPLIT_OVER_100; per-row percent > 100 -> 422 field validation; a pending split blocks go-live.
 */
@QuarkusTest
@Tag("it")
class StudioReleaseSplitsIT {

  // Reuse this package's existing helpers: authenticated artist token, createDraft(type),
  // uploadTrack(releaseId) -> trackId. (Grep the sibling *IT.java for their exact names.)

  @Test
  void patchNestedSplits_thenGet_returnsThem() {
    String releaseId = createSingleDraftWithOneTrack();   // helper: draft + one uploaded track
    String trackId = firstTrackId(releaseId);

    given().auth().oauth2(artistToken())
        .contentType("application/json")
        .body("""
          { "tracks": [ { "trackId": "%s", "position": 0, "priceMinor": 250,
              "splits": [ { "name": "Producer", "email": "prod@example.com",
                            "role": "Producer", "percent": 30 } ] } ] }
          """.formatted(trackId))
        .when().patch("/v1/studio/releases/{id}", releaseId)
        .then().statusCode(200)
        .body("tracks[0].splits", hasSize(1))
        .body("tracks[0].splits[0].percent", equalTo(30))
        .body("tracks[0].splits[0].confirmation", equalTo("pending"));

    given().auth().oauth2(artistToken())
        .when().get("/v1/studio/releases/{id}", releaseId)
        .then().statusCode(200)
        .body("tracks[0].splits[0].email", equalTo("prod@example.com"));
  }

  @Test
  void perRowPercentOver100_is422FieldValidation_notSplitOver100() {
    String releaseId = createSingleDraftWithOneTrack();
    String trackId = firstTrackId(releaseId);
    given().auth().oauth2(artistToken()).contentType("application/json")
        .body("""
          { "tracks": [ { "trackId": "%s", "position": 0, "priceMinor": 250,
              "splits": [ { "name": "P", "email": "p@x.com", "role": "P", "percent": 150 } ] } ] }
          """.formatted(trackId))
        .when().patch("/v1/studio/releases/{id}", releaseId)
        .then().statusCode(422)
        .body("error.code", org.hamcrest.Matchers.not(equalTo("SPLIT_OVER_100")));
  }

  @Test
  void finalizeWithSumOver100_is422SplitOver100() {
    String releaseId = createSingleDraftWithOneTrack();
    String trackId = firstTrackId(releaseId);
    given().auth().oauth2(artistToken()).contentType("application/json")
        .body("""
          { "tracks": [ { "trackId": "%s", "position": 0, "priceMinor": 250,
              "splits": [ { "name": "A", "email": "a@x.com", "role": "A", "percent": 60 },
                          { "name": "B", "email": "b@x.com", "role": "B", "percent": 60 } ] } ] }
          """.formatted(trackId))
        .when().patch("/v1/studio/releases/{id}", releaseId).then().statusCode(200);

    given().auth().oauth2(artistToken()).header("Idempotency-Key", "wu-cat6-it-1")
        .when().post("/v1/studio/releases/{id}/submit", releaseId)
        .then().statusCode(422).body("error.code", equalTo("SPLIT_OVER_100"));
  }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `cd backend && ./mvnw -q test -Dtest=StudioReleaseSplitsIT`
Expected: FAIL — response has no `tracks[].splits`; the PATCH body's `splits` is ignored.

- [ ] **Step 3: Add `splits` to `TrackDraftView` + the `SplitView` record** in `TrackDraftView.java`:

```java
public record TrackDraftView(
    String trackId, String title, int duration, String status, int position, MoneyView price,
    List<SplitView> splits) {

  public record SplitView(
      String id, String name, String email, String role, int percent, String confirmation) {}
}
```

Add `import java.util.List;`.

- [ ] **Step 4: Populate splits in `ReleaseViewMapper.toDetailView`.** Replace the `new TrackDraftView(...)` construction with one that attaches the track's splits:

```java
    List<TrackDraftView> trackViews = r.getTracks().stream()
        .sorted(Comparator.comparingInt(ReleaseTrack::position))
        .map(rt -> {
          Track t = byId.get(rt.trackId());
          List<TrackDraftView.SplitView> splitViews = r.getSplits().stream()
              .filter(s -> s.trackId().equals(rt.trackId()))
              .map(s -> new TrackDraftView.SplitView(
                  s.id(), s.name(), s.email(), s.role(), s.percent(), s.confirmation().name()))
              .toList();
          return new TrackDraftView(
              rt.trackId(),
              t != null ? t.getTitle() : "",
              t != null ? t.getDurationSec() : 0,
              t != null ? t.getStatus() : "uploading",
              rt.position(),
              MoneyView.ofMinor(rt.priceMinor()),
              splitViews);
        })
        .toList();
```

- [ ] **Step 5: Accept nested `splits` in the PATCH body** in `StudioReleaseResource.java`.

5a. Extend `TrackRefBody` and add `SplitRefBody` with Bean Validation:

```java
  public record TrackRefBody(
      String trackId, int position, long priceMinor,
      @jakarta.validation.Valid List<SplitRefBody> splits) {}

  public record SplitRefBody(
      String name, String email, String role,
      @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(100) int percent) {}
```

5b. Map the nested splits into the command's `TrackRef`. Change the existing mapping (around line 164):

```java
                .map(t -> new TrackRef(
                    t.trackId(), t.position(), t.priceMinor(),
                    t.splits() == null ? null
                        : t.splits().stream()
                            .map(s -> new UpdateRelease.SplitRef(s.name(), s.email(), s.role(), s.percent()))
                            .toList()))
```

5c. Ensure the PATCH handler validates the body. If the method parameter is not already `@Valid`, annotate it: `public Response patch(..., @jakarta.validation.Valid UpdateReleaseBody body)`. (Confirm whether the class/method already triggers validation; the `@Min/@Max` must produce a 422 via the platform's validation-exception mapper.)

- [ ] **Step 6: Run the REST IT to green**

Run: `cd backend && ./mvnw -q test -Dtest=StudioReleaseSplitsIT`
Expected: PASS (3 tests).

- [ ] **Step 7: Run the catalog module's existing REST + contract tests to catch regressions**

Run: `cd backend && ./mvnw -q test -Dtest='StudioRelease*,*ContractTest'`
Expected: PASS (existing WU-CAT-5 ITs still green with the additive `splits: []` on non-split releases).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/in/TrackDraftView.java \
        backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/service/ReleaseViewMapper.java \
        backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/in/rest/StudioReleaseResource.java \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/adapter/in/rest/StudioReleaseSplitsIT.java
git commit -m "feat(catalog): WU-CAT-6 nested splits in PATCH body + detail response"
```

---

### Task 5: Docs, backlog registration, contract, and verification gate

**Files:**
- Modify: `backend/.project/backlog.yaml`
- Modify: `backend/docs/architecture/catalog.md`
- Modify: `API-CONTRACT.md`

**Interfaces:** none (documentation + registry).

- [ ] **Step 1: Register WU-CAT-6 in the backlog.** Add an entry after WU-CAT-5, following the exact format of the surrounding entries (grep WU-CAT-5's block for the field set — `id, title, phase, owner, depends_on, status, ...`):

```yaml
  - id: WU-CAT-6
    title: Release split persistence + finalize validation
    # copy phase/owner/labels fields from the WU-CAT-5 entry verbatim
    depends_on: [WU-CAT-5]
    status: in_progress
```

- [ ] **Step 2: Confirm no migration is needed.**

Run: `bash backend/scripts/next-migration-version.sh`
Expected: prints the next free version (e.g. `971`). Do **not** create a migration — `split_entry`, its CHECKs, `idx_split_track`, and `SplitEntryEntity` already exist (V305). Record "no migration (V305 covers split_entry)" in the PR body.

- [ ] **Step 3: Update `backend/docs/architecture/catalog.md`.** Flip the two deferred notes to as-built:
  - In `TrackDraftView` §: change "No splits this WU — deferred to WU-CAT-6" to note `splits: List<SplitView>` is now returned.
  - In the "Known gap — split persistence (tracked, deferred to WU-CAT-6)" §: rewrite as as-built — splits persist on the `PATCH` (nested in `tracks[]`, collaborators-only implicit-remainder, `confirmation=pending`), `Σ≤100` validated at finalize (`SPLIT_OVER_100`), read back on `findRelease`; `hasPendingSplits` now has real data and gates go-live; invite/accept flow remains a follow-on WU.

- [ ] **Step 4: Update `API-CONTRACT.md`.** Change the `TrackDraft { ... }` line (currently "(no `splits` yet — deferred to WU-CAT-6)") to include `splits: SplitEntry[]` where `SplitEntry { id, name, email, role, percent, confirmation: self|confirmed|pending|auto }`, noting collaborators-only (creator holds the remainder), and that PATCH accepts `splits` nested under each track (input has no `id`/`confirmation`).

- [ ] **Step 5: Commit docs + backlog**

```bash
git add backend/.project/backlog.yaml backend/docs/architecture/catalog.md API-CONTRACT.md
git commit -m "docs(catalog): WU-CAT-6 as-built (backlog, catalog ADD, API-CONTRACT)"
```

- [ ] **Step 6: Full verification gate (USER RUNS THIS).** Ask the user to run and report:

```
bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh
```

Expected: `Local gate PASSED` + `Smoke PASSED` (Spotless, compile, unit, integration, ArchUnit, coverage, contract, migration-on-empty-DB, `docker compose up` healthy). Loop back to the owning task on any red.

- [ ] **Step 7: Open the PR** (after the gate is green) using the repo's PR template — link WU-CAT-6 + the LLFR (`LLFR-CATALOG-02.3` split extension), the DoD checklist, "no migration (V305 covers `split_entry`)", test evidence, and the catalog ADD box. Base `master`, one WU per branch.

---

## Notes for the executor

- **Cross-task compile safety:** Task 1's `reconstitute` overload keeps existing callers compiling; the port method (Task 2) ships with both its production and fake implementations in the same task. Each task compiles and tests independently.
- **Why `saveTrackSplits` is decoupled from `saveRelease`:** so an unrelated save with an aggregate that didn't load splits (scheduler go-live, upload) can never wipe `split_entry`. Splits are written only by the PATCH path and loaded only on the detail `findRelease`.
- **Implicit remainder means `Σ ≤ 100`, not `== 100`** — a release with a single 30% collaborator (creator implicitly 70%) is valid. Do not add a `== 100` check anywhere in the backend.
- **`SplitConfirmation` enum values are lowercase** (`self, confirmed, pending, auto`); `.name()` yields the exact wire string the frontend expects.
