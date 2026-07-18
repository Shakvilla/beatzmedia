# WU-CAT-8 — Home-Feed Discover Rails Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend `GET /home` with a `rails` object (new releases, popular artists, curated playlists) sourced from ranked catalog queries, and wire the frontend home page to render them from real data — deleting the last mock-data rails.

**Architecture:** Backend, catalog module, strict hexagonal. Three new read-only ranked `CatalogRepository` queries feed a nested `rails` field on the existing `HomeFeedView`, populated by `GetHomeFeedService` and auto-serialized by the existing `/home` resource (it returns the port view directly). Frontend maps the new wire fields with existing mappers and drops `lib/mock-data`. One WU / one branch / one PR.

**Tech Stack:** Java 25, Quarkus 3.36, Hibernate ORM (JPQL/`EntityManager`), PostgreSQL 16 (no migration), JUnit 5 + Testcontainers; React 19 + TanStack Query/Router + TypeScript + Vitest (Node 22.17.1 via nvm).

## Global Constraints

- **Additive only.** `GET /home` gains a nested `rails` object; `trending`/`top10`/`featuredAlbums` and the `/home` route are unchanged. The resource returns `HomeFeedView` directly (JAX-RS serializes the record), so adding a field auto-serializes — no REST DTO/handler change.
- **Rankings (exact):** `newestAlbums` = albums `ORDER BY year DESC`; `popularArtists` = artists `ORDER BY monthlyListeners DESC NULLS LAST`; `curatedPlaylists` = `WHERE isPublic = true ORDER BY followers DESC NULLS LAST`. (`NULLS LAST` because `monthlyListeners`/`followers` are nullable `Long`.)
- **Limits:** `NEW_RELEASES_LIMIT = 10`, `POPULAR_ARTISTS_LIMIT = 10`, `CURATED_PLAYLISTS_LIMIT = 6` — constants beside the existing `TRENDING_LIMIT`/`FEATURED_ALBUMS_LIMIT` in `GetHomeFeedService`.
- **Reuse existing views/mappers.** Rail elements are the existing `AlbumView` / `ArtistView` / `PlaylistView` (backend) and are mapped with the existing `toAlbum` / `toArtist` / `toPlaylist` (frontend). Playlist rail items carry `trackIds` with `tracks = List.of()` (list-summary convention, same as search).
- **No migration** — all three queries are read-only over existing columns (`album.year`, `artist_profile.monthly_listeners`, `playlist.is_public`/`followers`).
- **Both `CatalogRepository` implementors compile:** `JpaCatalogRepository` (production) and `catalog/fakes/FakeCatalogRepository` (test).
- **Hexagonal** — catalog-internal; no cross-module imports; domain untouched.
- **Frontend:** delete `import { artists, playlists, albums } from '../lib/mock-data'` in `routes/index.tsx`; a rail renders only when its list is non-empty. Gate: `npm run build` clean (`tsc -b`, the real typecheck gate), lint 0 new warnings, Node 22.17.1 via nvm.
- **Verification:** `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh` run by **the user**; the implementer runs targeted `./backend/mvnw -Dtest=...` and `npm run build`/`vitest` only. Branch `feat/WU-CAT-8-home-feed-rails` (already checked out). Never touch `application.properties` / `docker-compose.yml`.

---

## File Structure

- `catalog/application/port/out/CatalogRepository.java` — 3 new query signatures. (Task 1)
- `catalog/adapter/out/persistence/JpaCatalogRepository.java` — JPQL impls. (Task 1)
- `catalog/fakes/FakeCatalogRepository.java` (test) — in-memory impls. (Task 1)
- `catalog/application/port/in/HomeFeedView.java` — nested `RailsView` field. (Task 2)
- `catalog/application/service/GetHomeFeedService.java` — populate rails + `toArtistView`/`toPlaylistView` helpers + limits. (Task 2)
- `Frontend/src/lib/api/queries/catalog.ts` — `HomeFeedWire.rails` + `homeQuery` mapping. (Task 3)
- `Frontend/src/routes/index.tsx` — consume `home.rails.*`, delete mock import, hide empty rails. (Task 3)
- Docs/backlog/contract. (Task 4)

---

### Task 1: Backend — three ranked repository queries

