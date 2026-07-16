# WU-SRCH-2 — Search Index Backfill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `GET /v1/search?q=` actually return results by giving the search module a real reindex
that reads catalog entities and populates `search_document`, which is currently permanently empty.

**Architecture:** The search module declares a new outbound port `IndexSource` (an SPI). Each owning
module contributes implementations that map *its own* domain entities to `IndexDocument`s. `ReindexService`
— today a stub that only counts already-indexed rows — is rewritten to discover all `IndexSource` beans
via CDI, load their documents, and upsert them through the existing `SearchIndex` port. A `ReindexJob`
implementing `ScheduledJob` wires the already-declared-but-dormant `search.reindex` tick.

**Tech Stack:** Java 25, Quarkus 3.37.x, Maven, PostgreSQL 16, JUnit 5 + Testcontainers (via Quarkus
Dev Services), ArchUnit.

## Why this design (context for every task)

The obvious approach — `ReindexService` reads catalog through catalog's input ports — is what
`backend/docs/architecture/search.md` §9 originally sketched, and what `ReindexService`'s own TODO
comment anticipates ("once the catalog read ports are available"). **We are deliberately not doing
that**, for two reasons:

1. **It would create a module dependency cycle.** `catalog.application.service.SearchService` already
   injects `search.application.port.in.QueryService`. Adding `search → catalog` on top of the existing
   `catalog → search` makes the two modules mutually dependent.
2. **Catalog has no port that can enumerate everything.** Every `catalog/application/port/in/*` either
   fetches by a single id, batch-resolves a caller-supplied id list, or is owner-scoped/capped. A
   backfill cannot legally "list all tracks" through them today.

Instead we follow the direction the codebase already establishes: `store/adapter/out/persistence/SearchIndexPg.java`
is an outbound adapter of the **store** module that injects **search**'s `IndexEntityUseCase` and maps
store's own domain type to an `IndexDocument`. `module → search` is the established edge. `IndexSource`
generalizes exactly that, and adds no new module edges.

This is a structural decision, so Task 6 records it as an ADR.

## Global Constraints

- **Hexagonal dependency rule:** `adapter → application → domain`. Domain imports no framework (no
  Jakarta/Quarkus/Hibernate on domain types). Inbound adapters must not import outbound adapters, and
  vice versa. Enforced by `src/test/java/org/shakvilla/beatzmedia/archunit/ArchitectureRulesTest.java`.
- **No wall-clock or randomness in `..domain..`/`..application..`.** ArchUnit rule `no_wallclock_in_core`
  fails the build on `Instant.now()` or `UUID.randomUUID()` there. Inject
  `org.shakvilla.beatzmedia.platform.application.port.out.Clock` (`now(): Instant`, `today(ZoneId): LocalDate`).
- **A module must never read another module's tables.** Cross-module access goes through the other
  module's `port.in` (or its published domain types), never its `adapter.*` or `port.out`.
- **`search_document` is written only through the search module.** `PostgresFtsSearchAdapter`,
  `SearchDocumentEntity`, and `SearchDocumentMapper` are package-private and must stay unreferenced
  outside `search`.
- Java 25 / Quarkus. Build with `./backend/mvnw`. Spotless must be clean.
- **Assertions: JUnit 5 `org.junit.jupiter.api.Assertions` only. AssertJ is NOT a dependency of this
  project** — there is no `assertj` in `backend/pom.xml` and zero existing tests import `org.assertj`.
  Do not add it. Match the style of the existing `search/integration/SearchIndexIT.java`.
- **Running tests — this project has two separate silent-false-green traps. Both report BUILD SUCCESS
  while running nothing.** Use exactly these invocations:
  - unit tests / ArchUnit (surefire, `*Test`): `cd backend && ./mvnw -q test -Dtest=<ClassName>`
  - all unit tests: `cd backend && ./mvnw -q test -DskipITs=true`
  - integration tests (failsafe, `*IT`):
    `cd backend && ./mvnw -q verify -DskipITs=false -DskipUnitTests=true -Dit.test=<ClassName>`

  Trap 1: `-Dgroups=...` runs **zero** tests — this pom has no surefire groups wiring. Never use it.
  ArchUnit has no separate step; `ArchitectureRulesTest` runs inside the normal test suite.

  Trap 2: **`<skipITs>true</skipITs>` is the pom default** (`pom.xml:17`). Plain `./mvnw verify` — with
  or without `-Dit.test=` — prints "Tests are skipped." and exits green **without running any IT**. The
  `-DskipITs=false` is mandatory; `verify.sh:127` uses exactly `-DskipITs=false -DskipUnitTests=true`.

  **After any IT run, confirm it actually ran**: `ls backend/target/failsafe-reports/*.xml` and read the
  report for your class. A green build is not evidence that a test executed.
- **Do NOT run `backend/scripts/verify.sh` or `smoke.sh` yourself** — the repo owner runs those and
  reports results (IntelliJ JPS races the build). Task 7 is that gate.
- Branch: `feat/WU-SRCH-2-search-index-backfill`, one WU per branch. Conventional Commits with the WU id
  in scope, e.g. `feat(search): WU-SRCH-2 reindex backfill`.
- **No migration is needed.** `search_document` already exists (`V801`–`V803`). Do not create one.

## Load-bearing facts discovered during design (do not re-derive; do not contradict)

- **`search_document` is empty and nothing can fill it.** `R__seed_dev_data.sql` has zero references to
  it; `IndexEventObserversStub.java` is a comment-only placeholder with no `@Observes` beans;
  `ReindexService.reindex()` only sums `IndexDocumentRepository.count(type)` over already-indexed rows;
  the `search.reindex` scheduled tick has no bean and silently no-ops.
- **The seed has NO `release` rows at all**, and every seeded track has `release_id = NULL`. A rule
  requiring `release.status = 'live'` would index nothing and leave search broken.
- **`track.status`** is an upload-pipeline status (`uploading|ready|error`, DB CHECK in `V302`), **not**
  a publish flag. All seeded tracks are `'ready'`.
- **The catalog repository applies no visibility filter.** `findTrack`, `tracksByIds`, `findAlbum`,
  `findPlaylist`, `playlistsByIds` have no WHERE clause on status/visibility — the private seeded
  playlist is returned by them. So the index's `visible` flag is the **only** gate on what search exposes.
- **`PostgresFtsSearchAdapter` filters `WHERE visible = true`** on both query paths, and its upsert sets
  `visible = EXCLUDED.visible`. **Therefore: index every entity and set `visible` correctly — never skip
  rows.** Skipping is unsafe: reindex is upsert-only, so a row that was public and becomes private would
  keep its stale `visible=true` document forever. Upserting with `visible=false` converges.
- **`Popularity(long score)` rejects negative scores**, and the source fields (`Track.getPlays()`,
  `ArtistProfile.getMonthlyListeners()`, `Playlist.getFollowers()`) are nullable `Long`. Null-coalesce to
  `0L` before constructing, or you get an unboxing NPE.
- **`Album` has no popularity-like field at all** — use `Popularity.ZERO`.
- **`SearchDocumentMapper.ALLOWED_PAYLOAD_KEYS` silently strips unlisted keys**, per `EntityType`:
  - `TRACK`: `image, duration_sec, price_minor, price_amount, price_currency, quality, type, genre`
  - `ARTIST`: `image, genre`
  - `ALBUM`: `image, price_minor, price_amount, price_currency, genre`
  - `PLAYLIST`: `image`
