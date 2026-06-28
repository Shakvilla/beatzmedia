package org.shakvilla.beatzmedia.search.domain;

import java.util.List;
import java.util.Optional;

/**
 * Grouped search results shaped to the {@code /search} contract (ADD §3, §5).
 * {@code topResult} = the single highest-scored hit across all groups, tie-broken by popularity.
 * Catalog/store resources hydrate each hit's {@code payload} into the frontend type before returning.
 */
public record SearchResults(
    List<SearchHit> tracks,
    List<SearchHit> artists,
    List<SearchHit> albums,
    List<SearchHit> playlists,
    List<SearchHit> storeItems,
    List<SearchHit> podcasts,
    List<SearchHit> events,
    Optional<SearchHit> topResult,
    long total) {

  public SearchResults {
    tracks = tracks == null ? List.of() : List.copyOf(tracks);
    artists = artists == null ? List.of() : List.copyOf(artists);
    albums = albums == null ? List.of() : List.copyOf(albums);
    playlists = playlists == null ? List.of() : List.copyOf(playlists);
    storeItems = storeItems == null ? List.of() : List.copyOf(storeItems);
    podcasts = podcasts == null ? List.of() : List.copyOf(podcasts);
    events = events == null ? List.of() : List.copyOf(events);
    if (topResult == null) topResult = Optional.empty();
  }

  public static SearchResults empty() {
    return new SearchResults(
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        Optional.empty(), 0L);
  }

  /** Compute topResult as the highest-scored hit across all groups, tie-broken by popularity. */
  public static Optional<SearchHit> computeTopResult(List<SearchHit> allHits) {
    return allHits.stream()
        .max(
            java.util.Comparator.comparingDouble(SearchHit::score)
                .thenComparingLong(SearchHit::popularity));
  }
}
