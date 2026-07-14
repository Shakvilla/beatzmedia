# Catalog Batch Resolve Endpoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `POST /v1/catalog/resolve`, a single endpoint that resolves id-lists (tracks/artists/albums/playlists) to their full views in one request, so frontend list screens (starting with the Library screen) fetch once instead of per-item.

**Architecture:** Standard hexagonal inbound flow, identical in shape to the existing catalog read endpoints — a new input port (`ResolveCatalog`) and application service (`ResolveCatalogService`) that call the **already-existing** batch persistence reads (`CatalogRepository.tracksByIds/artistsByIds/albumsByIds/playlistsByIds`, built for the WU-CAT-2 home feed) and map results through the **already-existing** view records (`TrackView`, `ArtistView`, `AlbumView`, `PlaylistView`) and the **already-existing** `TrackMapper.toView(...)` for per-caller ownership. A new REST method on `PublicCatalogResource` exposes it. No schema, no migration, no persistence-port changes.

**Tech Stack:** Quarkus 3.37.x / Java 25 (backend), JAX-RS (`quarkus-rest`), JUnit 5, REST-assured, Quarkus Dev Services (Testcontainers Postgres) for integration tests.

## Global Constraints

- Endpoint: `POST /v1/catalog/resolve`, `@PermitAll`, `Content-Type: application/json` in and out.
- Request body: `{ trackIds?: string[], artistIds?: string[], albumIds?: string[], playlistIds?: string[] }` — every field nullable; a null/missing list is treated as empty.
- Response body: `{ tracks: TrackView[], artists: ArtistView[], albums: AlbumView[], playlists: PlaylistView[] }` — reusing the existing view records verbatim (no new wire shapes).
- **Lenient:** unknown/removed ids are silently omitted from the result, never a `404`.
- **Private playlists are omitted**, mirroring `GetPlaylistService`'s existing privacy rule (non-public playlists are hidden from every caller, owner-check not yet available — WU-CAT-1 note).
- **Cap: 200 ids per kind** (per list, not summed). Exceeding it → `422` with error code `VALIDATION` and `field` naming the offending list (`trackIds`, `artistIds`, `albumIds`, or `playlistIds`).
- Track ownership decoration is per-caller: extract the optional JWT subject the same way `PublicCatalogResource.callerId()` already does; anonymous callers get intrinsic ownership, exactly like the single-item `GET /tracks/{id}`.
- Follow the hexagonal dependency rule: the new service lives in `catalog/application/service`, the new port in `catalog/application/port/in`, the new REST method in the existing `catalog/adapter/in/rest/PublicCatalogResource.java`. No cross-module imports.
- Error envelope: `{ "error": { "code", "message", "field" } }` via the existing `DomainExceptionMapper` (no new mapper needed — reuse `org.shakvilla.beatzmedia.platform.domain.ValidationException`, which already maps `ErrorCode.VALIDATION` → HTTP 422).

---

### Task 1: Application layer — port, view, service (TDD)

