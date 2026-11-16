package org.shakvilla.beatzmedia.platform.application.port.in;

import java.time.Instant;
import java.util.List;

/** Reads and changes the provider selected for each external capability. */
public interface ManageProviderSelections {
  List<ProviderSelectionView> list();

  ProviderSelectionView change(
      String actorId, String capability, String provider, long expectedVersion);

  record ProviderSelectionView(
      String capability,
      String provider,
      long version,
      boolean ready,
      String readinessReason,
      Instant updatedAt,
      String updatedBy) {}
}
