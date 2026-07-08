package org.shakvilla.beatzmedia.store.adapter.out.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.search.application.port.in.IndexEntityUseCase;
import org.shakvilla.beatzmedia.search.application.port.in.QueryService;
import org.shakvilla.beatzmedia.search.domain.EntityType;
import org.shakvilla.beatzmedia.search.domain.IndexDocument;
import org.shakvilla.beatzmedia.search.domain.Popularity;
import org.shakvilla.beatzmedia.search.domain.SearchFilters;
import org.shakvilla.beatzmedia.search.domain.SearchQuery;
import org.shakvilla.beatzmedia.search.domain.SearchResults;
import org.shakvilla.beatzmedia.store.application.port.out.SearchIndex;
import org.shakvilla.beatzmedia.store.domain.Genre;
import org.shakvilla.beatzmedia.store.domain.StoreItem;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;
import org.shakvilla.beatzmedia.store.domain.StoreItemType;
import org.shakvilla.beatzmedia.store.domain.StoreSort;

/**
 * Thin outbound adapter over the {@code search} module's shared FTS/{@code pg_trgm} index (WU-SRCH-1).
 * Never touches the {@code search_document} table directly — it calls the search module's
 * published <strong>input</strong> ports ({@link QueryService}, {@link IndexEntityUseCase}), the
 * same cross-module pattern {@code catalog.application.service.SearchService} already uses
 * (hexagonal dependency rule: talk to another module through its input port). Store ADD §4.2 /
 * §5.2.
 */
@ApplicationScoped
public class SearchIndexPg implements SearchIndex {

  private final QueryService queryService;
  private final IndexEntityUseCase indexEntityUseCase;

  @Inject
  public SearchIndexPg(QueryService queryService, IndexEntityUseCase indexEntityUseCase) {
    this.queryService = queryService;
    this.indexEntityUseCase = indexEntityUseCase;
  }

  @Override
  public Page<StoreItemId> query(
      String text, Optional<StoreItemType> type, Optional<Genre> genre, StoreSort sort, PageRequest page) {
    SearchFilters filters =
        new SearchFilters(type.map(StoreItemType::name), genre.map(Genre::wireValue), toSearchSort(sort));
    SearchQuery searchQuery =
        new SearchQuery(text, org.shakvilla.beatzmedia.search.domain.SearchScope.STORE_ITEM, filters, page);
    SearchResults results = queryService.search(searchQuery);
    List<StoreItemId> ids = results.storeItems().stream().map(hit -> new StoreItemId(hit.entityId())).toList();
    return Page.of(ids, page.page(), page.size(), results.total());
  }

  @Override
  public void index(StoreItem item) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("image", item.image());
    payload.put("price_minor", item.priceMinor());
    payload.put(
        "price_amount",
        BigDecimal.valueOf(item.priceMinor()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    payload.put("price_currency", item.currency().name());
    payload.put("type", item.type().name());
    item.genre().ifPresent(g -> payload.put("genre", g.wireValue()));
    item.quality().ifPresent(q -> payload.put("quality", q));

    IndexDocument document =
        IndexDocument.of(
            EntityType.STORE_ITEM,
            item.id().value(),
            item.title(),
            item.title() + " " + item.artistName(),
            new Popularity(item.popularity().map(Integer::longValue).orElse(0L)),
            true,
            payload);
    indexEntityUseCase.index(document);
  }

  @Override
  public void remove(StoreItemId id) {
    indexEntityUseCase.deindex(EntityType.STORE_ITEM, id.value());
  }

  private static org.shakvilla.beatzmedia.search.domain.Sort toSearchSort(StoreSort sort) {
    return switch (sort) {
      case POPULAR -> org.shakvilla.beatzmedia.search.domain.Sort.POPULAR;
      case NEWEST -> org.shakvilla.beatzmedia.search.domain.Sort.NEWEST;
      case PRICE_ASC -> org.shakvilla.beatzmedia.search.domain.Sort.PRICE_ASC;
      case PRICE_DESC -> org.shakvilla.beatzmedia.search.domain.Sort.PRICE_DESC;
    };
  }
}
