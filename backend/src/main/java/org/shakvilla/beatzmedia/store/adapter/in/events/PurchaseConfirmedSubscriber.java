package org.shakvilla.beatzmedia.store.adapter.in.events;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.commerce.domain.OwnershipGranted;
import org.shakvilla.beatzmedia.store.application.port.out.StoreRepository;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;

/**
 * CDI event observer that reacts to a settled purchase to decrement {@code stock_remaining} on
 * EXCLUSIVE/MERCH store items (INV-STORE-C floor). Store ADD §5.2 / §9.
 *
 * <h3>Why {@link OwnershipGranted}, not a {@code PurchaseConfirmed} event</h3>
 *
 * No {@code PurchaseConfirmed} domain event exists in {@code commerce.domain}. The actual
 * after-commit, order-settled signal commerce publishes is {@link OwnershipGranted}
 * (fired {@code AFTER_SUCCESS}, exactly once per settled order — see {@code
 * GrantOwnershipService}, guarded by its {@code order_grant_posting} exactly-once claim). That is
 * this subscriber's real integration seam, matching the same cross-module event-reaction pattern
 * used elsewhere (e.g. {@code analytics.adapter.in.events.SaleRecordedObserver}, {@code
 * notifications.adapter.in.events.NotificationEventObservers}): react ONLY to the event payload,
 * never read another module's table directly.
 *
 * <p><strong>Current scope note.</strong> {@code commerce.CheckoutService} presently gates {@code
 * kind=store} checkout ({@code CheckoutKindUnsupportedException}, its own G3 gate) until the
 * owning module ships an authoritative price port — so {@code trackIds}/{@code episodeIds} on
 * {@link OwnershipGranted} will not yet carry store item ids in production. This subscriber is
 * wired now, forward-compatible: it treats every granted unit id as a possible store item id and
 * is a safe no-op for ids that are not one (or that carry no stock tracking).
 *
 * <p><strong>Idempotency (INV-STORE-C / "keyed by orderId").</strong> {@link OwnershipGranted}
 * itself fires at most once per order (the exactly-once claim lives upstream in commerce), so a
 * redelivery of the same order's grant cannot occur from this in-process CDI event. The floor
 * itself is additionally enforced atomically in {@link StoreRepository#decrementStock} (a
 * conditional {@code UPDATE ... WHERE stock_remaining >= qty}), so even a hypothetical duplicate
 * delivery can never drive stock below zero.
 */
@ApplicationScoped
public class PurchaseConfirmedSubscriber {

  private final StoreRepository storeRepository;

  @Inject
  public PurchaseConfirmedSubscriber(StoreRepository storeRepository) {
    this.storeRepository = storeRepository;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onOwnershipGranted(@Observes(during = TransactionPhase.AFTER_SUCCESS) OwnershipGranted event) {
    List<String> grantedUnitIds = new ArrayList<>();
    grantedUnitIds.addAll(event.trackIds());
    grantedUnitIds.addAll(event.episodeIds());
    for (String unitId : grantedUnitIds) {
      StoreItemId id = new StoreItemId(unitId);
      storeRepository.findById(id).ifPresent(item -> {
        if (item.stockRemaining().isPresent()) {
          storeRepository.decrementStock(id, 1);
        }
      });
    }
  }
}
