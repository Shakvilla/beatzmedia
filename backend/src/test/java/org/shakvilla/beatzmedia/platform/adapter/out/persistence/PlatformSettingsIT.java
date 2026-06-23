package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test for the platform settings and feature flags persistence adapters.
 * Verifies Flyway seed applies correct defaults on an empty DB. Testing-strategy §3 / ADD §11.
 *
 * <p>Uses Quarkus Dev Services (Testcontainers Postgres) — Flyway migrates at start.
 */
@QuarkusTest
@Tag("integration")
class PlatformSettingsIT {

  @Inject
  PlatformSettingsProvider settingsProvider;

  @Inject
  FeatureFlags featureFlags;

  // --- Flyway seed verification -------------------------------------------

  @Test
  void flyway_seed_applies_default_platform_fee_pct() {
    PlatformSettings settings = settingsProvider.current();
    assertNotNull(settings, "Settings must be seeded");
    assertEquals(30, settings.platformFeePct(), "Default platformFeePct should be 30");
  }

  @Test
  void flyway_seed_applies_default_creator_share_pct() {
    assertEquals(70, settingsProvider.current().creatorSharePct());
  }

  @Test
  void flyway_seed_applies_default_tip_fee_pct() {
    assertEquals(10, settingsProvider.current().tipFeePct());
  }

  @Test
  void flyway_seed_applies_default_bundle_discount_pct() {
    assertEquals(24, settingsProvider.current().bundleDiscountPct());
  }

  @Test
  void flyway_seed_applies_default_payout_minimum_minor() {
    assertEquals(1000L, settingsProvider.current().payoutMinimumMinor());
  }

  @Test
  void flyway_seed_applies_default_service_fee_minor() {
    assertEquals(50L, settingsProvider.current().serviceFeeMinor());
  }

  @Test
  void flyway_seed_applies_maintenance_mode_false() {
    assertFalse(settingsProvider.current().maintenanceMode());
  }

  @Test
  void flyway_seed_applies_ghs_as_default_currency() {
    assertEquals("GHS", settingsProvider.current().defaultCurrency().name());
  }

  // --- Feature flag seed verification -------------------------------------

  @Test
  void artist_signups_enabled_by_default() {
    assertTrue(featureFlags.isEnabled(FeatureKey.ARTIST_SIGNUPS));
  }

  @Test
  void podcasts_enabled_by_default() {
    assertTrue(featureFlags.isEnabled(FeatureKey.PODCASTS));
  }

  @Test
  void events_enabled_by_default() {
    assertTrue(featureFlags.isEnabled(FeatureKey.EVENTS));
  }

  @Test
  void tipping_enabled_by_default() {
    assertTrue(featureFlags.isEnabled(FeatureKey.TIPPING));
  }

  @Test
  void fan_messaging_disabled_by_default() {
    // Ships disabled per PRD §1.4
    assertFalse(featureFlags.isEnabled(FeatureKey.FAN_MESSAGING));
  }

  // --- Cache invalidation round-trip -------------------------------------

  @Test
  void settings_save_and_read_round_trip() {
    PlatformSettings current = settingsProvider.current();
    PlatformSettings updated = current.withMaintenanceMode(true);
    settingsProvider.save(updated);
    assertTrue(settingsProvider.current().maintenanceMode(), "Saved settings should be reflected");
    // Restore
    settingsProvider.save(current.withMaintenanceMode(false));
    assertFalse(settingsProvider.current().maintenanceMode());
  }

  @Test
  void feature_flag_set_and_read_round_trip() {
    // Disable TIPPING and verify
    featureFlags.set(FeatureKey.TIPPING, false);
    assertFalse(featureFlags.isEnabled(FeatureKey.TIPPING), "Flag should be disabled after set");
    // Re-enable
    featureFlags.set(FeatureKey.TIPPING, true);
    assertTrue(featureFlags.isEnabled(FeatureKey.TIPPING));
  }
}
