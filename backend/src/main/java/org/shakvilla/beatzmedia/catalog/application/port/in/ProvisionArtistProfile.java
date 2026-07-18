package org.shakvilla.beatzmedia.catalog.application.port.in;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/**
 * Input port: provision the {@code artist_profile} shell for a newly-upgraded artist. Driven by the
 * catalog reactor to identity's {@code ArtistUpgraded} domain event (WU-CAT-7). Idempotent — a
 * repeat call for an already-provisioned artist is a no-op, so it never clobbers curated profile
 * data. Catalog ADD §10.
 *
 * <p><strong>Hexagonal rule:</strong> catalog owns the {@code artist_profile} table and reacts to
 * the identity event; identity never writes catalog tables. This port is the seam the {@code
 * ArtistUpgraded} observer drives.
 */
public interface ProvisionArtistProfile {

  void provision(ProvisionCommand command);

  /**
   * Command carrying the identity attributes needed to seed the profile shell. {@code image} is the
   * account avatar (may be null/blank — the service falls back to a placeholder so the NOT NULL
   * {@code artist_profile.image} column is always satisfied).
   */
  record ProvisionCommand(ArtistId artistId, String name, String image) {}
}
