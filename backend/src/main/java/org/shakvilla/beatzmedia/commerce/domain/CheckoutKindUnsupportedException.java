package org.shakvilla.beatzmedia.commerce.domain;

import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when checkout contains a cart line whose authoritative pricing module does not exist yet
 * (episode / season-pass / ticket / store — WU-POD-1 / WU-EVT-1 / WU-STO-1 are Phase-4). Maps to HTTP
 * 409 {@code CHECKOUT_KIND_UNSUPPORTED}.
 *
 * <p><strong>G3 security decision (SAFE default, ADR-23).</strong> WU-COM-2 does NOT depend on those
 * modules, so their prices would come from the interim {@code CatalogPricingServiceAdapter}
 * metadata-echo — i.e. client-supplied and spoofable. Rather than charge real money on a price the
 * server cannot vouch for, checkout GATES/REJECTS those kinds until the owning module ships an
 * authoritative price-lookup input port (// G2). Only {@code track}/{@code album}/{@code album-rest}
 * (priced authoritatively from catalog) may be checked out today. See the commerce ADD §9 and ADR-23.
 */
public class CheckoutKindUnsupportedException extends ConflictException {

  public CheckoutKindUnsupportedException(String kind) {
    super(
        ErrorCode.CHECKOUT_KIND_UNSUPPORTED,
        "Checkout is not yet supported for '"
            + kind
            + "' items — authoritative pricing for this kind is not available yet");
  }
}