- **The frontend does not read `payload`.** `catalog.application.service.SearchService` hydrates hits by
  id from `CatalogRepository`, and the frontend's `resolveTopResult` looks up ids in those hydrated
  arrays. Payload only needs to be allow-list-clean; it is not load-bearing for rendering.
- `IndexDocument` full constructor: `(EntityType entityType, String entityId, String title, String subtitle,
  String searchText, Popularity popularity, boolean visible, Map<String,Object> payload)`. The
  convenience `IndexDocument.of(...)` omits `subtitle` (sets it null) — we want subtitles, so use the
  full constructor.
- `FakeSearchIndex` (`src/test/java/org/shakvilla/beatzmedia/search/unit/FakeSearchIndex.java`) is
  **package-private** in package `...search.unit` — a test can only reuse it from that same package.
- Search ITs are plain `@QuarkusTest @Tag("integration")`; the DB comes from Quarkus Dev Services (no
  `%test` datasource URL is configured). There is no shared IT base class. `SearchIndexIT` cleans up with
  `@BeforeEach @Transactional` running `DELETE FROM search_document`.

---

### Task 1: Register WU-SRCH-2 in the backlog

**Files:**
- Modify: `backend/.project/backlog.yaml`

- [ ] **Step 1: Read the existing WU-SRCH-1 entry and its neighbours**

Run: `grep -n -B2 -A12 "WU-SRCH-1" backend/.project/backlog.yaml`

Note the exact key order and indentation used by entries in this file (`id, title, phase, module, add,
owner, depends_on, llfrs, status`). Match it exactly.

- [ ] **Step 2: Add the WU-SRCH-2 entry directly after WU-SRCH-1**

```yaml
  - id: WU-SRCH-2
    title: Search index backfill (IndexSource SPI + real reindex + scheduled job)
    phase: 1
    module: search
    add: search.md
    owner: backend-engineer
    depends_on: [WU-SRCH-1, WU-CAT-2]
    llfrs: [LLFR-SEARCH-01.1]
    status: in_progress
```

Match the surrounding entries' indentation exactly — copy the leading whitespace from the WU-SRCH-1
entry rather than assuming two spaces.

- [ ] **Step 3: Verify the file still parses as YAML**

Run: `python3 -c "import yaml,sys; d=yaml.safe_load(open('backend/.project/backlog.yaml')); print('OK, work_units:', len(d.get('work_units', d if isinstance(d,list) else [])))"`
Expected: prints OK and a count, no traceback. If `yaml` is unavailable, run
`python3 -c "import json,sys; print(open('backend/.project/backlog.yaml').read().count('- id:'))"` and
confirm the count went up by exactly one versus `git stash`-ing your change.

- [ ] **Step 4: Commit**

```bash
git add backend/.project/backlog.yaml
git commit -m "chore(search): WU-SRCH-2 register search index backfill work unit"
```

---

### Task 2: `IndexSource` port + real `ReindexService`

**Files:**
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/search/application/port/out/IndexSource.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/search/application/service/ReindexService.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/search/unit/ReindexServiceTest.java`

**Interfaces:**
- Consumes: `SearchIndex` (`search/application/port/out/SearchIndex.java` — `upsert(IndexDocument)`),
  `Clock` (`platform/application/port/out/Clock.java` — `now(): Instant`), `EntityType`, `IndexDocument`,
  `ReindexReport(EntityType type, long documentsIndexed, long documentsRemoved, Instant startedAt, Instant completedAt)`.
- Produces: `IndexSource` — implemented by catalog in Task 4. Exact contract below; Task 4 depends on it
  verbatim.

- [ ] **Step 1: Write the `IndexSource` port**

```java
package org.shakvilla.beatzmedia.search.application.port.out;

import java.util.List;

import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;

/**
 * SPI supplying source entities for a reindex. Each owning module (catalog, store, podcasts, …)
 * contributes one implementation per {@link EntityType} it owns, mapping its own domain objects to
 * {@link IndexDocument}s. The search module never reads another module's tables or ports; sources are
 * discovered via CDI, which keeps the dependency edge pointing {@code module -> search} — the same
 * direction {@code store.adapter.out.persistence.SearchIndexPg} already establishes. Search ADD §9.
 *
 * <p>Implementations must return every entity they own, including ones that are not publicly
 * visible, with {@link IndexDocument#visible()} set accordingly. Reindex is upsert-only, so omitting
 * a now-hidden entity would strand its previous {@code visible=true} document in the index forever.
 */
public interface IndexSource {

  /** The entity type this source supplies. */
  EntityType entityType();

  /** All documents of {@link #entityType()} currently in the owning module. */
  List<IndexDocument> load();
}
```

- [ ] **Step 2: Write the failing test**

> **As-built correction (Task 2 is complete):** the test code below uses AssertJ, which is **not** a
> dependency of this project — see the Global Constraints. The implemented version at commit `b62a552`
> uses JUnit 5 `Assertions` instead, with the same coverage. Read the committed
> `search/unit/ReindexServiceTest.java` for the real tests; the block below is kept only to show the
> intended cases.

Note the package: `org.shakvilla.beatzmedia.search.unit`, so the package-private `FakeSearchIndex` can be
reused. `ReindexService` is package-private in `...search.application.service`, so the test constructs it
via the existing helper pattern — add a public helper next to it (Step 3) rather than making the service
public.

```java
package org.shakvilla.beatzmedia.search.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.search.application.port.out.IndexSource;
import org.shakvilla.beatzmedia.search.application.service.ReindexServiceTestHelper;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;
import org.shakvilla.beatzmedia.search.domain.Popularity;
import org.shakvilla.beatzmedia.search.domain.ReindexReport;

class ReindexServiceTest {

  private static IndexDocument doc(EntityType type, String id, boolean visible) {
    return new IndexDocument(type, id, "Title " + id, "Subtitle", "Title " + id, new Popularity(1L), visible, Map.of());
  }

  private static IndexSource source(EntityType type, List<IndexDocument> docs) {
    return new IndexSource() {
      @Override
      public EntityType entityType() {
        return type;
      }

      @Override
      public List<IndexDocument> load() {
        return docs;
      }
    };
  }

  @Test
  void reindex_all_upserts_every_document_from_every_source() {
    FakeSearchIndex index = new FakeSearchIndex();
    var tracks = source(EntityType.TRACK, List.of(doc(EntityType.TRACK, "t1", true), doc(EntityType.TRACK, "t2", true)));
    var artists = source(EntityType.ARTIST, List.of(doc(EntityType.ARTIST, "a1", true)));
    var service = ReindexServiceTestHelper.create(index, FakeClock.fixed(), List.of(tracks, artists));

    ReindexReport report = service.reindex(null);

    assertThat(index.upsertCallCount).isEqualTo(3);
    assertThat(report.documentsIndexed()).isEqualTo(3L);
    assertThat(report.documentsRemoved()).isZero();
    assertThat(report.type()).isNull();
  }

  @Test
  void reindex_of_one_type_only_touches_that_types_source() {
    FakeSearchIndex index = new FakeSearchIndex();
    var tracks = source(EntityType.TRACK, List.of(doc(EntityType.TRACK, "t1", true)));
    var artists = source(EntityType.ARTIST, List.of(doc(EntityType.ARTIST, "a1", true)));
    var service = ReindexServiceTestHelper.create(index, FakeClock.fixed(), List.of(tracks, artists));

    ReindexReport report = service.reindex(EntityType.ARTIST);

    assertThat(report.documentsIndexed()).isEqualTo(1L);
    assertThat(index.upsertCallCount).isEqualTo(1);
    assertThat(report.type()).isEqualTo(EntityType.ARTIST);
  }

