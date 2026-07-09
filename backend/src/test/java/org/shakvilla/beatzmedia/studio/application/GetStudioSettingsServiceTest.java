package org.shakvilla.beatzmedia.studio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioSettingsView;
import org.shakvilla.beatzmedia.studio.application.service.GetStudioSettingsService;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.NotificationPrefs;
import org.shakvilla.beatzmedia.studio.domain.PayoutSettings;
import org.shakvilla.beatzmedia.studio.domain.PrivacySettings;
import org.shakvilla.beatzmedia.studio.domain.StudioDefaults;
import org.shakvilla.beatzmedia.studio.domain.StudioSettings;
import org.shakvilla.beatzmedia.studio.domain.TeamMember;
import org.shakvilla.beatzmedia.studio.domain.TeamRole;
import org.shakvilla.beatzmedia.studio.fakes.FakeAccountReader;
import org.shakvilla.beatzmedia.studio.fakes.FakeStudioRepository;

/** Unit tests for {@link GetStudioSettingsService} — LLFR-STUDIO-04.2 (studio settings read). */
@Tag("unit")
class GetStudioSettingsServiceTest {

  private static final ArtistId ARTIST = new ArtistId("artist-1");

  @Test
  void get_neverConfigured_returnsBlankCategoryAAndHonestCategoryBDefaults() {
    FakeStudioRepository repo = new FakeStudioRepository();
    GetStudioSettingsService service =
        new GetStudioSettingsService(repo, FakeAccountReader.of("artist@example.com"));

    StudioSettingsView view = service.get(ARTIST);

    assertEquals("artist@example.com", view.email());
    assertEquals("", view.phone());
    assertEquals("", view.country());
    assertEquals("English", view.language());
    assertEquals("GMT (Accra)", view.timezone());
    assertFalse(view.twoFactor());
    assertTrue(view.sessions().isEmpty());
    assertTrue(view.connectedApps().isEmpty());
    assertTrue(view.verification().artist()); // real: @RolesAllowed("artist") already gated this.
    assertFalse(view.verification().identity());
    assertFalse(view.verification().payout());
    assertFalse(view.verification().rights());
    assertEquals("Free", view.billing().plan());
    assertEquals(BigDecimal.ZERO, view.billing().price());
    assertFalse(view.notifications().sales());
    assertEquals(BigDecimal.valueOf(0, 2), view.defaults().trackPrice());
    assertEquals("public", view.defaults().releaseVisibility());
    assertTrue(view.team().isEmpty());
  }

  @Test
  void get_previouslySaved_returnsPersistedCategoryAFields() {
    FakeStudioRepository repo = new FakeStudioRepository().withSettings(new StudioSettings(
        ARTIST,
        new NotificationPrefs(true, true, false, false, false, false, false),
        new StudioDefaults(Money.ofCedis(BigDecimal.valueOf(2.5)), "scheduled", true, false),
        new PayoutSettings(true, Money.ofCedis(BigDecimal.valueOf(50)), "TIN-1"),
        new PrivacySettings(true, false, true, true),
        List.of(new TeamMember("u1", "Owner Name", "owner@example.com", TeamRole.OWNER)),
        Instant.parse("2026-06-01T00:00:00Z")));
    GetStudioSettingsService service =
        new GetStudioSettingsService(repo, FakeAccountReader.of("artist@example.com"));

    StudioSettingsView view = service.get(ARTIST);

    assertTrue(view.notifications().sales());
    assertTrue(view.notifications().tips());
    assertFalse(view.notifications().followers());
    assertEquals(BigDecimal.valueOf(250, 2), view.defaults().trackPrice());
    assertEquals("scheduled", view.defaults().releaseVisibility());
    assertTrue(view.payouts().autoWithdraw());
    assertEquals(BigDecimal.valueOf(5000, 2), view.payouts().autoWithdrawThreshold());
    assertEquals("TIN-1", view.payouts().taxId());
    assertTrue(view.privacy().discoverable());
    assertEquals(1, view.team().size());
    assertEquals("Owner", view.team().get(0).role());
  }

  @Test
  void get_neverReturnsAnotherArtistsSettings() {
    FakeStudioRepository repo = new FakeStudioRepository().withSettings(new StudioSettings(
        new ArtistId("other-artist"),
        new NotificationPrefs(true, true, true, true, true, true, true),
        StudioDefaults.blank(),
        PayoutSettings.blank(),
        PrivacySettings.blank(),
        List.of(),
        Instant.now()));
    GetStudioSettingsService service =
        new GetStudioSettingsService(repo, FakeAccountReader.of("artist@example.com"));

    StudioSettingsView view = service.get(ARTIST);

    assertFalse(view.notifications().sales());
  }
}