**Files:**
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/out/CatalogRepository.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence/JpaCatalogRepository.java`
- Modify: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/fakes/FakeCatalogRepository.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence/HomeRailsQueryIT.java` (create)

**Interfaces:**
- Produces: `List<Album> newestAlbums(int limit)`, `List<ArtistProfile> popularArtists(int limit)`, `List<Playlist> curatedPlaylists(int limit)` on `CatalogRepository`.

- [ ] **Step 1: Write the failing integration test** — create `HomeRailsQueryIT.java`. It runs against the dev-seeded data (the same seed the other catalog ITs use). Assert ordering + limit:

```java
package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;

import io.quarkus.test.junit.QuarkusTest;

/** WU-CAT-8: home-feed rail queries return ranked, limited rows over seeded data. */
@QuarkusTest
@Tag("it")
class HomeRailsQueryIT {

  @Inject CatalogRepository repo;

  @Test
  void newestAlbums_orderedByYearDesc_andLimited() {
    List<Album> albums = repo.newestAlbums(3);
    assertFalse(albums.isEmpty());
    assertTrue(albums.size() <= 3);
    for (int i = 1; i < albums.size(); i++) {
      assertTrue(albums.get(i - 1).getYear() >= albums.get(i).getYear(),
          "albums must be sorted by year DESC");
    }
  }

  @Test
  void popularArtists_orderedByMonthlyListenersDesc_andLimited() {
    List<ArtistProfile> artists = repo.popularArtists(3);
    assertFalse(artists.isEmpty());
    assertTrue(artists.size() <= 3);
    for (int i = 1; i < artists.size(); i++) {
      long prev = artists.get(i - 1).getMonthlyListeners() == null ? 0 : artists.get(i - 1).getMonthlyListeners();
      long cur = artists.get(i).getMonthlyListeners() == null ? 0 : artists.get(i).getMonthlyListeners();
      assertTrue(prev >= cur, "artists must be sorted by monthlyListeners DESC");
    }
  }

  @Test
  void curatedPlaylists_publicOnly_orderedByFollowersDesc_andLimited() {
    List<Playlist> playlists = repo.curatedPlaylists(3);
    assertTrue(playlists.size() <= 3);
    for (Playlist p : playlists) {
      assertTrue(p.isPublic(), "curated playlists must be public");
    }
    for (int i = 1; i < playlists.size(); i++) {
      long prev = playlists.get(i - 1).getFollowers() == null ? 0 : playlists.get(i - 1).getFollowers();
      long cur = playlists.get(i).getFollowers() == null ? 0 : playlists.get(i).getFollowers();
      assertTrue(prev >= cur, "playlists must be sorted by followers DESC");
    }
  }
}
```

> If the seed has no public playlists, `curatedPlaylists` may return empty — the test only asserts `<= 3`, public-only, and ordering, so an empty result still passes. (Confirm the dev seed has public playlists; the other catalog read ITs rely on the same seed.)

- [ ] **Step 2: Run it and watch it fail**

Run: `cd backend && ./mvnw -q test -Dtest=HomeRailsQueryIT`
Expected: COMPILE FAILURE — `repo.newestAlbums/popularArtists/curatedPlaylists` don't exist.

- [ ] **Step 3: Add the port signatures** in `CatalogRepository.java`, next to `featuredAlbums`:

```java
  /** WU-CAT-8 home rail: albums newest-first (ORDER BY year DESC), capped at {@code limit}. */
  List<Album> newestAlbums(int limit);

  /** WU-CAT-8 home rail: artists by monthly listeners DESC (nulls last), capped at {@code limit}. */
  List<ArtistProfile> popularArtists(int limit);

  /** WU-CAT-8 home rail: public playlists by followers DESC (nulls last), capped at {@code limit}. */
  List<Playlist> curatedPlaylists(int limit);
```

(`Album`, `ArtistProfile`, `Playlist` are already imported in this port.)

- [ ] **Step 4: Implement in `JpaCatalogRepository.java`.** Mirror the existing `featuredAlbums`/`artistsByIds`/`playlistsByIds` mapping helpers. Add:

