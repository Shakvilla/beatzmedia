package org.shakvilla.beatzmedia.store.application.port.out;

import java.util.Optional;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.store.application.port.in.ListStore.StoreQuery;
import org.shakvilla.beatzmedia.store.domain.StoreItem;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;
import org.shakvilla.beatzmedia.store.domain.StoreSort;

/**
 * Output port for {@code store_item}/{@code license_option}/{@code merch_variant} persistence.
 * Implemented by {@code StoreRepositoryJpa} (Hibernate); maps JPA entity ↔ domain, eager-fetching
 * license options / merch variants on detail. Store ADD §4.2.
 */
public interface StoreRepository {

  /** Filter + sort + paginate the catalog feed (LLFR-STORE-01.1). Each sort is index-backed (§7). */
  Page<StoreItem> find(StoreQuery query, StoreSort sort, PageRequest page);

  /** Product detail with type-specific children eagerly loaded (LLFR-STORE-01.2). */
  Optional<StoreItem> findById(StoreItemId id);

  /**
   * Decrement {@code stock_remaining} by {@code qty}, floor-guarded at zero (INV-STORE-C): applied
   * atomically only when {@code stock_remaining >= qty}, so it is never partially applied — an
   * item with no stock tracking (not EXCLUSIVE/MERCH) or with insufficient remaining stock is a
   * silent no-op, never negative. Invoked from the {@code PurchaseConfirmedSubscriber}.
   */
  void decrementStock(StoreItemId id, int qty);
}
