package org.shakvilla.beatzmedia.commerce.application.port.out;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Outbound SPI: an owning module (podcasts/events/store) resolves the settlement concerns for one
 * cart {@code entityType} that commerce cannot resolve from catalog — the recipient <em>payee</em>
 * for the 70/30 split (INV-4), the ownable unit ids commerce must grant, and any owning-module side
 * effect (ticket mint, store stock). Declared by commerce and implemented by the owning module, so
 * the only new edge is {@code module → commerce} (mirrors {@link ModulePriceSource} and the WU-SRCH-2
 * {@code IndexSource} pattern) — no cycle. WU-COM-4.
 *
 * <p>Invoked only from {@code GrantOwnershipService} on a settled order, inside its exactly-once
 * grant claim. Implementations resolve from their own module's persisted data.
 */
public interface SettlementSource {

  /** The {@link org.shakvilla.beatzmedia.commerce.domain.CartItemKind#wireValue()} this source settles. */
  String entityType();

  /**
   * The creator/artist/seller account to credit in the 70/30 sale split for {@code refId}. Empty when
   * the owning entity has no attributable payee — the caller must NOT silently drop the gross; the
   * checkout-time guard (WU-COM-4) already prevents an un-payable item from being charged, so an empty
   * result at settlement is an integrity anomaly the caller audits rather than a normal path.
   */
  Optional<AccountId> payee(String refId);

  /**
   * The episode ids this settled line grants ownership of (commerce writes one {@code
   * OwnershipGrant.forEpisode} per id): a single episode for {@code episode}, every current episode of
   * the show for {@code season-pass} (album-like expansion, INV-2). Empty for {@code ticket}/{@code
   * store}, whose fulfillment is a module side effect via {@link #fulfill}, not an ownership grant.
   */
  default List<String> ownedEpisodeIds(String refId) {
    return List.of();
  }

  /**
   * Perform the owning-module settlement side effect for a settled line — mint the ticket(s) ({@code
   * events.IssueTicket}), decrement store stock. No-op for {@code episode}/{@code season-pass} (commerce
   * writes those grants directly). Runs inside the commerce grant transaction and MUST be idempotent
   * (the module carries its own exactly-once guard, e.g. {@code ticketExistsForOrderTier}).
   */
  default void fulfill(SettlementContext ctx) {}
}
