package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a {@link ReleaseTrack}'s {@code priceMinor} is negative, or exceeds the sane upper
 * bound ({@link ReleaseTrack#MAX_PRICE_MINOR}), guarding INV-5 (list-price computation) / INV-11
 * (authoritative money in integer minor units — never trust an unbounded client-supplied value).
 * Maps to 422 {@code INVALID_PRICE}.
 *
 * <p>Catalog owns its own copy of this signal rather than importing another module's concrete
 * exception class — the inbound adapter must not reach into another module's private domain
 * (hexagonal rule). The wire {@code code} is identical ({@code INVALID_PRICE}) because both carry
 * the same kernel {@link ErrorCode}, mirroring {@code studio.domain.InvalidPriceException}
 * (podcast {@code Episode}). WU-CAT-5.
 */
public class InvalidPriceException extends DomainException {

  public InvalidPriceException(String message) {
    super(ErrorCode.INVALID_PRICE, message, "priceMinor");
  }
}