  @Test
  void reindex_upserts_non_visible_documents_too_so_hidden_entities_converge() {
    FakeSearchIndex index = new FakeSearchIndex();
    var playlists =
        source(EntityType.PLAYLIST, List.of(doc(EntityType.PLAYLIST, "public-1", true), doc(EntityType.PLAYLIST, "private-1", false)));
    var service = ReindexServiceTestHelper.create(index, FakeClock.fixed(), List.of(playlists));

    ReindexReport report = service.reindex(EntityType.PLAYLIST);

    // Both are written; visible=false is what hides the private one, not omission from the index.
    assertThat(report.documentsIndexed()).isEqualTo(2L);
    assertThat(index.upsertCallCount).isEqualTo(2);
  }

  @Test
  void reindex_with_no_source_for_a_type_reports_zero_rather_than_failing() {
    FakeSearchIndex index = new FakeSearchIndex();
    var service = ReindexServiceTestHelper.create(index, FakeClock.fixed(), List.of());

    ReindexReport report = service.reindex(EntityType.EVENT);

    assertThat(report.documentsIndexed()).isZero();
    assertThat(index.upsertCallCount).isZero();
  }

  @Test
  void reindex_stamps_started_and_completed_from_the_clock() {
    FakeSearchIndex index = new FakeSearchIndex();
    var clock = FakeClock.fixed();
    var service = ReindexServiceTestHelper.create(index, clock, List.of());

    ReindexReport report = service.reindex(null);

    assertThat(report.startedAt()).isEqualTo(clock.now());
    assertThat(report.completedAt()).isEqualTo(clock.now());
  }
}
```

- [ ] **Step 2b: Write the test helper**

`ReindexService` is package-private, so tests outside its package need a public factory — this mirrors
the existing `IndexingServiceTestHelper` next to it.

```java
package org.shakvilla.beatzmedia.search.application.service;

import java.util.List;

import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.search.application.port.in.ReindexUseCase;
import org.shakvilla.beatzmedia.search.application.port.out.IndexSource;
import org.shakvilla.beatzmedia.search.application.port.out.SearchIndex;

/** Exposes the package-private {@link ReindexService} to tests without CDI. */
public final class ReindexServiceTestHelper {

  private ReindexServiceTestHelper() {}

  public static ReindexUseCase create(SearchIndex searchIndex, Clock clock, List<IndexSource> sources) {
    return new ReindexService(searchIndex, clock, sources);
  }
}
```

Put this at `backend/src/test/java/org/shakvilla/beatzmedia/search/application/service/ReindexServiceTestHelper.java`.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd backend && ./mvnw -q test -Dtest=ReindexServiceTest`
Expected: FAIL — compilation error, `IndexSource` / `ReindexServiceTestHelper` not found, or
`ReindexService` constructor mismatch.

- [ ] **Step 4: Rewrite `ReindexService`**

The CDI bean takes `Instance<IndexSource>`; a package-private constructor takes a plain `List` so tests
can inject fakes without a CDI container.

```java
package org.shakvilla.beatzmedia.search.application.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.search.application.port.in.ReindexUseCase;
import org.shakvilla.beatzmedia.search.application.port.out.IndexSource;
import org.shakvilla.beatzmedia.search.application.port.out.SearchIndex;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;
import org.shakvilla.beatzmedia.search.domain.ReindexReport;

/**
 * Application service implementing {@link ReindexUseCase}. Operational full rebuild; idempotent
 * (upsert-only). {@code type=null} = ALL.
 *
 * <p>Source entities are supplied by {@link IndexSource} beans contributed by the owning modules
 * (Search ADD §9) rather than read from those modules directly — search depends on no other module.
 * Documents are upserted regardless of {@link IndexDocument#visible()}: because reindex never
 * deletes, omitting a hidden entity would strand its stale visible document in the index.
 */
@ApplicationScoped
class ReindexService implements ReindexUseCase {

  private final SearchIndex searchIndex;
  private final Clock clock;
  private final List<IndexSource> sources;

  @Inject
  ReindexService(SearchIndex searchIndex, Clock clock, Instance<IndexSource> sources) {
    this(searchIndex, clock, sources.stream().toList());
  }

  ReindexService(SearchIndex searchIndex, Clock clock, List<IndexSource> sources) {
    this.searchIndex = searchIndex;
    this.clock = clock;
    this.sources = List.copyOf(sources);
  }

  @Override
  @Transactional
  public ReindexReport reindex(EntityType type) {
    // INV-10: AuditEntry on admin-triggered reindex — deferred until an admin REST trigger lands
    // (tracked as search.md §12 F6). The scheduled job is not a privileged user mutation.
    var startedAt = clock.now();

    List<EntityType> types = type == null ? Arrays.asList(EntityType.values()) : List.of(type);
    long indexed = 0L;

    for (EntityType t : types) {
      for (IndexSource source : sourcesFor(t)) {
        for (IndexDocument document : source.load()) {
          searchIndex.upsert(document);
          indexed++;
        }
      }
    }

    var completedAt = clock.now();
    return new ReindexReport(type, indexed, 0L, startedAt, completedAt);
  }

  private List<IndexSource> sourcesFor(EntityType type) {
    List<IndexSource> matching = new ArrayList<>();
    for (IndexSource source : sources) {
      if (source.entityType() == type) {
        matching.add(source);
      }
    }
    return matching;
  }
}
```

Note `IndexDocumentRepository` is no longer used by this service. Leave that port and its adapter in
place — do not delete them; `count` is still a legitimate read port and removing it is out of scope.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd backend && ./mvnw -q test -Dtest=ReindexServiceTest`
Expected: PASS (5 tests).

- [ ] **Step 6: Run ArchUnit — this is the rule most likely to bite**

Run: `cd backend && ./mvnw -q test -Dtest=ArchitectureRulesTest`
Expected: PASS. `ReindexService` lives in `..application..` and must not call `Instant.now()` — it uses
the injected `Clock`, which is why this passes.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/search/application/port/out/IndexSource.java \
        backend/src/main/java/org/shakvilla/beatzmedia/search/application/service/ReindexService.java \
        backend/src/test/java/org/shakvilla/beatzmedia/search/application/service/ReindexServiceTestHelper.java \
        backend/src/test/java/org/shakvilla/beatzmedia/search/unit/ReindexServiceTest.java
git commit -m "feat(search): WU-SRCH-2 IndexSource SPI and real reindex service"
```

---

### Task 3: Catalog enumeration on `CatalogRepository`

**Files:**
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/out/CatalogRepository.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence/JpaCatalogRepository.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/it/CatalogEnumerationIT.java`

**Interfaces:**
- Produces: four enumeration methods on `CatalogRepository`, consumed by Task 4:
  - `List<Track> allTracksForIndex()`
  - `List<ArtistProfile> allArtistsForIndex()`
  - `List<Album> allAlbumsForIndex()`
  - `List<Playlist> allPlaylistsForIndex()`

These are catalog's **own** out-port, so only catalog may call them — Task 4's adapter lives in catalog,
which is what makes this legal.

- [ ] **Step 1: Read the existing repository to match its idiom**

Run: `grep -n "tracksByIds\|playlistsByIds\|artistsByIds\|albumsByIds" backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence/JpaCatalogRepository.java`

Read those method bodies in full. Match their JPQL style, their entity→domain mapping helpers
(`trackToDomain`, and the equivalents for artist/album/playlist), and their null/empty handling. Do not
invent a new mapping path — reuse the existing private mappers.

- [ ] **Step 2: Write the failing integration test**

This is an IT rather than a unit test because the behavior under test *is* the JPQL. It relies on the
repeatable seed migration having been applied, which Dev Services does automatically.

```java
package org.shakvilla.beatzmedia.catalog.it;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;