**Files:**
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/in/ResolveCatalog.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/in/ResolvedCatalogView.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/service/ResolveCatalogService.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/application/ResolveCatalogServiceTest.java`

**Interfaces:**
- Consumes (all pre-existing, unchanged):
  - `CatalogRepository.tracksByIds(List<String>) : List<Track>`
  - `CatalogRepository.artistsByIds(List<String>) : List<ArtistProfile>`
  - `CatalogRepository.albumsByIds(List<String>) : List<Album>`
  - `CatalogRepository.playlistsByIds(List<String>) : List<Playlist>`
  - `TrackMapper.toView(Track, Optional<String> callerId, OwnershipReader) : TrackView` (package-private, same package as the new service — `catalog.application.service`)
  - `org.shakvilla.beatzmedia.platform.domain.ValidationException(String message, String field)` — extends `DomainException`, maps to HTTP 422 `VALIDATION`.
- Produces (consumed by Task 2):
  - `ResolveCatalog` interface: `ResolvedCatalogView resolve(ResolveCatalog.Command command, Optional<String> callerId)`, with nested `record Command(List<String> trackIds, List<String> artistIds, List<String> albumIds, List<String> playlistIds)`.
  - `ResolvedCatalogView` record: `record ResolvedCatalogView(List<TrackView> tracks, List<ArtistView> artists, List<AlbumView> albums, List<PlaylistView> playlists)`.
  - `ResolveCatalogService` class implementing `ResolveCatalog`, constructor `ResolveCatalogService(CatalogRepository catalogRepository, OwnershipReader ownershipReader)`.

- [ ] **Step 1: Write the failing unit tests**

Create `backend/src/test/java/org/shakvilla/beatzmedia/catalog/application/ResolveCatalogServiceTest.java`:

```java
package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.in.ResolveCatalog;
import org.shakvilla.beatzmedia.catalog.application.port.in.ResolvedCatalogView;
import org.shakvilla.beatzmedia.catalog.application.service.ResolveCatalogService;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;
import org.shakvilla.beatzmedia.catalog.domain.PlaylistId;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.catalog.fakes.FakeOwnershipReader;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/** Unit test for the batch catalog resolve endpoint. Uses fake ports; no framework. */
@Tag("unit")
class ResolveCatalogServiceTest {

  private FakeCatalogRepository repo;
  private FakeOwnershipReader ownershipReader;
  private ResolveCatalog service;

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    ownershipReader = new FakeOwnershipReader();
    service = new ResolveCatalogService(repo, ownershipReader);
  }

  @Test
  void resolves_each_kind_by_id() {
    repo.addTrack(sampleTrack("t1"));
    repo.addArtist(sampleArtist("a1"));
    repo.addAlbum(sampleAlbum("al1", "a1"));
    repo.addPlaylist(samplePlaylist("p1", true));

    ResolvedCatalogView view = service.resolve(
        new ResolveCatalog.Command(List.of("t1"), List.of("a1"), List.of("al1"), List.of("p1")),
        Optional.empty());

    assertEquals(1, view.tracks().size());
    assertEquals("t1", view.tracks().get(0).id());
    assertEquals(1, view.artists().size());
    assertEquals("a1", view.artists().get(0).id());
    assertEquals(1, view.albums().size());
    assertEquals("al1", view.albums().get(0).id());
    assertEquals(1, view.playlists().size());
    assertEquals("p1", view.playlists().get(0).id());
  }

  @Test
  void omits_unknown_ids_without_error() {
    repo.addTrack(sampleTrack("t1"));

    ResolvedCatalogView view = service.resolve(
        new ResolveCatalog.Command(
            List.of("t1", "bogus-track"), List.of("bogus-artist"), List.of("bogus-album"),
            List.of("bogus-playlist")),
        Optional.empty());

    assertEquals(1, view.tracks().size());
    assertTrue(view.artists().isEmpty());
    assertTrue(view.albums().isEmpty());
    assertTrue(view.playlists().isEmpty());
  }

  @Test
  void null_and_empty_lists_resolve_to_empty_results() {
    ResolvedCatalogView view = service.resolve(
        new ResolveCatalog.Command(null, List.of(), null, List.of()), Optional.empty());

    assertTrue(view.tracks().isEmpty());
    assertTrue(view.artists().isEmpty());
    assertTrue(view.albums().isEmpty());
    assertTrue(view.playlists().isEmpty());
  }

  @Test
  void omits_private_playlists() {
    repo.addPlaylist(samplePlaylist("public-1", true));
    repo.addPlaylist(samplePlaylist("private-1", false));

    ResolvedCatalogView view = service.resolve(
        new ResolveCatalog.Command(null, null, null, List.of("public-1", "private-1")),
        Optional.empty());

    assertEquals(1, view.playlists().size());
    assertEquals("public-1", view.playlists().get(0).id());
  }

  @Test
  void resolved_track_ownership_reflects_caller() {
    repo.addTrack(sampleTrack("owned-track"));
    ownershipReader.set("owned-track", OwnershipStatus.owned, null);

    ResolvedCatalogView view = service.resolve(
        new ResolveCatalog.Command(List.of("owned-track"), null, null, null),
        Optional.of("caller-1"));

    assertEquals("owned", view.tracks().get(0).ownership());
  }

  @Test
  void more_than_200_ids_in_one_list_throws_validation_exception() {
    List<String> tooMany = new ArrayList<>();
    for (int i = 0; i < 201; i++) {
      tooMany.add("t" + i);
    }

    ValidationException ex = assertThrows(ValidationException.class, () -> service.resolve(
        new ResolveCatalog.Command(tooMany, null, null, null), Optional.empty()));
    assertEquals("trackIds", ex.getField());
  }

  private Track sampleTrack(String id) {
    return new Track(
        new TrackId(id), "Title " + id,
        new ArtistId("a1"), "Artist One",
        null, null,
        200, "https://img.test/cover.jpg",
        OwnershipStatus.free, null, 500L, null, null, null, 2023, "ready");
  }

  private ArtistProfile sampleArtist(String id) {
    return new ArtistProfile(
        new ArtistId(id), "Artist " + id, "https://img.test/artist.jpg", null,
        true, 1000L, 500L, "Bio", "Accra", List.of("Afrobeats"), List.of());
  }

  private Album sampleAlbum(String id, String artistId) {
    return new Album(
        new AlbumId(id), "Album " + id, new ArtistId(artistId), "Artist One",
        2024, "https://img.test/album.jpg", List.of("Afrobeats"), List.of("t1"), 0L);
  }

  private Playlist samplePlaylist(String id, boolean isPublic) {
    return new Playlist(
        new PlaylistId(id), "Playlist " + id, "Description", "Creator", null,
        "https://img.test/playlist.jpg", isPublic, 10L, List.of("t1"));
  }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=ResolveCatalogServiceTest -pl . 2>&1 | tail -40`
Expected: compilation failure — `ResolveCatalog`, `ResolvedCatalogView`, and `ResolveCatalogService` do not exist yet.

- [ ] **Step 3: Create `ResolvedCatalogView.java`**

```java
package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

