package org.shakvilla.beatzmedia.store.fakes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.store.application.port.in.ListStore.StoreQuery;
import org.shakvilla.beatzmedia.store.application.port.out.StoreRepository;
import org.shakvilla.beatzmedia.store.domain.StoreItem;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;
import org.shakvilla.beatzmedia.store.domain.StoreSort;

/** In-memory fake for {@link StoreRepository} used in unit tests. */
public class FakeStoreRepository implements StoreRepository {

  private final Map<String, StoreItem> items = new LinkedHashMap<>();

  public FakeStoreRepository withItem(StoreItem item) {
    items.put(item.id().value(), item);
    return this;
  }

  @Override
  public Page<StoreItem> find(StoreQuery query, StoreSort sort, PageRequest page) {
    List<StoreItem> filtered =
        items.values().stream()
            .filter(i -> query.type().isEmpty() || i.type() == query.type().get())
            .filter(i -> query.genre().isEmpty() || query.genre().equals(i.genre()))
            .sorted(comparator(sort))
            .toList();
    int from = Math.min(page.offset(), filtered.size());
    int to = Math.min(from + page.size(), filtered.size());
    return Page.of(new ArrayList<>(filtered.subList(from, to)), page.page(), page.size(), filtered.size());
  }

  @Override
  public Optional<StoreItem> findById(StoreItemId id) {
    return Optional.ofNullable(items.get(id.value()));
  }

  @Override
  public void decrementStock(StoreItemId id, int qty) {
    if (qty <= 0) {
      return;
    }
    StoreItem current = items.get(id.value());
    if (current == null || current.stockRemaining().isEmpty()) {
      return;
    }
    // Mirrors JpaStoreRepository's atomic conditional UPDATE (WHERE stock_remaining >= qty):
    // a decrement that would exceed remaining stock is rejected outright (never partially
    // applied / clamped) — INV-STORE-C. In practice qty is always 1 (one grant per call).
    int remaining = current.stockRemaining().get();
    if (remaining < qty) {
      return;
    }
    int newStock = remaining - qty;
    items.put(
        id.value(),
        new StoreItem(
            current.id(),
            current.type(),
            current.title(),
            current.artistName(),
            current.artistId().orElse(null),
            current.image(),
            current.priceMinor(),
            current.currency(),
            current.genre().orElse(null),
            current.badges(),
            current.description().orElse(null),
            current.popularity().orElse(null),
            current.createdAt(),
            current.licenseOptions(),
            current.variants(),
            current.quality().orElse(null),
            current.dropsAt().orElse(null),
            newStock));
  }

  private static Comparator<StoreItem> comparator(StoreSort sort) {
    return switch (sort) {
      case POPULAR ->
          Comparator.<StoreItem, Integer>comparing(
                  i -> i.popularity().orElse(Integer.MIN_VALUE), Comparator.reverseOrder())
              .thenComparing(i -> i.id().value());
      case NEWEST ->
          Comparator.<StoreItem, java.time.Instant>comparing(StoreItem::createdAt, Comparator.reverseOrder())
              .thenComparing(i -> i.id().value());
      case PRICE_ASC ->
          Comparator.<StoreItem>comparingLong(StoreItem::priceMinor).thenComparing(i -> i.id().value());
      case PRICE_DESC ->
          Comparator.<StoreItem>comparingLong(StoreItem::priceMinor)
              .reversed()
              .thenComparing(i -> i.id().value());
    };
  }
}