@QuarkusTest
@Tag("integration")
class CatalogEnumerationIT {

  @Inject CatalogRepository repository;

  @Test
  void allTracksForIndex_returns_the_seeded_tracks() {
    var tracks = repository.allTracksForIndex();

    assertFalse(tracks.isEmpty(), "expected the seeded tracks to be enumerated");
    assertTrue(
        tracks.stream().anyMatch(t -> t.getId().value().equals("last-last")),
        "expected seeded track 'last-last'");
  }

  @Test
  void allArtistsForIndex_returns_the_seeded_artists() {
    var artists = repository.allArtistsForIndex();

    assertFalse(artists.isEmpty(), "expected the seeded artists to be enumerated");
    assertTrue(
        artists.stream().anyMatch(a -> a.getId().value().equals("black-sherif")),
        "expected seeded artist 'black-sherif'");
  }

  @Test
  void allAlbumsForIndex_returns_the_seeded_albums() {
    var albums = repository.allAlbumsForIndex();

    assertFalse(albums.isEmpty(), "expected the seeded albums to be enumerated");
    assertTrue(
        albums.stream().anyMatch(a -> a.getId().value().equals("iron-boy")),
        "expected seeded album 'iron-boy'");
  }

  @Test
  void allPlaylistsForIndex_returns_private_playlists_too_so_the_indexer_can_hide_them() {
    var playlists = repository.allPlaylistsForIndex();

    Playlist priv =
        playlists.stream()
            .filter(p -> p.getId().value().equals("private-test-playlist"))
            .findFirst()
            .orElse(null);

    // Enumerated, NOT filtered out: the indexer needs it so it can write visible=false.
    assertTrue(priv != null, "private playlist must be enumerated so the indexer can hide it");
    assertFalse(priv.isPublic(), "the seeded private playlist should not be public");
    assertTrue(
        playlists.stream().anyMatch(p -> p.getId().value().equals("vibes-from-the-233")),
        "public playlists must be enumerated too");
  }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `cd backend && ./mvnw -q verify -DskipITs=false -DskipUnitTests=true -Dit.test=CatalogEnumerationIT`
Expected: FAIL — compilation error, `allTracksForIndex()` undefined on `CatalogRepository`.

Note this is `verify` + `-Dit.test` (failsafe), not `test` + `-Dtest` (surefire) — `*IT` classes are
failsafe's, and surefire's default includes do not match them.

- [ ] **Step 4: Add the port methods**

Add to `CatalogRepository` (keep the file's existing javadoc style):

```java
  /**
   * All tracks eligible for the search index, i.e. those whose audio has finished processing and
   * which are not gated behind a non-live release. Ordering is unspecified. Used only by the
   * catalog-side search indexer (WU-SRCH-2); not a public listing.
   */
  List<Track> allTracksForIndex();

  /** All artist profiles, for the search indexer (WU-SRCH-2). Ordering is unspecified. */
  List<ArtistProfile> allArtistsForIndex();

  /** All albums, for the search indexer (WU-SRCH-2). Ordering is unspecified. */
  List<Album> allAlbumsForIndex();

  /**
   * All playlists — including private ones, so the indexer can write them with {@code visible=false}
   * rather than omitting them (reindex is upsert-only). For the search indexer (WU-SRCH-2).
   */
  List<Playlist> allPlaylistsForIndex();
```

- [ ] **Step 5: Implement them in `JpaCatalogRepository`**

The track query is the only one with a filter. `release_id IS NULL` must be treated as visible: the seed
has zero `release` rows and every seeded track has `release_id = NULL`, so requiring a live release would
index nothing. Use the `release_track` join table's owning release where present — but since `TrackEntity`
carries `release_id` directly and the domain `Track` has no release field, the simplest correct form is a
native-free JPQL left join on the release entity by id.

Adapt the exact entity/field names to what this file already uses (check whether the release entity is
`ReleaseEntity` and whether `TrackEntity` exposes `releaseId` as a plain column or an association — the
existing `markReleaseTracksReady` method is the reference for how this file joins track↔release):

```java
  @Override
  public List<Track> allTracksForIndex() {
    return em.createQuery(
            """
            SELECT t FROM TrackEntity t
            WHERE t.status = 'ready'
              AND (t.releaseId IS NULL
                   OR EXISTS (SELECT 1 FROM ReleaseEntity r WHERE r.id = t.releaseId AND r.status = 'live'))
            """,
            TrackEntity.class)
        .getResultList()
        .stream()
        .map(this::trackToDomain)
        .toList();
  }

  @Override
  public List<ArtistProfile> allArtistsForIndex() {
    return em.createQuery("SELECT a FROM ArtistProfileEntity a", ArtistProfileEntity.class)
        .getResultList()
        .stream()
        .map(this::artistToDomain)
        .toList();
  }

  @Override
  public List<Album> allAlbumsForIndex() {
    return em.createQuery("SELECT a FROM AlbumEntity a", AlbumEntity.class)
        .getResultList()
        .stream()
        .map(this::albumToDomain)
        .toList();
  }

  @Override
  public List<Playlist> allPlaylistsForIndex() {
    return em.createQuery("SELECT p FROM PlaylistEntity p", PlaylistEntity.class)
        .getResultList()
        .stream()
        .map(this::playlistToDomain)
        .toList();
  }
```

**The private mapper method names above (`trackToDomain`, `artistToDomain`, `albumToDomain`,
`playlistToDomain`) and the entity class names are a best guess from the design pass — verify each
against the actual file and use whatever it really calls them.** If a mapper is inlined rather than
extracted for some entity, follow that file's existing approach for that entity rather than refactoring
it.

If `TrackEntity` models the release as an association rather than a raw `releaseId` column, adjust the
JPQL accordingly (e.g. `t.release IS NULL OR t.release.status = 'live'`) — the semantic requirement is
what matters: **`status = 'ready'` AND (no release OR the release is live)**.

- [ ] **Step 6: Run the IT to verify it passes**

Run: `cd backend && ./mvnw -q verify -DskipITs=false -DskipUnitTests=true -Dit.test=CatalogEnumerationIT`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/catalog/application/port/out/CatalogRepository.java \
        backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/persistence/JpaCatalogRepository.java \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/it/CatalogEnumerationIT.java
git commit -m "feat(catalog): WU-SRCH-2 enumerate indexable catalog entities"
```

---

### Task 4: Catalog `IndexSource` adapters

**Files:**
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/search/CatalogIndexDocuments.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/search/TrackIndexSource.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/search/ArtistIndexSource.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/search/AlbumIndexSource.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/search/PlaylistIndexSource.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/catalog/unit/CatalogIndexDocumentsTest.java`

**Interfaces:**
- Consumes: `IndexSource` from Task 2 (`entityType(): EntityType`, `load(): List<IndexDocument>`);
  `CatalogRepository.allTracksForIndex/allArtistsForIndex/allAlbumsForIndex/allPlaylistsForIndex` from
  Task 3.
- Produces: four CDI beans that `ReindexService` discovers automatically. No other module consumes them.

Mapping is extracted into a pure static class (`CatalogIndexDocuments`) so it can be unit-tested without
CDI or a database; the four `IndexSource` beans are thin wiring.

- [ ] **Step 1: Write the failing mapping test**

```java
package org.shakvilla.beatzmedia.catalog.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.adapter.out.search.CatalogIndexDocuments;
import org.shakvilla.beatzmedia.search.domain.EntityType;

class CatalogIndexDocumentsTest {

  /** Mirrors SearchDocumentMapper.ALLOWED_PAYLOAD_KEYS — anything else is silently stripped on write. */
  private static final Set<String> TRACK_ALLOWED =
      Set.of("image", "duration_sec", "price_minor", "price_amount", "price_currency", "quality", "type", "genre");
  private static final Set<String> ARTIST_ALLOWED = Set.of("image", "genre");
  private static final Set<String> ALBUM_ALLOWED = Set.of("image", "price_minor", "price_amount", "price_currency", "genre");
  private static final Set<String> PLAYLIST_ALLOWED = Set.of("image");

  @Test
  void track_maps_title_subtitle_and_searchable_text() {
    var track = CatalogTestFixtures.track("t1", "Second Sermon", "black-sherif", "Black Sherif", 1500L);

    var doc = CatalogIndexDocuments.fromTrack(track);

    assertEquals(EntityType.TRACK, doc.entityType());
    assertEquals("t1", doc.entityId());
    assertEquals("Second Sermon", doc.title());
    assertEquals("Black Sherif", doc.subtitle());
    assertTrue(doc.searchText().contains("Second Sermon"), "searchText should carry the title");
    assertTrue(doc.searchText().contains("Black Sherif"), "searchText should carry the artist name");
    assertEquals(1500L, doc.popularity().score());
    assertTrue(doc.visible());
  }

  @Test
  void track_with_null_plays_gets_zero_popularity_not_an_NPE() {
    var track = CatalogTestFixtures.track("t2", "Kwaku the Traveller", "black-sherif", "Black Sherif", null);

    var doc = CatalogIndexDocuments.fromTrack(track);

    assertEquals(0L, doc.popularity().score());
  }

  @Test
  void track_payload_only_carries_allow_listed_keys() {
    var track = CatalogTestFixtures.track("t3", "Title", "a1", "Artist", 1L);

    var doc = CatalogIndexDocuments.fromTrack(track);

    assertTrue(
        TRACK_ALLOWED.containsAll(doc.payload().keySet()),
        "payload had keys SearchDocumentMapper would silently strip: " + doc.payload().keySet());
  }

  @Test
  void public_playlist_is_visible_and_private_playlist_is_indexed_but_hidden() {
    var publicPlaylist = CatalogTestFixtures.playlist("p1", "Vibes", "BeatzClik", true, 10L);
    var privatePlaylist = CatalogTestFixtures.playlist("p2", "My Private Playlist", "Me", false, 0L);

    assertTrue(CatalogIndexDocuments.fromPlaylist(publicPlaylist).visible());
    // Indexed, not omitted: reindex is upsert-only, so hiding must be expressed as visible=false.
    assertFalse(CatalogIndexDocuments.fromPlaylist(privatePlaylist).visible());
  }

  @Test
  void playlist_payload_only_carries_image() {
    var playlist = CatalogTestFixtures.playlist("p1", "Vibes", "BeatzClik", true, 10L);

    var keys = CatalogIndexDocuments.fromPlaylist(playlist).payload().keySet();

    assertTrue(PLAYLIST_ALLOWED.containsAll(keys), "unexpected playlist payload keys: " + keys);
  }

  @Test
  void artist_uses_monthly_listeners_for_popularity_and_is_visible() {
    var artist = CatalogTestFixtures.artist("a1", "Black Sherif", 9000L);

    var doc = CatalogIndexDocuments.fromArtist(artist);

    assertEquals(EntityType.ARTIST, doc.entityType());
    assertEquals("Black Sherif", doc.title());
    assertEquals(9000L, doc.popularity().score());
    assertTrue(doc.visible());
    assertTrue(ARTIST_ALLOWED.containsAll(doc.payload().keySet()), "unexpected artist payload keys");
  }

  @Test
  void artist_with_null_monthly_listeners_gets_zero_popularity_not_an_NPE() {
    var artist = CatalogTestFixtures.artist("a2", "Nobody", null);

    assertEquals(0L, CatalogIndexDocuments.fromArtist(artist).popularity().score());
  }

  @Test
  void album_has_no_popularity_source_so_it_is_zero() {
    var album = CatalogTestFixtures.album("al1", "Iron Boy", "black-sherif", "Black Sherif");

    var doc = CatalogIndexDocuments.fromAlbum(album);

    assertEquals(EntityType.ALBUM, doc.entityType());
    assertEquals("Black Sherif", doc.subtitle());
    assertEquals(0L, doc.popularity().score());
    assertTrue(doc.visible());
    assertTrue(ALBUM_ALLOWED.containsAll(doc.payload().keySet()), "unexpected album payload keys");
  }

  @Test
  void every_document_has_a_non_blank_title_and_id() {
    // IndexDocument's compact constructor rejects blank title/entityId — pin that we never build one.
    List<String> titles =
        List.of(
            CatalogIndexDocuments.fromTrack(CatalogTestFixtures.track("t1", "T", "a1", "A", 1L)).title(),
            CatalogIndexDocuments.fromArtist(CatalogTestFixtures.artist("a1", "A", 1L)).title(),
            CatalogIndexDocuments.fromAlbum(CatalogTestFixtures.album("al1", "Al", "a1", "A")).title(),
            CatalogIndexDocuments.fromPlaylist(CatalogTestFixtures.playlist("p1", "P", "C", true, 1L)).title());

    titles.forEach(t -> assertFalse(t == null || t.isBlank(), "title must be non-blank"));
  }
}
```

**`CatalogTestFixtures` is a helper you must write** at
`backend/src/test/java/org/shakvilla/beatzmedia/catalog/unit/CatalogTestFixtures.java`, constructing real
`Track`/`ArtistProfile`/`Album`/`Playlist` domain objects. **First check whether an equivalent fixture
already exists** (`grep -rln "class CatalogTestFixtures\|Track.of(\|new Track(" backend/src/test/java/org/shakvilla/beatzmedia/catalog/`)
— if one does, reuse it and adapt the test's calls to its API instead of writing a duplicate. Build the
fixtures against the real constructors in `catalog/domain/Track.java` etc.; do not guess signatures.

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest=CatalogIndexDocumentsTest`
Expected: FAIL — `CatalogIndexDocuments` not found.

- [ ] **Step 3: Write the mapper**

```java
package org.shakvilla.beatzmedia.catalog.adapter.out.search;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;
import org.shakvilla.beatzmedia.search.domain.Popularity;

/**
 * Maps catalog domain entities to search {@link IndexDocument}s. Pure and stateless so it can be
 * unit-tested without CDI or a database.
 *
 * <p>Payload keys are restricted to what {@code SearchDocumentMapper.ALLOWED_PAYLOAD_KEYS} permits
 * for each type — anything else is silently dropped on persistence, so emitting it would be a lie.
 *
 * <p>{@code visible} is the only thing gating what search returns: the catalog repository applies no
 * visibility filter of its own, and {@code PostgresFtsSearchAdapter} queries {@code WHERE visible =
 * true}. Entities that must not surface are indexed with {@code visible=false} rather than skipped,
 * because reindex is upsert-only and a skipped entity would keep any stale visible document forever.
 */
public final class CatalogIndexDocuments {

  private CatalogIndexDocuments() {}

  public static IndexDocument fromTrack(Track track) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("image", track.getImage());
    payload.put("duration_sec", track.getDurationSec());
    track.getPriceMinor().ifPresent(minor -> putPrice(payload, minor));
    track.getQuality().ifPresent(q -> payload.put("quality", q));

    return new IndexDocument(
        EntityType.TRACK,
        track.getId().value(),
        track.getTitle(),
        track.getArtistName(),
        track.getTitle() + " " + track.getArtistName(),
        new Popularity(track.getPlays().orElse(0L)),
        true,
        payload);
  }

  public static IndexDocument fromArtist(ArtistProfile artist) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("image", artist.getImage());
    if (artist.getGenres() != null && !artist.getGenres().isEmpty()) {
      payload.put("genre", artist.getGenres().get(0));
    }

    return new IndexDocument(
        EntityType.ARTIST,
        artist.getId().value(),
        artist.getName(),
        artist.getLocation(),
        artist.getName(),
        new Popularity(artist.getMonthlyListeners() == null ? 0L : artist.getMonthlyListeners()),
        true,
        payload);
  }

