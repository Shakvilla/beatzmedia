package org.shakvilla.beatzmedia.commerce.application.port.out;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;

/**
 * Output port: resolves a settled order line into the concrete ownable unit ids to grant (INV-2) and
 * the recipient creator for the sale split (INV-4). Read-only, in-process over catalog's own JPA
 * entities — the same cross-module read pattern used by {@code CatalogPricingServiceAdapter} (no
 * cross-module FK, no shared persistence). Commerce ADD §4.2 / §8.
 */
public interface CatalogExpansionReader {

  /**
   * The track ids a settled line grants ownership of (INV-2):
   *
   * <ul>
   *   <li>{@code track} → the single track id.
   *   <li>{@code album} / {@code album-rest} → every constituent track id of the album.
   * </ul>
   *
   * Returns an empty list if the ref does not resolve (defensive; the checkout gate already rejects
   * non-track/album kinds — G3).
   */
  List<String> tracksToGrant(CartItemKind kind, String refId);

  /**
   * The recipient creator (artist) account id for a settled line, used as the {@code creator_payable}
   * account owner in the 70/30 sale split (INV-4). Resolved from the catalog track/album's artist.
   * Empty if unresolvable (the split is then skipped rather than posted to a fabricated creator).
   */
  Optional<String> creatorOf(CartItemKind kind, String refId);
}
