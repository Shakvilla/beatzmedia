package org.shakvilla.beatzmedia.commerce.domain;

import org.shakvilla.beatzmedia.platform.domain.NotFoundException;

/**
 * Thrown when the referenced catalog item (track/album/etc.) cannot be resolved to a price —
 * either it does not exist or it is not for sale. Maps to HTTP 404. Commerce ADD §4.2
 * ({@code PricingService}) / LLFR-COMMERCE-01.2.
 */
public class PriceUnavailableException extends NotFoundException {

  public PriceUnavailableException(String kind, String refId) {
    super("Price unavailable for " + kind + ":" + refId);
  }
}