  public static IndexDocument fromAlbum(Album album) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("image", album.getCoverImage());
    putPrice(payload, album.getListPriceMinor());
    if (album.getGenres() != null && !album.getGenres().isEmpty()) {
      payload.put("genre", album.getGenres().get(0));
    }

    return new IndexDocument(
        EntityType.ALBUM,
        album.getId().value(),
        album.getTitle(),
        album.getArtistName(),
        album.getTitle() + " " + album.getArtistName(),
        Popularity.ZERO,
        true,
        payload);
  }

  public static IndexDocument fromPlaylist(Playlist playlist) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("image", playlist.getImage());

    return new IndexDocument(
        EntityType.PLAYLIST,
        playlist.getId().value(),
        playlist.getTitle(),
        playlist.getCreator(),
        playlist.getTitle() + " " + playlist.getCreator(),
        new Popularity(playlist.getFollowers() == null ? 0L : playlist.getFollowers()),
        playlist.isPublic(),
        payload);
  }

  private static void putPrice(Map<String, Object> payload, long priceMinor) {
    payload.put("price_minor", priceMinor);
    payload.put(
        "price_amount", BigDecimal.valueOf(priceMinor).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    payload.put("price_currency", "GHS");
  }
}
```

Verify each getter against the real domain classes before assuming: `Track.getPriceMinor()` /
`getPlays()` / `getQuality()` return `Optional`, while `ArtistProfile.getMonthlyListeners()` and
`Playlist.getFollowers()` return nullable `Long`, and `Album.getListPriceMinor()` returns a primitive
`long`. If any of these differs from what this code assumes, follow the real signature.

- [ ] **Step 4: Write the four `IndexSource` beans**

```java
package org.shakvilla.beatzmedia.catalog.adapter.out.search;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.search.application.port.out.IndexSource;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;