```java
  @Override
  public List<Album> newestAlbums(int limit) {
    List<AlbumEntity> entities =
        em.createQuery("SELECT a FROM AlbumEntity a ORDER BY a.year DESC", AlbumEntity.class)
            .setMaxResults(limit)
            .getResultList();
    return mapAlbumsWithBatchedTrackIds(entities); // same helper featuredAlbums uses
  }

  @Override
  public List<ArtistProfile> popularArtists(int limit) {
    List<ArtistProfileEntity> entities =
        em.createQuery(
                "SELECT a FROM ArtistProfileEntity a ORDER BY a.monthlyListeners DESC NULLS LAST",
                ArtistProfileEntity.class)
            .setMaxResults(limit)
            .getResultList();
    if (entities.isEmpty()) return List.of();
    List<String> ids = entities.stream().map(e -> e.id).toList();
    List<ArtistShowEntity> allShows =
        em.createQuery(
                "SELECT s FROM ArtistShowEntity s WHERE s.artistId IN :ids ORDER BY s.position",
                ArtistShowEntity.class)
            .setParameter("ids", ids)
            .getResultList();
    Map<String, List<ArtistShowEntity>> showsByArtist =
        allShows.stream().collect(Collectors.groupingBy(s -> s.artistId));
    // Preserve query order (ranked); toDomain(e, shows) mirrors artistsByIds.
    return entities.stream()
        .map(e -> toDomain(e, showsByArtist.getOrDefault(e.id, Collections.emptyList())))
        .toList();
  }

  @Override
  public List<Playlist> curatedPlaylists(int limit) {
    List<PlaylistEntity> entities =
        em.createQuery(
                "SELECT p FROM PlaylistEntity p WHERE p.isPublic = true ORDER BY p.followers DESC NULLS LAST",
                PlaylistEntity.class)
            .setMaxResults(limit)
            .getResultList();
    if (entities.isEmpty()) return List.of();
    List<String> ids = entities.stream().map(e -> e.id).toList();
    List<PlaylistTrackEntity> allTracks =
        em.createQuery(
                "SELECT pt FROM PlaylistTrackEntity pt WHERE pt.playlistId IN :ids ORDER BY pt.position",
                PlaylistTrackEntity.class)
            .setParameter("ids", ids)
            .getResultList();
    Map<String, List<String>> trackIdsByPlaylist =
        allTracks.stream().collect(Collectors.groupingBy(
            pt -> pt.playlistId, Collectors.mapping(pt -> pt.trackId, Collectors.toList())));
    return entities.stream()
        .map(e -> new Playlist(
            new PlaylistId(e.id), e.title, e.description, e.creator, e.creatorAvatar, e.image,
            e.isPublic, e.followers,
            trackIdsByPlaylist.getOrDefault(e.id, Collections.emptyList())))
        .toList();
  }
```

