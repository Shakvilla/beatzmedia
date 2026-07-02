package org.shakvilla.beatzmedia.commerce.application.port.out;

import java.util.Map;

import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;

/**
 * Output port: server-side price/title/image resolution for a cart line. Never trusts the client
 * (INV-2, INV-11). Commerce ADD §4.2.
 *
 * <p>{@code track}/{@code album}/{@code album-rest} resolve authoritatively from the catalog
 * module (in-process adapter over catalog's own JPA entities, mirroring the established
 * cross-module read pattern used by {@code library.adapter.out.persistence.CatalogReaderAdapter} —
 * no cross-module FK, same schema). {@code episode}/{@code season-pass}/{@code ticket}/{@code store}
 * have no backing module yet (podcasts/events/store ship in Phase 4, WU-POD-1/WU-EVT-1/WU-STO-1);
 * for those kinds the adapter accepts caller-supplied display/price metadata as a documented
 * interim measure until the owning module exists.
 *
 * <p>Throws {@link org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException} when the
 * refId does not resolve to a priced, purchasable item.
 */
public interface PricingService {

  PricedItem priceFor(CartItemKind kind, String refId, Map<String, Object> metadata);
}