/**
 * Supplies catalog tracks to the search module's reindex (WU-SRCH-2). Catalog owns the data and the
 * mapping; search never reads catalog. Same {@code module -> search} direction as
 * {@code store.adapter.out.persistence.SearchIndexPg}.
 */
@ApplicationScoped
public class TrackIndexSource implements IndexSource {

  private final CatalogRepository repository;

  @Inject
  public TrackIndexSource(CatalogRepository repository) {
    this.repository = repository;
  }

  @Override
  public EntityType entityType() {
    return EntityType.TRACK;
  }

  @Override
  public List<IndexDocument> load() {
    return repository.allTracksForIndex().stream().map(CatalogIndexDocuments::fromTrack).toList();
  }
}
```

Write `ArtistIndexSource`, `AlbumIndexSource`, and `PlaylistIndexSource` following exactly this shape —
same javadoc intent, swapping `EntityType.ARTIST` / `allArtistsForIndex()` / `fromArtist`,
`EntityType.ALBUM` / `allAlbumsForIndex()` / `fromAlbum`, and `EntityType.PLAYLIST` /
`allPlaylistsForIndex()` / `fromPlaylist` respectively.

- [ ] **Step 5: Run the mapping tests**

Run: `cd backend && ./mvnw -q test -Dtest=CatalogIndexDocumentsTest`
Expected: PASS (9 tests).

- [ ] **Step 6: Run ArchUnit**

Run: `cd backend && ./mvnw -q test -Dtest=ArchitectureRulesTest`
Expected: PASS. These classes live in `catalog.adapter.out.search` (adapter layer) and depend on
`catalog.application.port.out` and `search.application.port.out` (application layer) — adapter→application
is allowed, and no inbound adapter is touched.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/catalog/adapter/out/search/ \
        backend/src/test/java/org/shakvilla/beatzmedia/catalog/unit/
git commit -m "feat(catalog): WU-SRCH-2 catalog IndexSource adapters"
```

---

### Task 5: `ReindexJob` + end-to-end integration test

**Files:**
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/search/adapter/in/job/ReindexJob.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/search/integration/SearchBackfillIT.java`

**Interfaces:**
- Consumes: `ReindexUseCase` (Task 2), `ScheduledJob` (`platform/application/port/in/ScheduledJob.java`
  — `String jobName()`, `void runOnce()`).

`SchedulerRegistry` already declares the tick (`@Scheduled(every = "10m", identity = "search-reindex")`
→ `runWithLock("search.reindex")`) and resolves the job by name from `Instance<ScheduledJob>`; when no
bean matches, the tick silently no-ops. Registering a bean whose `jobName()` returns `"search.reindex"`
is all that is needed — **do not modify `SchedulerRegistry`.**

- [ ] **Step 1: Write the failing end-to-end IT**

This is the test that proves the whole feature: seeded catalog → reindex → search returns hits, and the
private playlist stays hidden.

```java
package org.shakvilla.beatzmedia.search.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.search.application.port.in.QueryService;
import org.shakvilla.beatzmedia.search.application.port.in.ReindexUseCase;
import org.shakvilla.beatzmedia.search.domain.SearchQuery;
import org.shakvilla.beatzmedia.search.domain.SearchResults;

@QuarkusTest
@Tag("integration")
class SearchBackfillIT {

  @Inject ReindexUseCase reindex;
  @Inject QueryService queryService;

  @Test
  void reindex_populates_the_index_from_seeded_catalog_so_search_returns_hits() {
    var report = reindex.reindex(null);

    assertTrue(report.documentsIndexed() > 0, "reindex should have indexed the seeded catalog");

    SearchResults results = queryService.search(SearchQuery.of("sherif"));

    assertFalse(results.artists().isEmpty(), "search should return artist hits after a reindex");
    assertTrue(
        results.artists().stream().anyMatch(h -> h.entityId().equals("black-sherif")),
        "expected the seeded artist 'black-sherif' to be searchable");
  }

  @Test
  void reindex_is_idempotent_and_can_run_twice_without_duplicating() {
    reindex.reindex(null);
    int first = queryService.search(SearchQuery.of("sherif")).artists().size();

    reindex.reindex(null);
    int second = queryService.search(SearchQuery.of("sherif")).artists().size();

    assertEquals(first, second, "a second reindex must upsert, not duplicate");
  }

  @Test
  void private_seeded_playlist_is_never_returned_by_search() {
    reindex.reindex(null);

    SearchResults results = queryService.search(SearchQuery.of("private"));

    assertFalse(
        results.playlists().stream().anyMatch(h -> h.entityId().equals("private-test-playlist")),
        "the private seeded playlist must never surface in search");
  }