/**
 * Read-model for the batch catalog resolve endpoint. Every list contains only the ids that
 * actually resolved — unknown/removed ids and non-public playlists are silently omitted, never
 * an error. Catalog ADD §6.
 */
public record ResolvedCatalogView(
    List<TrackView> tracks,
    List<ArtistView> artists,
    List<AlbumView> albums,
    List<PlaylistView> playlists) {}
```

- [ ] **Step 4: Create `ResolveCatalog.java`**

```java
package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;
import java.util.Optional;

/**
 * Input port: batch-resolve id-lists across tracks/artists/albums/playlists in one call, so
 * frontend list screens (e.g. the library) fetch once instead of per-item. Catalog ADD §4.1.
 *
 * <p>Lenient: unknown/removed ids and non-public playlists are silently omitted from the result
 * (never a 404). Throws {@link org.shakvilla.beatzmedia.platform.domain.ValidationException}
 * (→ 422 VALIDATION) if any single list exceeds the per-kind id cap.
 */
public interface ResolveCatalog {

  ResolvedCatalogView resolve(Command command, Optional<String> callerId);

  /** Every field is nullable; a null list is treated the same as an empty one. */
  record Command(
      List<String> trackIds,
      List<String> artistIds,
      List<String> albumIds,
      List<String> playlistIds) {}
}
```

- [ ] **Step 5: Create `ResolveCatalogService.java`**

```java
package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.AlbumView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ArtistView;
import org.shakvilla.beatzmedia.catalog.application.port.in.PlaylistView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ResolveCatalog;
import org.shakvilla.beatzmedia.catalog.application.port.in.ResolvedCatalogView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Application service for the batch catalog resolve endpoint. Delegates to the existing
 * {@code …ByIds} batch reads and view mappers — no new persistence access. Catalog ADD §4.1.
 */
