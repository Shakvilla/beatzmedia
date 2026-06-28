package org.shakvilla.beatzmedia.search.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.search.application.service.IndexingServiceTestHelper;
import org.shakvilla.beatzmedia.search.application.service.SearchQueryServiceTestHelper;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;
import org.shakvilla.beatzmedia.search.domain.Popularity;
import org.shakvilla.beatzmedia.search.domain.SearchQuery;
import org.shakvilla.beatzmedia.search.domain.SearchResults;

/**
 * Unit tests for QueryService grouping and topResult selection.
 * Uses FakeSearchIndex — no framework.
 */
class QueryServiceTest {

  FakeSearchIndex fakeIndex;
  IndexingServiceTestHelper indexService;
  SearchQueryServiceTestHelper queryService;

  @BeforeEach
  void setUp() {
    fakeIndex = new FakeSearchIndex();
    indexService = new IndexingServiceTestHelper(fakeIndex);
    queryService = new SearchQueryServiceTestHelper(fakeIndex);
  }

  @Test
  void results_are_grouped_by_entity_type() {
    indexService.index(doc("t1", "Drake Song", EntityType.TRACK));
    indexService.index(doc("a1", "Drake Artist", EntityType.ARTIST));
    indexService.index(doc("al1", "Drake Album", EntityType.ALBUM));

    SearchResults results = queryService.search(SearchQuery.of("drake"));

    assertEquals(1, results.tracks().size());
    assertEquals(1, results.artists().size());
    assertEquals(1, results.albums().size());
    assertTrue(results.playlists().isEmpty());
  }

  @Test
  void topResult_is_highest_scored_hit() {
    // exact match => score 1.0; partial => 0.5
    indexService.index(doc("t1", "Magic Song", EntityType.TRACK));   // partial match
    indexService.index(doc("a1", "magic", EntityType.ARTIST));        // exact match => higher score

    SearchResults results = queryService.search(SearchQuery.of("magic"));

    assertTrue(results.topResult().isPresent());
    assertEquals(EntityType.ARTIST, results.topResult().get().entityType());
  }

  @Test
  void topResult_tiebreak_by_popularity() {
    var highPop = new IndexDocument(EntityType.TRACK, "t1", "Beat Mix", null, "", new Popularity(100), true, Map.of());
    var lowPop  = new IndexDocument(EntityType.ARTIST, "a1", "Beat Maker", null, "", new Popularity(10), true, Map.of());
    indexService.index(highPop);
    indexService.index(lowPop);

    SearchResults results = queryService.search(SearchQuery.of("beat"));

    assertTrue(results.topResult().isPresent());
    assertEquals("t1", results.topResult().get().entityId());
  }

  @Test
  void invisible_documents_not_returned() {
    // INV-SRCH-2
    var hidden = new IndexDocument(EntityType.TRACK, "t99", "Hidden Track", null, "", Popularity.ZERO, false, Map.of());
    indexService.index(hidden);
    SearchResults results = queryService.search(SearchQuery.of("hidden"));
    assertTrue(results.tracks().isEmpty());
    assertFalse(results.topResult().isPresent());
  }

  private IndexDocument doc(String id, String title, EntityType type) {
    return new IndexDocument(type, id, title, null, "", Popularity.ZERO, true, Map.of());
  }
}
