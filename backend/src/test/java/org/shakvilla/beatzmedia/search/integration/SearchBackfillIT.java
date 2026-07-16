package org.shakvilla.beatzmedia.search.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.search.application.port.in.QueryService;
import org.shakvilla.beatzmedia.search.application.port.in.ReindexUseCase;
import org.shakvilla.beatzmedia.search.domain.SearchQuery;
import org.shakvilla.beatzmedia.search.domain.SearchResults;

import io.quarkus.test.junit.QuarkusTest;

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
