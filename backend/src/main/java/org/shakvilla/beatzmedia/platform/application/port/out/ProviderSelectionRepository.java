package org.shakvilla.beatzmedia.platform.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.platform.domain.ProviderCapability;
import org.shakvilla.beatzmedia.platform.domain.ProviderCode;
import org.shakvilla.beatzmedia.platform.domain.ProviderSelection;

/** Persistence port for versioned provider selections. */
public interface ProviderSelectionRepository {
  List<ProviderSelection> listAll();

  Optional<ProviderSelection> updateIfVersion(
      ProviderCapability capability,
      ProviderCode provider,
      long expectedVersion,
      Instant updatedAt,
      String updatedBy);
}
