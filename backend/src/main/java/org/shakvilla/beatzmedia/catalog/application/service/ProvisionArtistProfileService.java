package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.application.port.in.ProvisionArtistProfile;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;

/**
 * Application service for {@link ProvisionArtistProfile}. Creates the {@code artist_profile} shell
 * for a newly-upgraded artist with sensible defaults, so downstream flows (release create/upload,
 * the studio profile page) have a profile row and the {@code release.artist_id} FK resolves.
 * Catalog ADD §10 / WU-CAT-7.
 *
 * <p>Idempotent by construction: skips provisioning when a profile already exists for the id, so a
 * redelivered {@code ArtistUpgraded} event — or an id that already had a profile (e.g. a dev-seed
 * row) — never overwrites curated data. The repository's insert-only {@code saveArtistProfile} is a
 * second guard.
 */
@ApplicationScoped
public class ProvisionArtistProfileService implements ProvisionArtistProfile {

  /** Fallback square image when the upgrading account has no avatar. Matches the catalog default. */
  public static final String DEFAULT_IMAGE = "/images/placeholder.jpg";

  /** Fallback display name when the account carries a blank name (defensive; signup requires one). */
  public static final String DEFAULT_NAME = "Artist";

  private final CatalogRepository repo;

  @Inject
  public ProvisionArtistProfileService(CatalogRepository repo) {
    this.repo = repo;
  }

  @Override
  public void provision(ProvisionCommand command) {
    ArtistId artistId = command.artistId();

    // Idempotency: already provisioned → no-op (never clobber curated profile fields).
    if (repo.findArtist(artistId).isPresent()) {
      return;
    }

    String name =
        (command.name() == null || command.name().isBlank()) ? DEFAULT_NAME : command.name();
    String image =
        (command.image() == null || command.image().isBlank()) ? DEFAULT_IMAGE : command.image();

    ArtistProfile shell = new ArtistProfile(
        artistId,
        name,
        image,
        /* coverImage */ null,
        /* verified */ false,
        /* monthlyListeners */ 0L,
        /* followers */ 0L,
        /* bio */ null,
        /* location */ null,
        /* genres */ List.of(),
        /* shows */ List.of());

    repo.saveArtistProfile(shell);
  }
}