  @Test
  void public_seeded_playlist_is_returned_by_search() {
    reindex.reindex(null);

    SearchResults results = queryService.search(SearchQuery.of("vibes"));

    assertTrue(
        results.playlists().stream().anyMatch(h -> h.entityId().equals("vibes-from-the-233")),
        "expected the seeded public playlist to be searchable");
  }
}
```

`SearchQuery.of(String)` is used by the existing `SearchIndexIT` and by
`catalog/application/service/SearchService.java` — confirm its exact signature before relying on it, and
if `of(String)` does not exist, build the `SearchQuery` the way `SearchService` does.

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && ./mvnw -q verify -DskipITs=false -DskipUnitTests=true -Dit.test=SearchBackfillIT`
Expected: FAIL — assertions fail because nothing indexes yet (or a compile error if a signature differs).

Note: if this test passes at this point, something is wrong — stop and investigate rather than moving on.

- [ ] **Step 3: Write the job**

```java
package org.shakvilla.beatzmedia.search.adapter.in.job;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.platform.application.port.in.ScheduledJob;
import org.shakvilla.beatzmedia.search.application.port.in.ReindexUseCase;

/**
 * Periodic full search reindex (WU-SRCH-2). Backs the {@code search.reindex} tick that
 * {@code SchedulerRegistry} has declared since WU-SRCH-1 but which had no bean behind it, so it
 * silently no-opped and the index stayed empty.
 *
 * <p>Upsert-only and idempotent, so it is safe to run live and safe to run repeatedly. The advisory
 * lock in {@code SchedulerRegistry.runWithLock} keeps concurrent instances from overlapping.
 */
@ApplicationScoped
public class ReindexJob implements ScheduledJob {

  private final ReindexUseCase reindex;

  @Inject
  public ReindexJob(ReindexUseCase reindex) {
    this.reindex = reindex;
  }

  @Override
  public String jobName() {
    return "search.reindex";
  }

  @Override
  public void runOnce() {
    reindex.reindex(null);
  }
}
```

- [ ] **Step 4: Run the IT to verify it passes**

Run: `cd backend && ./mvnw -q verify -DskipITs=false -DskipUnitTests=true -Dit.test=SearchBackfillIT`
Expected: PASS (4 tests).

- [ ] **Step 5: Run ArchUnit**

Run: `cd backend && ./mvnw -q test -Dtest=ArchitectureRulesTest`
Expected: PASS. `ReindexJob` is an inbound adapter (`adapter.in.job`) depending only on
`application.port.in` — it must not import anything from `adapter.out`.

- [ ] **Step 6: Rewrite the two stale `SearchIndexIT` tests that encode the OLD reindex contract**

**Files:** Modify `backend/src/test/java/org/shakvilla/beatzmedia/search/integration/SearchIndexIT.java`

Task 2 deliberately changed `reindex()`'s contract: it used to report a count of rows *already* in
`search_document`; it now loads documents from `IndexSource` beans and upserts them. Two pre-existing
tests still assert the old behavior and are now wrong — they were left failing on purpose until sources
existed, which is now:

- `reindex_report_counts_existing_documents` — indexes two docs directly via `indexEntityUseCase`, then
  asserts `reindex(TRACK).documentsIndexed() == 2`. Under the new contract, `reindex(TRACK)` ignores what
  is already in the table and reports what the catalog `TrackIndexSource` supplied, so this premise no
  longer holds.
- `reindex_all_types_converges_from_empty` — same false premise for `reindex(null)`.

