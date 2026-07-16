package org.shakvilla.beatzmedia.commerce.application.port.out;

import java.util.Map;

/**
 * Outbound SPI: an owning module (podcasts/events/store) supplies the authoritative price for one
 * cart {@code entityType}. Declared by commerce and implemented by the owning module, so the only
 * new dependency edge is {@code module -> commerce} (the same direction as the existing
 * {@code podcasts -> commerce} ownership edge and the {@code store -> commerce} event-subscriber
 * edge) — no cycle. Mirrors the WU-SRCH-2 {@code IndexSource} pattern (search declares, modules
 * contribute). WU-COM-4.
 *
 * <p>Implementations MUST resolve the price from the module's own persisted data and never trust
 * client-supplied {@code metadata} for the amount (INV-11); {@code metadata} may be consulted only
 * to select among the module's own priced options (e.g. a store {@code licenseTier}).
 */
public interface ModulePriceSource {

  /** The {@link org.shakvilla.beatzmedia.commerce.domain.CartItemKind#wireValue()} this source prices, e.g. {@code "episode"}. */
  String entityType();

  /**
   * Authoritative price + display fields for {@code refId}. Throws {@link
   * org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException} when the item does not exist
   * or is not purchasable.
   */
  PricedItem price(String refId, Map<String, Object> metadata);
}
