package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.in.SearchResultsView;
import org.shakvilla.beatzmedia.catalog.application.service.SearchService;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.MissingQueryException;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.catalog.fakes.FakeOwnershipReader;
import org.shakvilla.beatzmedia.search.application.port.in.QueryService;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.SearchHit;
import org.shakvilla.beatzmedia.search.domain.SearchQuery;
import org.shakvilla.beatzmedia.search.domain.SearchResults;

/** Unit test for LLFR-CATALOG-01.2 (search read). Uses fake ports; no framework. */
@Tag("unit")
class SearchServiceTest {

  private FakeCatalogRepository repo;
  private FakeOwnershipReader ownershipReader;
  private FakeQueryService queryService;
  private SearchService service;

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    ownershipReader = new FakeOwnershipReader();
    queryService = new FakeQueryService();
    service = new SearchService(queryService, repo, ownershipReader);
  }

  @Test
  void search_hydrates_track_hits_from_catalog_repository() {
    Track track = sampleTrack("kwaku-the-traveller");
    repo.addTrack(track);
    queryService.setResults(new SearchResults(
        List.of(new SearchHit(EntityType.TRACK, "kwaku-the-traveller", "Kwaku The Traveller",
            "Black Sherif", Map.of(), 0.9, 1000L)),
        List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(),
        Optional.empty(), 1L));

    SearchResultsView view = service.search("kwaku", Optional.empty());

    assertEquals(1, view.tracks().size());
    assertEquals("kwaku-the-traveller", view.tracks().get(0).id());
  }

  @Test
  void search_blank_query_throws_missing_query_exception() {
    assertThrows(MissingQueryException.class, () -> service.search("  ", Optional.empty()));
  }

  @Test
  void search_null_query_throws_missing_query_exception() {
    assertThrows(MissingQueryException.class, () -> service.search(null, Optional.empty()));
  }

  @Test
  void search_maps_top_result_when_present() {
    Track track = sampleTrack("soja");
    repo.addTrack(track);
    SearchHit topHit = new SearchHit(EntityType.TRACK, "soja", "Soja", "Sarkodie",
        Map.of(), 0.99, 2000L);
    queryService.setResults(new SearchResults(
        List.of(topHit), List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(),
        Optional.of(topHit), 1L));

    SearchResultsView view = service.search("soja", Optional.empty());

    assertTrue(view.topResult().isPresent());
    assertEquals("soja", view.topResult().get().entityId());
    assertEquals("TRACK", view.topResult().get().entityType());
  }

  private Track sampleTrack(String id) {
    return new Track(
        new TrackId(id), "Title " + id,
        new ArtistId("artist-1"), "Artist One",
        null, null,
        200, "https://img.test/cover.jpg",
        OwnershipStatus.free, null, 500L, null, null, null, 2023, "ready");
  }

  /** Minimal in-memory stub for QueryService. */
  private static class FakeQueryService implements QueryService {
    private SearchResults results = SearchResults.empty();

    void setResults(SearchResults results) {
      this.results = results;
    }

    @Override
    public SearchResults search(SearchQuery query) {
      return results;
    }
  }
}
