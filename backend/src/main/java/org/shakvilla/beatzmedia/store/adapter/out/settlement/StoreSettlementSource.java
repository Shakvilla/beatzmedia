package org.shakvilla.beatzmedia.store.adapter.out.settlement;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.application.port.out.SettlementContext;
import org.shakvilla.beatzmedia.commerce.application.port.out.SettlementSource;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.store.application.port.out.StoreRepository;
import org.shakvilla.beatzmedia.store.domain.StoreItem;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;

/**
 * Settlement for a purchased {@code store} item (WU-COM-4): the payee is the item's seller ({@code
 * store_item.artist_id}); fulfillment decrements stock by the real line quantity via the atomic
 * floor-guarded {@code decrementStock} (a no-op for items with no stock tracking, INV-STORE-C). A
 * store purchase is recorded by the order + stock decrement + split — there is no {@code
 * ownership_grant} row for store (not a track/episode), and a store-library entitlement surface is
 * out of scope. The {@code refId} may carry a display note ({@code item-1:M}) which is stripped.
 *
 * <p>The dormant {@code store.PurchaseConfirmedSubscriber} (which decrements stock off {@code
 * OwnershipGranted}) never touches store items — commerce only emits track/episode ids on that event
 * — so there is no double-decrement with this source's authoritative, qty-correct path.
 */
@ApplicationScoped
public class StoreSettlementSource implements SettlementSource {

  private final StoreRepository repository;

  @Inject
  public StoreSettlementSource(StoreRepository repository) {
    this.repository = repository;
  }

  @Override
  public String entityType() {
    return "store";
  }

  @Override
  public Optional<AccountId> payee(String refId) {
    return repository
        .findById(new StoreItemId(stripNote(refId)))
        .flatMap(StoreItem::artistId)
        .map(AccountId::new);
  }

  @Override
  public void fulfill(SettlementContext ctx) {
    repository.decrementStock(new StoreItemId(stripNote(ctx.refId())), ctx.qty());
  }

  /** Strip a trailing display note (size/tier label) — the id is everything before the first colon. */
  private static String stripNote(String refId) {
    int colon = refId == null ? -1 : refId.indexOf(':');
    return colon < 0 ? refId : refId.substring(0, colon);
  }
}