@ApplicationScoped
@Transactional
public class ResolveCatalogService implements ResolveCatalog {

  /** Per-kind id cap; a single list larger than this is rejected rather than silently truncated. */
  private static final int MAX_IDS_PER_KIND = 200;

  private final CatalogRepository catalogRepository;
  private final OwnershipReader ownershipReader;

  @Inject
  public ResolveCatalogService(CatalogRepository catalogRepository, OwnershipReader ownershipReader) {
    this.catalogRepository = catalogRepository;
    this.ownershipReader = ownershipReader;
  }

  @Override
  public ResolvedCatalogView resolve(Command command, Optional<String> callerId) {
    List<String> trackIds = orEmpty(command.trackIds());
    List<String> artistIds = orEmpty(command.artistIds());
    List<String> albumIds = orEmpty(command.albumIds());
    List<String> playlistIds = orEmpty(command.playlistIds());

    requireWithinCap(trackIds, "trackIds");
    requireWithinCap(artistIds, "artistIds");
    requireWithinCap(albumIds, "albumIds");
    requireWithinCap(playlistIds, "playlistIds");

    List<TrackView> tracks = catalogRepository.tracksByIds(trackIds).stream()
        .map(t -> TrackMapper.toView(t, callerId, ownershipReader))
        .toList();

    List<ArtistView> artists = catalogRepository.artistsByIds(artistIds).stream()
        .map(this::toArtistView)
        .toList();

    List<AlbumView> albums = catalogRepository.albumsByIds(albumIds).stream()
        .map(this::toAlbumView)
        .toList();

    // LLFR-CATALOG-01.7 / GetPlaylistService parity: non-public playlists are hidden from every
    // caller (owner-check not yet available — WU-CAT-1 note), so they're omitted, not errored.
    List<PlaylistView> playlists = catalogRepository.playlistsByIds(playlistIds).stream()
        .filter(Playlist::isPublic)
        .map(p -> toPlaylistView(p, callerId))
        .toList();

    return new ResolvedCatalogView(tracks, artists, albums, playlists);
  }

  private static List<String> orEmpty(List<String> ids) {
    return ids == null ? List.of() : ids;
  }

  private static void requireWithinCap(List<String> ids, String field) {
    if (ids.size() > MAX_IDS_PER_KIND) {
      throw new ValidationException(
          field + " must not exceed " + MAX_IDS_PER_KIND + " ids", field);
    }
  }

  private ArtistView toArtistView(ArtistProfile a) {
    return new ArtistView(
        a.getId().value(),
        a.getName(),
        a.getImage(),
        a.getCoverImage(),
        a.isVerified(),
        a.getMonthlyListeners(),
        a.getFollowers(),
        a.getBio(),
        a.getLocation(),
        a.getGenres());
  }

  private AlbumView toAlbumView(Album album) {
    return new AlbumView(
        album.getId().value(),
        album.getTitle(),
        album.getArtistId().value(),
        album.getArtistName(),
        album.getYear(),
        album.getCoverImage(),
        album.getGenres(),
        album.getTrackIds(),
        null);
  }

