package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.catalog.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/**
 * WU-CAT-1 default stub: returns the track's intrinsic stored {@code ownership} and {@code
 * price_minor} from the catalog table. No per-caller decoration, no cross-module table reads.
 *
 * <p>ADR (WU-CAT-1): commerce/library modules do not exist yet. This stub satisfies the
 * {@link OwnershipReader} contract so the catalog module compiles and passes tests. Once WU-LIB-1
 * and WU-COM-1 ship, their adapters will be wired here and this stub removed. Catalog ADD §2 /
 * §4.2.
 */
@ApplicationScoped
public class StubOwnershipReaderAdapter implements OwnershipReader {

  private final EntityManager em;

  @Inject
  public StubOwnershipReaderAdapter(EntityManager em) {
    this.em = em;
  }

  @Override
  public OwnershipStatus ownership(TrackId trackId, Optional<String> callerId) {
    TrackEntity entity = em.find(TrackEntity.class, trackId.value());
    if (entity == null) {
      return OwnershipStatus.free;
    }
    try {
      return OwnershipStatus.valueOf(entity.ownership.replace('-', '_'));
    } catch (IllegalArgumentException e) {
      return OwnershipStatus.free;
    }
  }

  @Override
  public Optional<Long> priceMinor(TrackId trackId, Optional<String> callerId) {
    TrackEntity entity = em.find(TrackEntity.class, trackId.value());
    if (entity == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(entity.priceMinor);
  }
}