Rewrite both to assert the **new** contract. They run under `@QuarkusTest`, so the real catalog
`IndexSource` beans are present and the seeded catalog is in the DB. Suggested replacements (adapt to the
file's existing helpers and imports — it already has a `doc(...)` helper and uses JUnit 5 `assertEquals`):

```java
  @Test
  void reindex_reports_what_the_sources_supplied_not_what_was_already_indexed() {
    // Pre-existing rows are irrelevant to the report: reindex reads from IndexSource, not the table.
    indexEntityUseCase.index(doc("stale-1", "Stale", "", EntityType.TRACK, 0L, true));

    ReindexReport report = reindexUseCase.reindex(EntityType.TRACK);

    assertEquals(EntityType.TRACK, report.type());
    assertTrue(report.documentsIndexed() > 0, "the catalog TrackIndexSource should have supplied tracks");
    assertEquals(0L, report.documentsRemoved(), "reindex is upsert-only");
  }

  @Test
  void reindex_all_covers_every_type_and_makes_seeded_catalog_searchable() {
    ReindexReport report = reindexUseCase.reindex(null);

    assertEquals(null, report.type());
    assertTrue(report.documentsIndexed() > 0, "reindex(null) should index every type's sources");
  }
```

If either rewritten test needs an import the file lacks (`assertTrue`, `ReindexReport`), add it. Do not
weaken an assertion just to make it pass — if one fails for a reason you did not expect, stop and report
that rather than adjusting the expectation.

- [ ] **Step 7: Run the full suite for regressions**

Run: `cd backend && ./mvnw -q test -DskipITs=true` then `cd backend && ./mvnw -q verify -DskipITs=false -DskipUnitTests=true`
Expected: both PASS. `verify` runs the whole gate including every IT, so it is slow — that is fine and
expected here; this is the step that proves nothing else regressed. If any test outside this WU's files
fails, report exactly which and why **before** changing it.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/search/adapter/in/job/ReindexJob.java \
        backend/src/test/java/org/shakvilla/beatzmedia/search/integration/SearchBackfillIT.java \
        backend/src/test/java/org/shakvilla/beatzmedia/search/integration/SearchIndexIT.java
git commit -m "feat(search): WU-SRCH-2 scheduled reindex job wiring the dormant search.reindex tick"
```

---

### Task 6: Documentation — ADR + module ADDs

**Files:**
- Modify: `backend/docs/00-system-architecture.md` (§9 ADR table)
- Modify: `backend/docs/architecture/search.md`
- Modify: `backend/docs/architecture/catalog.md`

- [ ] **Step 1: Read the existing ADR table and match its format exactly**

Run: `grep -n -A30 "## 9" backend/docs/00-system-architecture.md | head -45`

Find the highest existing ADR number and use the next one. The design pass saw ADR-27 and ADR-28
referenced in an earlier plan — **do not assume ADR-29 is free; read the table and take the actual next
number.** Match the table's column layout exactly.

- [ ] **Step 2: Add the ADR row**

Content (adapt to the table's real columns):

> **ADR-<n>: Reindex sources are contributed by owning modules via an `IndexSource` SPI, not read by
> search.** Search declares `IndexSource` as an outbound port; catalog (and later store/podcasts/events)
> contribute implementations that map their own domain entities to `IndexDocument`s. Supersedes
> search.md §9's sketch of `ReindexUseCase` reading the owning modules' read ports, which would have
> made `search → catalog` a dependency on top of the existing `catalog → search`
> (`catalog.application.service.SearchService` injects search's `QueryService`), creating a cycle —
> and which was not implementable anyway, since no catalog input port can enumerate all entities.
> Keeps the `module → search` direction already set by `store.adapter.out.persistence.SearchIndexPg`.

- [ ] **Step 3: Update `backend/docs/architecture/search.md`**

Make these edits — read the surrounding text first and match its voice:

1. **§9 (Cross-cutting hooks / Reindex job):** replace the claim that reindex "streams source entities
   (via the owning modules' read ports at the catalog/store boundary, or a snapshot feed)" with the
   as-built design: sources are `IndexSource` beans contributed by owning modules and discovered via CDI.
   Reference the new ADR.
2. **The WU-SRCH-1 deferral note** (around the "source-entity streaming is deferred to WU-CAT-3/CAT-4"
   text): record that WU-CAT-3/CAT-4 shipped without delivering it, that the index was consequently
   empty in every environment, and that WU-SRCH-2 closed it.
3. **The dev-bootstrap note** (~§5, "seed catalog/store, then run `ReindexUseCase.reindex(ALL)`"):
   it is now accurate, but add that the `search.reindex` scheduled job (every 10 minutes) does this
   automatically, so a dev stack self-populates within one tick of boot.
4. **§10 WU table:** add a WU-SRCH-2 row.
5. **§12 Tracked deferrals:** keep F6 (no `AuditEntry` on reindex) open — the scheduled job is not a
   privileged user mutation, so INV-10 does not apply to it; the deferral still stands for a future
   admin-triggered reindex. Add two new deferrals:
   - **Event-driven incremental indexing is still not wired.** `IndexEventObserversStub` remains a
     placeholder; freshness is bounded by the 10-minute reindex tick. Catalog does now publish
     `ReleaseWentLive`/`ContentTakenDown`, so observers are implementable in a follow-up.
   - **Reindex is upsert-only and never removes documents.** An entity deleted from the catalog keeps
     its document. Hiding works (`visible=false` on upsert) but hard deletion does not.
6. **Add a short subsection documenting the visibility rules** the backfill applies, since `visible` is
   the only gate search has: tracks — `status = 'ready'` and (no release or release is `live`); playlists
   — `visible = is_public`; artists and albums — always visible. State plainly that the catalog
   repository applies no visibility filter of its own, so this is load-bearing.

- [ ] **Step 4: Update `backend/docs/architecture/catalog.md`**

Add an as-built note recording that catalog now owns the mapping of its entities into the search index:
the four `IndexSource` beans under `catalog/adapter/out/search/` plus the `allXForIndex()` enumeration
methods on `CatalogRepository`. Note explicitly that `allPlaylistsForIndex()` deliberately returns
private playlists so the indexer can mark them `visible=false` (omitting them would strand stale
documents, since reindex never deletes). Match the file's existing as-built-note convention.

- [ ] **Step 5: Commit**

```bash
git add backend/docs/00-system-architecture.md backend/docs/architecture/search.md backend/docs/architecture/catalog.md
git commit -m "docs(search): WU-SRCH-2 ADR + search/catalog ADD as-built notes"
```

---

### Task 7: Verification gate and pull request

**Files:** none (verification-only task).

- [ ] **Step 1: Ask the repo owner to run the verification gate**

Per this repo's established practice, the agent does not run these (IntelliJ JPS races the build). Ask
the owner to run and report:

```bash
bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh
```

Do not proceed until they confirm both passed.

- [ ] **Step 2: Flip the backlog status to `in_review`**

```bash
# in backend/.project/backlog.yaml, WU-SRCH-2: status: in_progress -> in_review
git add backend/.project/backlog.yaml
git commit -m "chore(search): WU-SRCH-2 move to in_review"
```

- [ ] **Step 3: Open the pull request**

```bash
git push -u origin feat/WU-SRCH-2-search-index-backfill
gh pr create --title "feat(search): WU-SRCH-2 search index backfill" --body "$(cat <<'EOF'
## Summary

`GET /v1/search?q=` returned empty for every query in every environment, because `search_document` was
empty and nothing could populate it: the seed migration bypasses the index, the event observers were
never written, `ReindexService.reindex()` only counted already-indexed rows, and the `search.reindex`
scheduled tick had no bean behind it. `WU-SRCH-1` was marked done, having deferred the indexing wiring to
downstream WUs that shipped without it.

This WU makes reindex real:

- New `IndexSource` outbound port in `search` — an SPI each owning module implements for the entity
  types it owns. Keeps the dependency edge pointing `module → search` (the direction
  `store.adapter.out.persistence.SearchIndexPg` already set) and avoids the `catalog ↔ search` cycle that
  the ADD's original sketch would have created. See ADR in `00-system-architecture.md` §9.
- `ReindexService` rewritten to discover sources via CDI, load their documents, and upsert them.
- Four catalog `IndexSource` beans plus `allXForIndex()` enumeration on `CatalogRepository`.
- `ReindexJob` backs the previously dormant `search.reindex` tick (every 10 min, advisory-locked,
  upsert-only, idempotent).

**Visibility is load-bearing.** The catalog repository applies no visibility filter, and
`PostgresFtsSearchAdapter` queries `WHERE visible = true` — so the index's `visible` flag is the only
gate on what search exposes. Entities that must not surface are indexed with `visible=false` rather than
skipped: reindex never deletes, so skipping would strand a stale `visible=true` document forever. The
seeded private playlist is covered by an explicit test.

No migration (`search_document` already exists). No API contract change.

## Test plan
- [x] `ReindexServiceTest` — 5 unit tests (all/one-type dispatch, non-visible docs still upserted, missing source, clock stamping)
- [x] `CatalogIndexDocumentsTest` — 7 unit tests (mapping, null-popularity coalescing, payload allow-lists, private playlist → `visible=false`)
- [x] `CatalogEnumerationIT` — 4 integration tests against seeded data
- [x] `SearchBackfillIT` — 4 integration tests: seeded catalog → reindex → search returns hits; idempotent; private playlist never returned; public playlist returned
- [x] ArchUnit green
- [ ] `verify.sh` + `smoke.sh` (run by repo owner)

## Follow-ups (documented in `search.md` §12, not in this PR)
- Event-driven incremental indexing is still unwired; freshness is bounded by the 10-minute tick.
- Reindex is upsert-only — a hard-deleted entity keeps its document.
- Admin-triggered reindex + `AuditEntry` (F6) remains deferred.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 4: Update the SDD progress ledger**

Append the outcome to `.superpowers/sdd/progress.md`: commits, PR URL, and anything QA should watch.

---

## Self-Review Notes

- **Coverage of the goal:** search returns real results (Tasks 2–5, proven by `SearchBackfillIT`); the
  index stays fresh within 10 minutes (Task 5); the private playlist never leaks (Tasks 3–5, asserted at
  both the mapping and end-to-end levels); the design decision is recorded (Task 6).
- **Placeholder scan:** no TBD/TODO/"add error handling" phrasing. Where the plan cannot know a real
  name (JPA mapper method names, `SearchQuery.of`'s exact signature, the next free ADR number, whether a
  catalog test fixture already exists), it says so explicitly and instructs the implementer to verify
  against the actual file rather than guessing — those are directed checks, not placeholders.
- **Type consistency:** `IndexSource`'s two methods (`entityType()`, `load()`) are defined in Task 2 and
  consumed with those exact names in Tasks 4 and 5. `CatalogIndexDocuments.fromTrack/fromArtist/fromAlbum/fromPlaylist`
  are defined and consumed with matching names. `allTracksForIndex/allArtistsForIndex/allAlbumsForIndex/allPlaylistsForIndex`
  are defined in Task 3 and consumed in Task 4. `ReindexService`'s two constructors (CDI `Instance` +
  package-private `List`) are what `ReindexServiceTestHelper.create` depends on.
- **Known risk flagged for the implementer:** Task 3's JPQL is the least certain code in this plan
  (entity names, whether the track→release link is a raw column or an association). The task states the
  semantic requirement so a correct implementation is possible even if the literal JPQL needs adjusting.