> Confirm the helper name `mapAlbumsWithBatchedTrackIds` and the artist `toDomain(e, shows)` signature against the current file (grep them — they're used by `featuredAlbums`/`artistsByIds`). Field names (`e.id`, `e.monthlyListeners`, `e.isPublic`, `e.followers`, `pt.playlistId`, `pt.trackId`) match the existing `playlistsByIds`/`artistsByIds` code. `PlaylistId`, `Collections`, `Collectors`, `Map` are already imported.

- [ ] **Step 5: Implement in the test fake** `FakeCatalogRepository.java` (it already has `Map<String, Album> albums`, `Map<String, ArtistProfile> artists`, `Map<String, Playlist> playlists`). Add:

```java
  @Override
  public List<Album> newestAlbums(int limit) {
    return albums.values().stream()
        .sorted(java.util.Comparator.comparingInt(Album::getYear).reversed())
        .limit(limit)
        .toList();
  }

  @Override
  public List<ArtistProfile> popularArtists(int limit) {
    return artists.values().stream()
        .sorted(java.util.Comparator.comparingLong(
            (ArtistProfile a) -> a.getMonthlyListeners() == null ? 0L : a.getMonthlyListeners()).reversed())
        .limit(limit)
        .toList();
  }

  @Override
  public List<Playlist> curatedPlaylists(int limit) {
    return playlists.values().stream()
        .filter(Playlist::isPublic)
        .sorted(java.util.Comparator.comparingLong(
            (Playlist p) -> p.getFollowers() == null ? 0L : p.getFollowers()).reversed())
        .limit(limit)
        .toList();
  }
```

- [ ] **Step 6: Run the IT + confirm both implementors compile**

Run: `cd backend && ./mvnw -q test -Dtest=HomeRailsQueryIT`
Expected: PASS (3 tests).
Run: `cd backend && ./mvnw -q -o test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/out/CatalogRepository.java \
        backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence/JpaCatalogRepository.java \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/fakes/FakeCatalogRepository.java \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence/HomeRailsQueryIT.java
git commit -m "feat(catalog): WU-CAT-8 ranked home-rail repo queries"
```

---

### Task 2: Backend — `HomeFeedView.rails` + service populates it

**Files:**
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/in/HomeFeedView.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/service/GetHomeFeedService.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/application/GetHomeFeedServiceTest.java` (create or extend)

**Interfaces:**
- Consumes: Task 1's `newestAlbums`/`popularArtists`/`curatedPlaylists`; existing `AlbumView`/`ArtistView`/`PlaylistView`.
- Produces: `HomeFeedView.RailsView(List<AlbumView> newReleases, List<ArtistView> popularArtists, List<PlaylistView> curatedPlaylists)` as a new `rails` field on `HomeFeedView`. The `/home` REST response now carries `rails`.

- [ ] **Step 1: Write the failing service test** — create `GetHomeFeedServiceTest.java` (unit, fake repo). Seed the fake with a couple of albums/artists/public playlists and assert the rails come back mapped + limited:

```java
package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.in.HomeFeedView;
import org.shakvilla.beatzmedia.catalog.application.service.GetHomeFeedService;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.catalog.fakes.FakeOwnershipReader;

@Tag("unit")
class GetHomeFeedServiceTest {

  @Test
  void get_populatesRailsFromRepo() {
    FakeCatalogRepository repo = new FakeCatalogRepository();
    // Seed the fake with at least one public playlist, one artist, one album.
    // Use the fake's existing add helpers (addArtist/addAlbum/…); grep FakeCatalogRepository for
    // the exact seeding methods and the domain factories the sibling tests use.
    seedOneOfEach(repo);

    GetHomeFeedService service = new GetHomeFeedService(repo, new FakeOwnershipReader());
    HomeFeedView view = service.get(Optional.empty());

    assertFalse(view.rails().newReleases().isEmpty(), "newReleases rail populated");
    assertFalse(view.rails().popularArtists().isEmpty(), "popularArtists rail populated");
    assertFalse(view.rails().curatedPlaylists().isEmpty(), "curatedPlaylists rail populated");
    // Views carry through the domain values:
    assertEquals(repo.newestAlbums(10).get(0).getId().value(), view.rails().newReleases().get(0).id());
  }

  // seedOneOfEach: build one Album, one ArtistProfile (monthlyListeners set), one public Playlist
  // via the same domain factories/fake helpers the other catalog application tests use, and add
  // them to `repo`. (Grep sibling tests, e.g. SearchServiceTest / GetHomeFeedService callers.)
}
```

> Reuse the exact fake-seeding helpers and domain factories the sibling catalog application tests use (grep `FakeCatalogRepository` for its `add*`/put methods and a sibling test like `SearchServiceTest` for how it builds `Album`/`ArtistProfile`/`Playlist`). `FakeOwnershipReader` is the existing test double the other `GetHomeFeedService`/track tests use — confirm its name by grepping `implements OwnershipReader` under `src/test`.

- [ ] **Step 2: Run it and watch it fail**

Run: `cd backend && ./mvnw -q test -Dtest=GetHomeFeedServiceTest`
Expected: COMPILE FAILURE — `view.rails()` doesn't exist.

- [ ] **Step 3: Add the `rails` field + `RailsView` record** to `HomeFeedView.java`:

```java
package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

/** Read-model for the home feed. LLFR-CATALOG-01.1, WU-CAT-2 (+ WU-CAT-8 rails). */
public record HomeFeedView(
    List<TrackView> trending,
    List<TrackView> top10,
    List<AlbumView> featuredAlbums,
    RailsView rails) {

  /** WU-CAT-8 discover rails. */
  public record RailsView(
      List<AlbumView> newReleases,
      List<ArtistView> popularArtists,
      List<PlaylistView> curatedPlaylists) {}
}
```

- [ ] **Step 4: Populate rails in `GetHomeFeedService.java`.** Add the three limit constants, the two new mapper helpers (copied verbatim from `SearchService`), and the 4-arg `HomeFeedView`:

Add constants beside the existing ones:
```java
  private static final int NEW_RELEASES_LIMIT = 10;
  private static final int POPULAR_ARTISTS_LIMIT = 10;
  private static final int CURATED_PLAYLISTS_LIMIT = 6;
```

Change the end of `get(...)` from `return new HomeFeedView(trending, top10, featuredAlbums);` to:
```java
    HomeFeedView.RailsView rails = new HomeFeedView.RailsView(
        catalogRepository.newestAlbums(NEW_RELEASES_LIMIT).stream().map(this::toAlbumView).toList(),
        catalogRepository.popularArtists(POPULAR_ARTISTS_LIMIT).stream().map(this::toArtistView).toList(),
        catalogRepository.curatedPlaylists(CURATED_PLAYLISTS_LIMIT).stream().map(this::toPlaylistView).toList());

    return new HomeFeedView(trending, top10, featuredAlbums, rails);
```

Add the two helpers (verbatim from `SearchService`; `toAlbumView` already exists in this class). Add imports `ArtistView`, `PlaylistView`, and domain `ArtistProfile`, `Playlist`:
```java
  private ArtistView toArtistView(ArtistProfile a) {
    return new ArtistView(
        a.getId().value(), a.getName(), a.getImage(), a.getCoverImage(), a.isVerified(),
        a.getMonthlyListeners(), a.getFollowers(), a.getBio(), a.getLocation(), a.getGenres());
  }

  private PlaylistView toPlaylistView(Playlist p) {
    // List-summary: carry trackIds; embed tracks only in the detail endpoint.
    return new PlaylistView(
        p.getId().value(), p.getTitle(), p.getDescription(), p.getCreator(), p.getCreatorAvatar(),
        p.getImage(), p.isPublic(), p.getFollowers(), p.getTrackIds(), List.of());
  }
```

- [ ] **Step 5: Run the service test to green**

Run: `cd backend && ./mvnw -q test -Dtest=GetHomeFeedServiceTest`
Expected: PASS.

- [ ] **Step 6: Add a `/home` REST assertion** proving rails serialize end-to-end. Find the existing home-feed REST/contract test (`grep -rl "getHomeFeed\|\"/home\"\|/v1/home\|HomeFeed" backend/src/test`) and add a case; if none exists, create `backend/src/test/java/org/shakvilla/beatzmedia/catalog/adapter/in/rest/HomeFeedRailsIT.java`:

```java
package org.shakvilla.beatzmedia.catalog.adapter.in.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** WU-CAT-8: GET /home serializes the rails object additively. */
@QuarkusTest
@Tag("it")
class HomeFeedRailsIT {
  @Test
  void home_includesRails_andKeepsExistingFields() {
    given()
        .when().get("/v1/home")
        .then().statusCode(200)
        .body("trending", notNullValue())          // existing field unchanged
        .body("featuredAlbums", notNullValue())     // existing field unchanged
        .body("rails.newReleases", notNullValue())
        .body("rails.popularArtists", notNullValue())
        .body("rails.curatedPlaylists", notNullValue());
  }
}
```

Run: `cd backend && ./mvnw -q test -Dtest=HomeFeedRailsIT`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/in/HomeFeedView.java \
        backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/service/GetHomeFeedService.java \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/application/GetHomeFeedServiceTest.java \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/adapter/in/rest/HomeFeedRailsIT.java
git commit -m "feat(catalog): WU-CAT-8 home feed serves discover rails"
```

---

### Task 3: Frontend — map and render the rails

**Files:**
- Modify: `Frontend/src/lib/api/queries/catalog.ts`
- Modify: `Frontend/src/routes/index.tsx`
- Test: `Frontend/src/lib/api/queries/catalog.test.ts` (extend)

**Interfaces:**
- Consumes: the `/home` response's `rails` (Task 2); existing `toAlbum`/`toArtist`/`toPlaylist` mappers + `AlbumWire`/`ArtistWire`/`PlaylistWire`.
- Produces: `homeQuery`'s data gains `rails: { newReleases: Album[]; popularArtists: Artist[]; curatedPlaylists: Playlist[] }`.

- [ ] **Step 1: Write the failing mapping test** — add to `catalog.test.ts` (match the file's existing `apiFetch`-mock style; grep it for how it mocks the client and asserts `homeQuery`). Add:

```ts
it('homeQuery maps the discover rails', async () => {
  const albumWire = { id: 'al1', title: 'A', artistId: 'ar1', artistName: 'Art', year: 2026, coverImage: '/c', genres: [], trackIds: [], tracks: null }
  const artistWire = { id: 'ar1', name: 'Art', image: '/i', coverImage: '/cc', verified: true, monthlyListeners: 5, followers: 3, bio: '', location: '', genres: [] }
  const playlistWire = { id: 'pl1', title: 'Mix', description: '', creator: 'BeatzClik', creatorAvatar: '/a', image: '/p', isPublic: true, followers: 9, trackIds: ['t1'], tracks: [] }
  mockApiFetch({
    trending: [], top10: [], featuredAlbums: [],
    rails: { newReleases: [albumWire], popularArtists: [artistWire], curatedPlaylists: [playlistWire] },
  })
  const data = await homeQuery().queryFn!({} as any)
  expect(data.rails.newReleases[0].id).toBe('al1')
  expect(data.rails.popularArtists[0].id).toBe('ar1')
  expect(data.rails.curatedPlaylists[0].id).toBe('pl1')
})
```

> Adapt `mockApiFetch(...)` / the `homeQuery().queryFn` invocation to the exact pattern this test file already uses for the existing `homeQuery` test (it already mocks `/home`). The three wire objects must include every field the existing `AlbumWire`/`ArtistWire`/`PlaylistWire` and `toAlbum`/`toArtist`/`toPlaylist` read — copy the shapes from an existing test in the same file if it already builds them.

- [ ] **Step 2: Run it and watch it fail**

Run: `export NVM_DIR="$HOME/.nvm" && source "$NVM_DIR/nvm.sh" && nvm use 22.17.1 && cd Frontend && npx vitest run src/lib/api/queries/catalog.test.ts`
Expected: FAIL — `data.rails` is undefined.

- [ ] **Step 3: Extend `HomeFeedWire` + `homeQuery` mapping** in `catalog.ts`. Add `rails` to the wire interface:

```ts
interface HomeFeedWire {
  trending: TrackWire[]
  top10: TrackWire[]
  featuredAlbums: AlbumWire[]
  rails: {
    newReleases: AlbumWire[]
    popularArtists: ArtistWire[]
    curatedPlaylists: PlaylistWire[]
  }
}
```

In `homeQuery`'s mapper (where it returns `{ trending: ..., top10: ..., featuredAlbums: ... }`), add:

```ts
      return {
        trending: wire.trending.map(toTrack),
        top10: wire.top10.map(toTrack),
        featuredAlbums: wire.featuredAlbums.map(toAlbum),
        rails: {
          newReleases: wire.rails.newReleases.map(toAlbum),
          popularArtists: wire.rails.popularArtists.map(toArtist),
          curatedPlaylists: wire.rails.curatedPlaylists.map(toPlaylist),
        },
      }
```

Ensure `toArtist` and `toPlaylist` are imported at the top of `catalog.ts` (alongside `toTrack`/`toAlbum`) — add them if missing.

- [ ] **Step 4: Run the mapping test to green**

Run: `cd Frontend && npx vitest run src/lib/api/queries/catalog.test.ts`
Expected: PASS.

- [ ] **Step 5: Consume the rails in `routes/index.tsx`** and drop the mock import.

5a. Delete the mock import line:
```ts
import { artists, playlists, albums } from '../lib/mock-data'
```

5b. Replace the mock-derived locals with feed data. After `const home = useSuspenseQuery(homeQuery()).data` (or the existing destructure), define:
```ts
const newReleases = home.rails.newReleases
const popularArtists = home.rails.popularArtists
const curatedPlaylists = home.rails.curatedPlaylists
const quickPickPlaylists = curatedPlaylists.slice(0, 3)
```
Then swap the three rails' data sources: the "Made for you" rail maps `curatedPlaylists` (was `playlists`), the "New releases" rail maps `newReleases` (was `albums`), the "Popular artists" rail maps `popularArtists` (was `artists`), and the quick-pick block maps `quickPickPlaylists` (unchanged variable, now real).

5c. **Hide empty rails.** Wrap each of the three rails so it renders only when its list is non-empty, e.g.:
```tsx
{curatedPlaylists.length > 0 && (
  <MediaRail title="Made for you" subtitle="Mixes and playlists picked for your taste">
    {curatedPlaylists.map((playlist) => ( /* …existing card… */ ))}
  </MediaRail>
)}
```
Apply the same `list.length > 0 && (…)` guard to the New-releases rail, the Popular-artists rail, and the quick-pick block. Leave the already-wired rails (trending, top10, featured) exactly as they are.

- [ ] **Step 6: Typecheck + lint + full test run**

Run: `cd Frontend && npm run build && npm run lint && npx vitest run`
Expected: build clean (no reference to the deleted `mock-data` names remains); lint 0 new warnings; vitest all green.

- [ ] **Step 7: Commit**

```bash
git add Frontend/src/lib/api/queries/catalog.ts Frontend/src/routes/index.tsx \
        Frontend/src/lib/api/queries/catalog.test.ts
git commit -m "feat(home): WU-CAT-8 wire discover rails, drop mock-data rails"
```

---

### Task 4: Docs, backlog, contract, verification

**Files:**
- Modify: `backend/.project/backlog.yaml`
- Modify: `backend/docs/architecture/catalog.md`
- Modify: `API-CONTRACT.md`

**Interfaces:** none (documentation + registry).

- [ ] **Step 1: Register WU-CAT-8 in the backlog.** Add after WU-CAT-7 (or the last WU-CAT entry), copying the field set of the WU-CAT-5/WU-CAT-6 entries verbatim:

```yaml
  - id: WU-CAT-8
    title: Home-feed discover rails
    phase: 1
    module: catalog
    add: catalog.md
    owner: backend-engineer
    depends_on: [WU-CAT-2]
    llfrs: [LLFR-CATALOG-01.1]
    status: in_progress
```

(Confirm the next-free id — grep `id: WU-CAT-` — in case another branch registered WU-CAT-8 first; if so use the next number and rename the branch note accordingly.)

- [ ] **Step 2: Update `API-CONTRACT.md`.** Change the `GET /home` row so the `rails` object is concrete:

`GET /home → { trending: Track[], top10: Track[], featuredAlbums: Album[], rails: { newReleases: Album[], popularArtists: Artist[], curatedPlaylists: Playlist[] } }` — and a one-line note: newReleases = albums newest-first, popularArtists = by monthly listeners, curatedPlaylists = public playlists by followers (each carries `trackIds`, `tracks` empty).

- [ ] **Step 3: Update `backend/docs/architecture/catalog.md`.** In the home-feed section, note the `rails` addition (WU-CAT-8): three ranked read queries (`newestAlbums`/`popularArtists`/`curatedPlaylists`) feeding `HomeFeedView.RailsView`, limits 10/10/6, no migration.

- [ ] **Step 4: Commit docs + backlog**

```bash
git add backend/.project/backlog.yaml backend/docs/architecture/catalog.md API-CONTRACT.md
git commit -m "docs(catalog): WU-CAT-8 as-built (backlog, catalog ADD, API-CONTRACT)"
```

- [ ] **Step 5: Full verification gate (USER RUNS).** Ask the user to run and report:

```
bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh
```
Expected: `Local gate PASSED` + `Smoke PASSED`. Loop back on any red.

- [ ] **Step 6: Open the PR** (after the gate is green) to `master` using the PR template — link WU-CAT-8 + LLFR-CATALOG-01.1, the DoD checklist, "no migration", test evidence (`HomeRailsQueryIT`, `GetHomeFeedServiceTest`, `HomeFeedRailsIT`, frontend `catalog.test.ts`), and the catalog ADD box. One WU per branch.

---

## Notes for the executor

- **Cross-task compile safety:** Task 1 adds the port method with BOTH implementors in the same task; Task 2 changes `HomeFeedView` (4-arg) and its only constructor (`GetHomeFeedService`) together; the `/home` resource returns `HomeFeedView` directly, so no REST change is needed. Each task compiles independently.
- **`NULLS LAST`** is required on the nullable ranking columns (`monthlyListeners`, `followers`) so null-valued rows don't sort to the top under `DESC`.
- **Playlist rail** carries `trackIds` only (`tracks: List.of()`), matching the search/list convention — the home card only needs `title`/`image`/`creator`/`trackIds.length`.
- **No visual change when data is present** — the only intended behavior addition is that an empty rail is hidden.
