package org.shakvilla.beatzmedia.catalog.application.port.out;

import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/**
 * Output port for per-caller ownership and price decoration of tracks. The commerce/library
 * modules will implement this once built (WU-COM-* / WU-LIB-*). For WU-CAT-1 this port has an
 * in-catalog stub implementation ({@link
 * org.shakvilla.beatzmedia.catalog.adapter.out.persistence.StubOwnershipReaderAdapter}) that
 * returns the track's intrinsic stored ownership and price — no cross-module tables are read.
 *
 * <p>ADR decision (WU-CAT-1): the stub is the default adapter until commerce/library exist;
 * per-caller pricing decoration will be wired in a later WU. Catalog ADD §2 / §4.2.
 */
public interface OwnershipReader {

  /**
   * Returns the effective ownership status for the given track and caller. Anonymous callers
   * (empty {@code callerId}) receive the intrinsic stored ownership.
   *
   * @param trackId the track to check
   * @param callerId the calling account id (empty = anonymous)
   * @return ownership status; never null
   */
  OwnershipStatus ownership(TrackId trackId, Optional<String> callerId);

  /**
   * Returns the effective price in minor units for the given track and caller, or empty if the
   * track is free/owned.
   *
   * @param trackId the track to check
   * @param callerId the calling account id (empty = anonymous)
   * @return price in pesewas, or empty if no price applies
   */
  Optional<Long> priceMinor(TrackId trackId, Optional<String> callerId);
}
