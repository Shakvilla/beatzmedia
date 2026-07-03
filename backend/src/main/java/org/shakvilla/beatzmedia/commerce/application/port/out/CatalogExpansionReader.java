package org.shakvilla.beatzmedia.commerce.application.port.out;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Output port: resolves a settled order line into the concrete ownable unit ids to grant (INV-2) and
 * the recipient creator for the sale split (INV-4). Read-only, in-process over catalog's own JPA
 * entities — the same cross-module read pattern used by {@code CatalogPricingServiceAdapter} (no
 * cross-module FK, no shared persistence). Commerce ADD §4.2 / §8.
 */
public interface CatalogExpansionReader {

  /**
   * A single for-sale track of an album with its authoritative individual price (minor units) — the
   * ownership-aware basis for {@code album-rest} pricing/granting (finding F2).
   */
  record PurchasableTrack(String trackId, long priceMinor) {}

  /**
   * The track ids a settled line grants ownership of (INV-2):
   *
   * <ul>
   *   <li>{@code track} → the single track id.
   *   <li>{@code album} → every constituent track id of the album (full-album purchase).
   *   <li>{@code album-rest} → only the album's <strong>for-sale</strong> track ids; the per-track
   *       already-owned guard in the grant service then skips any the caller already owns, so the fan
   *       is granted exactly the tracks they paid for (finding F2). Free / authoring-owned tracks are
   *       never granted here.
   * </ul>
   *
   * Returns an empty list if the ref does not resolve (defensive; the checkout gate already rejects
   * non-track/album kinds — G3).
   */
  List<String> tracksToGrant(CartItemKind kind, String refId);

  /**
   * The caller's <strong>remaining purchasable</strong> tracks of an album — the album's {@code
   * for-sale} tracks the caller does not already actively own — each with its authoritative individual
   * price. This is the ownership-aware basis for {@code album-rest} checkout (finding F2): the price
   * charged is the SUM of these prices (individual track prices, NO bundle discount — the discount is
   * a full-album-only, authoring-time concept), and exactly these tracks are granted. Empty if the
   * album is unknown or the caller already owns every for-sale track.
   *
   * @param account the authenticated buyer (ownership is caller-specific)
   * @param albumRefId the album id
   */
  List<PurchasableTrack> remainingForSaleTracks(AccountId account, String albumRefId);

  /**
   * The recipient creator (artist) account id for a settled line, used as the {@code creator_payable}
   * account owner in the 70/30 sale split (INV-4). Resolved from the catalog track/album's artist.
   * Empty if unresolvable (the split is then skipped rather than posted to a fabricated creator).
   */
  Optional<String> creatorOf(CartItemKind kind, String refId);
}
