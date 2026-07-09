package org.shakvilla.beatzmedia.studio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/** Unit tests for {@link StudioSettings} construction invariants — LLFR-STUDIO-04.2. */
@Tag("unit")
class StudioSettingsTest {

  @Test
  void constructor_nullArtistId_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new StudioSettings(null, null, null, null, null, List.of(), Instant.now()));
  }

  @Test
  void constructor_nullSubObjects_normalizeToBlankDefaults() {
    StudioSettings settings = new StudioSettings(
        new ArtistId("artist-1"), null, null, null, null, null, Instant.parse("2026-06-01T00:00:00Z"));

    assertEquals(NotificationPrefs.blank(), settings.notifications());
    assertEquals(StudioDefaults.blank(), settings.defaults());
    assertEquals(PayoutSettings.blank(), settings.payouts());
    assertEquals(PrivacySettings.blank(), settings.privacy());
    assertTrue(settings.team().isEmpty());
  }

  @Test
  void blank_returnsEmptyShellForArtist() {
    StudioSettings blank = StudioSettings.blank(new ArtistId("artist-2"));

    assertEquals("artist-2", blank.artistId().value());
    assertEquals(NotificationPrefs.blank(), blank.notifications());
    assertEquals(Money.ofMinor(0, Currency.GHS), blank.defaults().trackPrice());
    assertEquals("public", blank.defaults().releaseVisibility());
    assertTrue(blank.team().isEmpty());
  }

  @Test
  void teamMember_blankId_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TeamMember("", "name", "email@test.com", TeamRole.OWNER));
  }

  @Test
  void teamMember_nullRole_throws() {
    assertThrows(IllegalArgumentException.class, () -> new TeamMember("id", "name", "e@test.com", null));
  }

  @Test
  void teamRole_wireValues_roundTrip() {
    assertEquals(TeamRole.OWNER, TeamRole.fromWireValue("Owner"));
    assertEquals(TeamRole.MANAGER, TeamRole.fromWireValue("Manager"));
    assertEquals(TeamRole.LABEL, TeamRole.fromWireValue("Label"));
    assertEquals(TeamRole.INVITED, TeamRole.fromWireValue("Invited"));
    assertThrows(IllegalArgumentException.class, () -> TeamRole.fromWireValue("Nope"));
  }

  @Test
  void studioDefaults_nullFieldsNormalize() {
    StudioDefaults defaults = new StudioDefaults(null, null, true, true);

    assertEquals(Money.ofMinor(0, Currency.GHS), defaults.trackPrice());
    assertEquals("public", defaults.releaseVisibility());
  }

  @Test
  void payoutSettings_moneyStoredInMinorUnits() {
    PayoutSettings payouts = new PayoutSettings(true, Money.ofCedis(BigDecimal.valueOf(50)), "TIN-1");

    assertEquals(5000L, payouts.autoWithdrawThreshold().minor());
  }
}
