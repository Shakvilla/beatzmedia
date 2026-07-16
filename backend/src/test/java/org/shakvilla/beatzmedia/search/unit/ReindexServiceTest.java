package org.shakvilla.beatzmedia.search.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    assertEquals(3, index.upsertCallCount);
    assertEquals(3L, report.documentsIndexed());
    assertEquals(0L, report.documentsRemoved());
    assertNull(report.type());
  }

  @Test
  void reindex_of_one_type_only_touches_that_types_source() {
    FakeSearchIndex index = new FakeSearchIndex();
    var tracks = source(EntityType.TRACK, List.of(doc(EntityType.TRACK, "t1", true)));
    var artists = source(EntityType.ARTIST, List.of(doc(EntityType.ARTIST, "a1", true)));
    var service = ReindexServiceTestHelper.create(index, FakeClock.fixed(), List.of(tracks, artists));

    ReindexReport report = service.reindex(EntityType.ARTIST);

    assertEquals(1L, report.documentsIndexed());
    assertEquals(1, index.upsertCallCount);
    assertEquals(EntityType.ARTIST, report.type());
  }

  @Test
  void reindex_upserts_non_visible_documents_too_so_hidden_entities_converge() {
    FakeSearchIndex index = new FakeSearchIndex();
    var playlists =
        source(EntityType.PLAYLIST, List.of(doc(EntityType.PLAYLIST, "public-1", true), doc(EntityType.PLAYLIST, "private-1", false)));
    var service = ReindexServiceTestHelper.create(index, FakeClock.fixed(), List.of(playlists));

    ReindexReport report = service.reindex(EntityType.PLAYLIST);

    // Both are written; visible=false is what hides the private one, not omission from the index.
    assertEquals(2L, report.documentsIndexed());
    assertEquals(2, index.upsertCallCount);
  }

  @Test
  void reindex_with_no_source_for_a_type_reports_zero_rather_than_failing() {
    FakeSearchIndex index = new FakeSearchIndex();
    var service = ReindexServiceTestHelper.create(index, FakeClock.fixed(), List.of());

    ReindexReport report = service.reindex(EntityType.EVENT);

    assertEquals(0L, report.documentsIndexed());
    assertEquals(0, index.upsertCallCount);
  }

  @Test
  void reindex_isolates_a_source_whose_load_throws_so_a_healthy_source_still_indexes() {
    // WU-SRCH-2 Finding 2 regression: a single bad row can throw while a source builds its
    // document list (e.g. IndexDocument's own validation rejecting a blank title) before the
    // source ever returns — that must not stop a second, healthy source from being indexed, and
    // the report must count only what was actually indexed.
    FakeSearchIndex index = new FakeSearchIndex();
    IndexSource broken = new IndexSource() {
      @Override
      public EntityType entityType() {
        return EntityType.TRACK;
      }

      @Override
      public List<IndexDocument> load() {
        throw new IllegalArgumentException("title must not be blank");
      }
    };
    var healthy = source(EntityType.ARTIST, List.of(doc(EntityType.ARTIST, "a1", true)));
    var service = ReindexServiceTestHelper.create(index, FakeClock.fixed(), List.of(broken, healthy));

    ReindexReport report = service.reindex(null);

    assertEquals(1L, report.documentsIndexed(), "the healthy source's document must still be indexed");
    assertEquals(1, index.upsertCallCount);
  }

  @Test
  void reindex_isolates_a_document_that_fails_to_upsert_so_the_rest_of_the_source_still_indexes() {
    // A failure at upsert time (rather than at load()) must isolate to just that document — the
    // rest of the same source's documents still get indexed and counted.
    FakeSearchIndex index = new FakeSearchIndex();
    index.failUpsertFor.add("TRACK|bad-1");
    var tracks = source(
        EntityType.TRACK,
        List.of(doc(EntityType.TRACK, "bad-1", true), doc(EntityType.TRACK, "good-1", true)));
    var service = ReindexServiceTestHelper.create(index, FakeClock.fixed(), List.of(tracks));

    ReindexReport report = service.reindex(EntityType.TRACK);

    assertEquals(1L, report.documentsIndexed(), "only the document that upserted successfully must be counted");
    assertEquals(1, index.upsertCallCount);
  }

  @Test
  void reindex_stamps_started_and_completed_from_the_clock() {
    FakeSearchIndex index = new FakeSearchIndex();
    var clock = FakeClock.fixed();
    var service = ReindexServiceTestHelper.create(index, clock, List.of());

    ReindexReport report = service.reindex(null);

    assertEquals(clock.now(), report.startedAt());
    assertEquals(clock.now(), report.completedAt());
  }
}
