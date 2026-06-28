package org.shakvilla.beatzmedia.search.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.search.application.port.in.IndexEntityUseCase;
import org.shakvilla.beatzmedia.search.application.port.in.QueryService;
import org.shakvilla.beatzmedia.search.application.port.in.ReindexUseCase;
import org.shakvilla.beatzmedia.search.application.port.out.IndexDocumentRepository;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;
import org.shakvilla.beatzmedia.search.domain.Popularity;
import org.shakvilla.beatzmedia.search.domain.ReindexReport;
import org.shakvilla.beatzmedia.search.domain.SearchFilters;
import org.shakvilla.beatzmedia.search.domain.SearchQuery;
import org.shakvilla.beatzmedia.search.domain.SearchResults;
import org.shakvilla.beatzmedia.search.domain.SearchScope;
import org.shakvilla.beatzmedia.search.domain.Sort;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for the search index lifecycle using real Postgres (Quarkus Dev Services).
 * Covers: FTS + pg_trgm, INV-SRCH-1, INV-SRCH-2, INV-SRCH-3, INV-SRCH-4, sort orders, reindex.
 */
@QuarkusTest
@Tag("integration")
class SearchIndexIT {

  @Inject IndexEntityUseCase indexEntityUseCase;
  @Inject QueryService queryService;
  @Inject ReindexUseCase reindexUseCase;
  @Inject IndexDocumentRepository indexDocumentRepository;
  @Inject jakarta.persistence.EntityManager em;

  @BeforeEach
  @Transactional
  void cleanUp() {
    em.createNativeQuery("DELETE FROM search_document").executeUpdate();
  }

  @Test
  void upsert_and_search_by_fts() {
    indexEntityUseCase.index(doc("t1", "Afrobeats Love Song", "love song afrobeats", EntityType.TRACK, 10L, true));

    SearchResults results = queryService.search(SearchQuery.of("afrobeats"));
    assertEquals(1, results.tracks().size());
    assertEquals("t1", results.tracks().get(0).entityId());
  }

  @Test
  void visible_false_excluded_from_results() {
    // INV-SRCH-2
    indexEntityUseCase.index(doc("t99", "Hidden Song", "hidden", EntityType.TRACK, 0L, false));

    SearchResults results = queryService.search(SearchQuery.of("hidden"));
    assertTrue(results.tracks().isEmpty());
    assertFalse(results.topResult().isPresent());
  }

  @Test
  void upsert_is_idempotent_on_type_and_id() {
    // INV-SRCH-1: two upserts on same (type,id) must yield exactly one row
    indexEntityUseCase.index(doc("t1", "Song V1", "old text", EntityType.TRACK, 0L, true));
    indexEntityUseCase.index(doc("t1", "Song V2", "new text", EntityType.TRACK, 5L, true));

    long count = indexDocumentRepository.count(EntityType.TRACK);
    assertEquals(1, count);

    SearchResults results = queryService.search(SearchQuery.of("song v2"));
    assertEquals(1, results.tracks().size());
    assertEquals("Song V2", results.tracks().get(0).title());
  }

  @Test
  void deindex_removes_document() {
    indexEntityUseCase.index(doc("t1", "Track Remove", "", EntityType.TRACK, 0L, true));
    indexEntityUseCase.deindex(EntityType.TRACK, "t1");

    SearchResults results = queryService.search(SearchQuery.of("remove"));
    assertTrue(results.tracks().isEmpty());
  }

  @Test
  void typo_tolerant_trgm_match() {
    // pg_trgm: "Shakiira" should be similar enough to "Shakira"
    indexEntityUseCase.index(doc("a1", "Shakira", "shakira pop", EntityType.ARTIST, 50L, true));

    SearchResults results = queryService.search(SearchQuery.of("Shakiira"));
    assertFalse(results.artists().isEmpty(), "Expected trgm match for Shakiira -> Shakira");
  }

  @Test
  void sort_by_popularity() {
    indexEntityUseCase.index(doc("t1", "Beat One", "beat", EntityType.STORE_ITEM, 100L, true));
    indexEntityUseCase.index(doc("t2", "Beat Two", "beat", EntityType.STORE_ITEM, 500L, true));

    var query = new SearchQuery("beat", SearchScope.STORE_ITEM, SearchFilters.withSort(Sort.POPULAR), PageRequest.defaults());
    SearchResults results = queryService.search(query);
    assertEquals(2, results.storeItems().size());
    assertEquals(500L, results.storeItems().get(0).popularity());
  }

  @Test
  void sort_by_price_asc() {
    indexEntityUseCase.index(docWithPrice("s1", "Expensive Beat", 5000L, EntityType.STORE_ITEM));
    indexEntityUseCase.index(docWithPrice("s2", "Cheap Beat", 100L, EntityType.STORE_ITEM));

    var query = new SearchQuery("beat", SearchScope.STORE_ITEM, SearchFilters.withSort(Sort.PRICE_ASC), PageRequest.defaults());
    SearchResults results = queryService.search(query);
    assertEquals(2, results.storeItems().size());
    assertEquals("s2", results.storeItems().get(0).entityId());
  }

  @Test
  void reindex_report_counts_existing_documents() {
    indexEntityUseCase.index(doc("t1", "Track A", "", EntityType.TRACK, 0L, true));
    indexEntityUseCase.index(doc("t2", "Track B", "", EntityType.TRACK, 0L, true));

    ReindexReport report = reindexUseCase.reindex(EntityType.TRACK);
    assertEquals(2L, report.documentsIndexed());
    assertEquals(EntityType.TRACK, report.type());
  }

  @Test
  void reindex_all_types_converges_from_empty() {
    indexEntityUseCase.index(doc("t1", "Track", "", EntityType.TRACK, 0L, true));
    indexEntityUseCase.index(doc("a1", "Artist", "", EntityType.ARTIST, 0L, true));

    ReindexReport report = reindexUseCase.reindex(null);
    assertEquals(2L, report.documentsIndexed());
  }

  @Test
  void tsv_trigger_populates_tsv_on_insert() {
    // INV-SRCH-4: DB trigger must fill tsv; FTS search must work after insert
    indexEntityUseCase.index(doc("t1", "Highlife Guitar", "classic ghana highlife", EntityType.TRACK, 0L, true));

    SearchResults results = queryService.search(SearchQuery.of("highlife"));
    assertEquals(1, results.tracks().size());
  }

  @Test
  void popularity_taken_from_supplied_value() {
    // INV-SRCH-3
    long suppliedPop = 777L;
    indexEntityUseCase.index(doc("t1", "Pop Track", "", EntityType.TRACK, suppliedPop, true));

    SearchResults results = queryService.search(SearchQuery.of("pop track"));
    assertFalse(results.tracks().isEmpty());
    assertEquals(suppliedPop, results.tracks().get(0).popularity());
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private IndexDocument doc(String id, String title, String searchText, EntityType type, long pop, boolean visible) {
    return new IndexDocument(type, id, title, null, searchText, new Popularity(pop), visible, Map.of());
  }

  private IndexDocument docWithPrice(String id, String title, long priceMinor, EntityType type) {
    return new IndexDocument(type, id, title, null, "", Popularity.ZERO, true, Map.of("price_minor", priceMinor));
  }
}
