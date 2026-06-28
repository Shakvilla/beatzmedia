package org.shakvilla.beatzmedia.search.unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.search.application.port.out.SearchIndex;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;
import org.shakvilla.beatzmedia.search.domain.SearchFilters;
import org.shakvilla.beatzmedia.search.domain.SearchHit;
import org.shakvilla.beatzmedia.search.domain.SearchQuery;
import org.shakvilla.beatzmedia.search.domain.SearchResults;

/** In-memory fake for unit tests; no framework dependency. */
class FakeSearchIndex implements SearchIndex {

  /** Key: entityType + "|" + entityId */
  final Map<String, IndexDocument> store = new HashMap<>();
  int upsertCallCount = 0;
  int removeCallCount = 0;

  @Override
  public void upsert(IndexDocument document) {
    upsertCallCount++;
    store.put(key(document.entityType(), document.entityId()), document);
  }

  @Override
  public void remove(EntityType type, String entityId) {
    removeCallCount++;
    store.remove(key(type, entityId));
  }

  @Override
  public SearchResults search(SearchQuery query, SearchFilters filters, PageRequest page) {
    // Simple in-memory match on title contains q
    String q = query.q().toLowerCase();
    List<SearchHit> tracks = new ArrayList<>();
    List<SearchHit> artists = new ArrayList<>();
    List<SearchHit> albums = new ArrayList<>();
    List<SearchHit> playlists = new ArrayList<>();
    List<SearchHit> storeItems = new ArrayList<>();
    List<SearchHit> podcasts = new ArrayList<>();
    List<SearchHit> events = new ArrayList<>();

    for (IndexDocument doc : store.values()) {
      if (!doc.visible()) continue;
      if (!doc.title().toLowerCase().contains(q)) continue;
      double score = doc.title().equalsIgnoreCase(q) ? 1.0 : 0.5;
      SearchHit hit =
          new SearchHit(
              doc.entityType(),
              doc.entityId(),
              doc.title(),
              doc.subtitle(),
              doc.payload(),
              score,
              doc.popularity().score());
      switch (doc.entityType()) {
        case TRACK -> tracks.add(hit);
        case ARTIST -> artists.add(hit);
        case ALBUM -> albums.add(hit);
        case PLAYLIST -> playlists.add(hit);
        case STORE_ITEM -> storeItems.add(hit);
        case PODCAST -> podcasts.add(hit);
        case EVENT -> events.add(hit);
      }
    }

    List<SearchHit> all = new ArrayList<>();
    all.addAll(tracks);
    all.addAll(artists);
    all.addAll(albums);
    all.addAll(playlists);
    all.addAll(storeItems);
    all.addAll(podcasts);
    all.addAll(events);

    Optional<SearchHit> topResult = SearchResults.computeTopResult(all);
    long total = all.size();
    return new SearchResults(
        tracks, artists, albums, playlists, storeItems, podcasts, events, topResult, total);
  }

  private String key(EntityType type, String id) {
    return type.name() + "|" + id;
  }
}