  private PlaylistView toPlaylistView(Playlist p, Optional<String> callerId) {
    List<TrackView> tracks = catalogRepository.tracksByIds(p.getTrackIds()).stream()
        .map(t -> TrackMapper.toView(t, callerId, ownershipReader))
        .toList();
    return new PlaylistView(
        p.getId().value(),
        p.getTitle(),
        p.getDescription(),
        p.getCreator(),
        p.getCreatorAvatar(),
        p.getImage(),
        p.isPublic(),
        p.getFollowers(),
        p.getTrackIds(),
        tracks);
  }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=ResolveCatalogServiceTest -pl . 2>&1 | tail -40`
Expected: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

```bash
cd backend && git add \
  src/main/java/org/shakvilla/beatzmedia/catalog/application/port/in/ResolveCatalog.java \
  src/main/java/org/shakvilla/beatzmedia/catalog/application/port/in/ResolvedCatalogView.java \
  src/main/java/org/shakvilla/beatzmedia/catalog/application/service/ResolveCatalogService.java \
  src/test/java/org/shakvilla/beatzmedia/catalog/application/ResolveCatalogServiceTest.java
git commit -m "feat(catalog): batch resolve application service + port"
```

---

### Task 2: REST wiring

**Files:**
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/in/rest/ResolveCatalogRequest.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/in/rest/PublicCatalogResource.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/it/CatalogResolveIT.java`

**Interfaces:**
- Consumes: `ResolveCatalog`, `ResolveCatalog.Command`, `ResolvedCatalogView` (Task 1) — `ResolveCatalogService` is `@ApplicationScoped`, so it's injectable directly by its port interface `ResolveCatalog` (matches how `PublicCatalogResource` already injects `GetArtist`, `GetAlbum`, etc.).
- Produces: `POST /v1/catalog/resolve` — consumed by the frontend collection/library slice (out of this plan's scope).

- [ ] **Step 1: Write the failing integration test**

Create `backend/src/test/java/org/shakvilla/beatzmedia/catalog/it/CatalogResolveIT.java`. This uses the same Dev-Services Postgres + `R__seed_dev_data.sql` seed as `PublicCatalogResourceIT`/`CatalogContractTest` — known seeded ids: artist `black-sherif`, tracks `last-last` (owned, has credits) and `its-plenty` (for-sale), album `iron-boy`, public playlist `vibes-from-the-233`, and the dev-only private playlist `private-test-playlist` (contains track `last-last`).

```java
package org.shakvilla.beatzmedia.catalog.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for {@code POST /v1/catalog/resolve}. Uses Quarkus Dev Services
 * (Testcontainers Postgres) + REST-assured + seed data from R__seed_dev_data.sql.
 */
@QuarkusTest
@Tag("integration")
class CatalogResolveIT {

  private static final String RESOLVE_URL = "/v1/catalog/resolve";

  @Test
  void resolves_known_ids_across_all_four_kinds() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "trackIds": ["last-last"],
              "artistIds": ["black-sherif"],
              "albumIds": ["iron-boy"],
              "playlistIds": ["vibes-from-the-233"]
            }
            """)
        .when().post(RESOLVE_URL)
        .then()
        .statusCode(200)
        .body("tracks", hasSize(1))
        .body("tracks[0].id", equalTo("last-last"))
        .body("artists", hasSize(1))
        .body("artists[0].id", equalTo("black-sherif"))
        .body("albums", hasSize(1))
        .body("albums[0].id", equalTo("iron-boy"))
        .body("playlists", hasSize(1))
        .body("playlists[0].id", equalTo("vibes-from-the-233"));
  }

  @Test
  void omits_unknown_ids_and_returns_200_not_404() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "trackIds": ["last-last", "bogus-track-xyz"],
              "artistIds": ["bogus-artist-xyz"]
            }
            """)
        .when().post(RESOLVE_URL)
        .then()
        .statusCode(200)
        .body("tracks", hasSize(1))
        .body("tracks[0].id", equalTo("last-last"))
        .body("artists", empty());
  }

  @Test
  void omits_private_playlists() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "playlistIds": ["vibes-from-the-233", "private-test-playlist"] }
            """)
        .when().post(RESOLVE_URL)
        .then()
        .statusCode(200)
        .body("playlists", hasSize(1))
        .body("playlists[0].id", equalTo("vibes-from-the-233"));
  }

  @Test
  void missing_and_null_lists_resolve_to_empty_arrays() {
    given()
        .contentType(ContentType.JSON)
        .body("{}")
        .when().post(RESOLVE_URL)
        .then()
        .statusCode(200)
        .body("tracks", empty())
        .body("artists", empty())
        .body("albums", empty())
        .body("playlists", empty());
  }

  @Test
  void resolved_track_ownership_reflects_caller() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "trackIds": ["its-plenty"] }
            """)
        .when().post(RESOLVE_URL)
        .then()
        .statusCode(200)
        .body("tracks[0].id", equalTo("its-plenty"))
        .body("tracks[0].ownership", equalTo("for-sale"))
        .body("tracks[0].price.currency", equalTo("GHS"));
  }

  @Test
  void over_cap_list_returns_422_validation_with_field() {
    StringBuilder ids = new StringBuilder("[");
    for (int i = 0; i < 201; i++) {
      if (i > 0) ids.append(",");
      ids.append("\"t").append(i).append("\"");
    }
    ids.append("]");

    given()
        .contentType(ContentType.JSON)
        .body("{ \"trackIds\": " + ids + " }")
        .when().post(RESOLVE_URL)
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"))
        .body("error.field", equalTo("trackIds"));
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CatalogResolveIT -pl . 2>&1 | tail -40`
Expected: `404` on every request — the `/v1/catalog/resolve` route does not exist yet (or a compile error if `ResolveCatalogRequest` is referenced before creation; this test does not reference it directly, so expect route-not-found failures).

- [ ] **Step 3: Create `ResolveCatalogRequest.java`**

```java
package org.shakvilla.beatzmedia.catalog.adapter.in.rest;

import java.util.List;

/**
 * Request DTO for POST /v1/catalog/resolve. Every field is nullable — a missing or null list is
 * treated as empty by the application layer. Catalog ADD §5.1 / §6.
 */
public record ResolveCatalogRequest(
    List<String> trackIds,
    List<String> artistIds,
    List<String> albumIds,
    List<String> playlistIds) {}
```

- [ ] **Step 4: Wire the endpoint into `PublicCatalogResource.java`**

Read the current file first (it already has `GetArtist`, `GetAlbum`, `GetTrack`, `GetLyrics`, `GetPlaylist`, `GetHomeFeed`, `Search`, `ListBrowseCategories`, `JsonWebToken` injected via constructor, plus the private `callerId()` helper reused by every method). Add `ResolveCatalog` alongside them:

1. Add these two imports near the top, with the other `jakarta.ws.rs.*` imports:
   ```java
   import jakarta.ws.rs.Consumes;
   import jakarta.ws.rs.POST;
   ```
2. Add this import near the other `catalog.application.port.in.*` imports:
   ```java
   import org.shakvilla.beatzmedia.catalog.application.port.in.ResolveCatalog;
   import org.shakvilla.beatzmedia.catalog.application.port.in.ResolvedCatalogView;
   ```
3. Add a `private final ResolveCatalog resolveCatalog;` field next to the other port fields.
4. Add `ResolveCatalog resolveCatalog` as a constructor parameter (append it to the existing parameter list, before `JsonWebToken jwt`) and assign it in the constructor body, mirroring every other port assignment already there.
5. Add the new endpoint method, placed after the existing `browseCategories()` method and before the private `callerId()` helper:
   ```java
   /** POST /v1/catalog/resolve — batch id-list resolution for list screens (e.g. library). */
   @POST
   @Path("/catalog/resolve")
   @Consumes(MediaType.APPLICATION_JSON)
   public ResolvedCatalogView resolveCatalog(ResolveCatalogRequest request) {
     return resolveCatalog.resolve(
         new ResolveCatalog.Command(
             request.trackIds(), request.artistIds(), request.albumIds(), request.playlistIds()),
         callerId());
   }
   ```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=CatalogResolveIT -pl . 2>&1 | tail -40`
Expected: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 6: Run the full catalog test slice to confirm no regressions**

Run: `cd backend && ./mvnw test -Dtest="org.shakvilla.beatzmedia.catalog.**" -pl . 2>&1 | tail -60`
Expected: all catalog unit + integration tests pass, including the pre-existing `PublicCatalogResourceIT`, `CatalogContractTest`, `CatalogBrowseSearchIT`.

- [ ] **Step 7: Commit**

```bash
cd backend && git add \
  src/main/java/org/shakvilla/beatzmedia/catalog/adapter/in/rest/ResolveCatalogRequest.java \
  src/main/java/org/shakvilla/beatzmedia/catalog/adapter/in/rest/PublicCatalogResource.java \
  src/test/java/org/shakvilla/beatzmedia/catalog/it/CatalogResolveIT.java
git commit -m "feat(catalog): POST /v1/catalog/resolve endpoint"
```

---

### Task 3: Contract test + docs

**Files:**
- Modify: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/it/CatalogContractTest.java`
- Modify: `API-CONTRACT.md`
- Modify: `backend/docs/architecture/catalog.md`

**Interfaces:**
- Consumes: the live `POST /v1/catalog/resolve` endpoint from Task 2 (black-box, via HTTP — no code-level interface).

- [ ] **Step 1: Add a contract test**

Read `backend/src/test/java/org/shakvilla/beatzmedia/catalog/it/CatalogContractTest.java` first. Add this test method after `show_response_shape_matches_contract()` and before the `--- Error envelope contract` comment block:

```java
  // --- Batch resolve contract: { tracks: Track[], artists: Artist[], albums: Album[],
  //                                playlists: Playlist[] } — every array present, ids-not-found
  //                                silently omitted (200, not 404).

  @Test
  void resolve_response_has_all_four_arrays_and_matching_item_shapes() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "trackIds": ["last-last"],
              "artistIds": ["black-sherif"],
              "albumIds": ["iron-boy"],
              "playlistIds": ["vibes-from-the-233"]
            }
            """)
        .when().post("/v1/catalog/resolve")
        .then()
        .statusCode(200)
        .body("tracks[0].id", equalTo("last-last"))
        .body("tracks[0].ownership", isA(String.class))
        .body("artists[0].id", equalTo("black-sherif"))
        .body("artists[0].verified", isA(Boolean.class))
        .body("albums[0].id", equalTo("iron-boy"))
        .body("albums[0].year", isA(Integer.class))
        .body("playlists[0].id", equalTo("vibes-from-the-233"))
        .body("playlists[0].tracks[0].id", isA(String.class));
  }

  @Test
  void resolve_unknown_id_omitted_not_404() {
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "trackIds": ["totally-bogus-id"] }
            """)
        .when().post("/v1/catalog/resolve")
        .then()
        .statusCode(200)
        .body("tracks", org.hamcrest.Matchers.empty());
  }
```

No new imports are needed — `equalTo`, `isA`, and `ContentType` are already statically imported/imported in this file; `org.hamcrest.Matchers.empty` is referenced fully-qualified inline to avoid touching the existing import block.

- [ ] **Step 2: Run the contract test**

Run: `cd backend && ./mvnw test -Dtest=CatalogContractTest -pl . 2>&1 | tail -40`
Expected: all `CatalogContractTest` tests pass, including the two new ones.

- [ ] **Step 3: Update `API-CONTRACT.md`**

Read the file first. In `## 3. Catalog (read-mostly, public)`, add a new subsection after the existing playlist entry (find where `GET /playlists/:id` or the last catalog `GET` route is documented in that section) with:

```markdown
### `POST /catalog/resolve`

Batch-resolves id-lists to full objects in one call — used by list screens (e.g. the library) that
hold only ids and need rich cards. Every request field is optional; a missing/null list is treated
as empty.

Request:
```json
{
  "trackIds": ["last-last"],
  "artistIds": ["black-sherif"],
  "albumIds": ["iron-boy"],
  "playlistIds": ["vibes-from-the-233"]
}
```

Response — `200`, every array always present (possibly empty):
```json
{
  "tracks": [ /* Track[] */ ],
  "artists": [ /* Artist[] */ ],
  "albums": [ /* Album[] */ ],
  "playlists": [ /* Playlist[] */ ]
}
```

- **Lenient:** unknown/removed ids are silently omitted — never a `404`.
- **Private playlists are omitted** from `playlists`, same visibility rule as `GET /playlists/:id`.
- **Cap:** 200 ids per list. Exceeding it → `422` `{ error: { code: "VALIDATION", field: "<listName>" } }`.
- Track `ownership`/`price` reflect the caller (same as `GET /tracks/:id`); anonymous callers get intrinsic ownership.
```

- [ ] **Step 4: Update the catalog module ADD**

Read `backend/docs/architecture/catalog.md` first. Find the section listing the module's REST endpoints (§5.1, per the existing per-file javadoc references like "Catalog ADD §5.1") and add one line for the new endpoint, following the existing format for other entries in that list:

```markdown
- `POST /v1/catalog/resolve` — batch id-list → full-object resolution across tracks/artists/albums/
  playlists in one call, for list screens that hold only ids (e.g. the library). Lenient (unknown
  ids omitted, never 404); private playlists omitted; 200-ids-per-kind cap → 422 VALIDATION.
```

Also find §6 (read-model / DTO listing, per the javadoc references like "Catalog ADD §6" on `ArtistView`/`AlbumView`/etc.) and add:

```markdown
- `ResolvedCatalogView(tracks: TrackView[], artists: ArtistView[], albums: AlbumView[], playlists: PlaylistView[])`
  — response for the batch resolve endpoint; reuses the existing per-kind views verbatim.
```

- [ ] **Step 5: Commit**

```bash
cd "/Users/mac/Desktop/BeatzClik FullStack" && git add \
  backend/src/test/java/org/shakvilla/beatzmedia/catalog/it/CatalogContractTest.java \
  API-CONTRACT.md \
  backend/docs/architecture/catalog.md
git commit -m "docs(catalog): document + contract-test POST /v1/catalog/resolve"
```

---

### Task 4: Full verification + PR

**Files:** none (verification + PR only).

**Interfaces:** none — exercises everything built in Tasks 1–3 together.

- [ ] **Step 1: Tell the user to run the full backend verification gate**

Per project convention, the agent does not run `verify.sh`/`smoke.sh` itself (IntelliJ JPS races the build) — tell the user to run:
```bash
bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh
```
Expected: both green. `verify.sh` covers Spotless, compile, unit, integration (including the new `ResolveCatalogServiceTest`, `CatalogResolveIT`, and the two new `CatalogContractTest` cases), ArchUnit, coverage gate, contract, and migration checks (no migrations changed, so that check is a no-op pass-through). `smoke.sh` covers `docker compose up` + `/q/health/ready`.

- [ ] **Step 2: Open the PR**

```bash
cd "/Users/mac/Desktop/BeatzClik FullStack" && git push -u origin feat/catalog-resolve
gh pr create --base master --head feat/catalog-resolve --title "feat(catalog): POST /v1/catalog/resolve batch endpoint" --body "$(cat <<'EOF'
## Summary
- Add `POST /v1/catalog/resolve`: resolves id-lists (tracks/artists/albums/playlists) to full views
  in one request, for list screens (e.g. the upcoming library slice) that hold only ids.
- Lenient: unknown/removed ids silently omitted (never 404); private playlists omitted (matches
  `GET /playlists/:id`'s existing visibility rule); 200-ids-per-kind cap → 422 VALIDATION.
- No schema/migration change — reuses the existing `CatalogRepository.*ByIds` batch reads (built for
  the WU-CAT-2 home feed) and the existing `TrackView`/`ArtistView`/`AlbumView`/`PlaylistView` shapes.

Design: docs/superpowers/specs/2026-07-14-catalog-batch-resolve-design.md
Plan: docs/superpowers/plans/2026-07-14-catalog-batch-resolve.md

## Test plan
- [x] `ResolveCatalogServiceTest` — 6 unit tests (fake ports)
- [x] `CatalogResolveIT` — 6 integration tests against real Postgres + seed data
- [x] `CatalogContractTest` — 2 new contract tests (shape + lenient-omission)
- [x] Full catalog test slice re-run, no regressions
- [ ] `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh` — reviewer/user gate

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Watch CI and report**

Once CI settles, confirm all required checks are green before requesting merge.
