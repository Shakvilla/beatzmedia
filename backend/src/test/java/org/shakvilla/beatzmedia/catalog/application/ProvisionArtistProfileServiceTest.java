package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.in.ProvisionArtistProfile.ProvisionCommand;
import org.shakvilla.beatzmedia.catalog.application.service.ProvisionArtistProfileService;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;

/**
 * Unit tests for {@link ProvisionArtistProfileService} (WU-CAT-7). Uses the in-memory
 * {@link FakeCatalogRepository}. Verifies defaults, avatar propagation, and idempotency.
 */
class ProvisionArtistProfileServiceTest {

  private FakeCatalogRepository repo;
  private ProvisionArtistProfileService service;

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    service = new ProvisionArtistProfileService(repo);
  }

  @Test
  void provision_creates_profile_shell_with_account_name_and_avatar() {
    ArtistId id = new ArtistId("019f6179-6f08-7b64-bf5a-4a97b5205ec4");

    service.provision(new ProvisionCommand(id, "Real Artist", "https://cdn/x/avatar.jpg"));

    ArtistProfile p = repo.findArtist(id).orElseThrow();
    assertEquals("Real Artist", p.getName());
    assertEquals("https://cdn/x/avatar.jpg", p.getImage());
    assertFalse(p.isVerified(), "new artists are unverified");
    assertEquals(0L, p.getMonthlyListeners().longValue());
    assertEquals(0L, p.getFollowers().longValue());
    assertEquals(List.of(), p.getGenres());
    assertEquals(List.of(), p.getShows());
  }

  @Test
  void provision_falls_back_to_placeholder_image_when_avatar_blank() {
    ArtistId id = new ArtistId("acc-no-avatar");

    service.provision(new ProvisionCommand(id, "No Avatar", "  "));

    assertEquals(
        ProvisionArtistProfileService.DEFAULT_IMAGE, repo.findArtist(id).orElseThrow().getImage());
  }

  @Test
  void provision_falls_back_to_default_name_when_name_blank() {
    ArtistId id = new ArtistId("acc-no-name");

    service.provision(new ProvisionCommand(id, null, "https://cdn/x/avatar.jpg"));

    assertEquals(
        ProvisionArtistProfileService.DEFAULT_NAME, repo.findArtist(id).orElseThrow().getName());
  }

  @Test
  void provision_is_idempotent_and_never_clobbers_existing_profile() {
    ArtistId id = new ArtistId("acc-existing");
    ArtistProfile curated = new ArtistProfile(
        id, "Curated Name", "https://cdn/curated.jpg", "https://cdn/cover.jpg",
        true, 2_400_000L, 2_400_000L, "A curated bio", "Accra, Ghana",
        List.of("Drill", "Hiplife"), List.of());
    repo.addArtist(curated);

    // A redelivered / duplicate event must not overwrite the curated fields.
    service.provision(new ProvisionCommand(id, "Different Name", "https://cdn/other.jpg"));

    ArtistProfile p = repo.findArtist(id).orElseThrow();
    assertEquals("Curated Name", p.getName());
    assertEquals("https://cdn/curated.jpg", p.getImage());
    assertTrue(p.isVerified());
    assertEquals(2_400_000L, p.getMonthlyListeners().longValue());
    assertEquals(List.of("Drill", "Hiplife"), p.getGenres());
  }
}
