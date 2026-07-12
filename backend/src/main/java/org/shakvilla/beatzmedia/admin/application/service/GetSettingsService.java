package org.shakvilla.beatzmedia.admin.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.GetSettings;
import org.shakvilla.beatzmedia.admin.application.port.in.PlatformSettingsView;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;

/**
 * Read service for {@link GetSettings} (LLFR-ADMIN-10.1). Projects the platform-kernel {@link
 * PlatformSettings} + {@link FeatureFlags} onto the frontend {@code PlatformSettings} shape.
 * Read-only; nothing audited. Super-admin scope is enforced at the inbound resource.
 *
 * <p>All five {@code flags.*} values are real platform-kernel {@link FeatureFlags} (including {@code
 * fanMessaging} = {@code FeatureKey.FAN_MESSAGING}, seeded {@code false}). Only {@code providers.*} is
 * honest-static (no per-provider enablement subsystem) — see {@link PlatformSettingsView}.
 */
@ApplicationScoped
public class GetSettingsService implements GetSettings {

  private final PlatformSettingsProvider settings;
  private final FeatureFlags featureFlags;

  @Inject
  public GetSettingsService(PlatformSettingsProvider settings, FeatureFlags featureFlags) {
    this.settings = settings;
    this.featureFlags = featureFlags;
  }

  @Override
  @Transactional
  public PlatformSettingsView get() {
    PlatformSettings s = settings.current();
    return toView(s, featureFlags);
  }

  /** Maps the kernel settings + flags to the frontend {@code PlatformSettings} shape. */
  static PlatformSettingsView toView(PlatformSettings s, FeatureFlags flags) {
    BigDecimal payoutMinimum =
        BigDecimal.valueOf(s.payoutMinimumMinor()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    return new PlatformSettingsView(
        s.platformFeePct(),
        s.payoutDay(),
        payoutMinimum,
        s.defaultCurrency().name(),
        s.maintenanceMode(),
        // providers.* — honest-static (no per-provider enablement subsystem).
        new PlatformSettingsView.Providers(true, true, true, true, true),
        new PlatformSettingsView.Flags(
            flags.isEnabled(FeatureKey.ARTIST_SIGNUPS),
            flags.isEnabled(FeatureKey.PODCASTS),
            flags.isEnabled(FeatureKey.EVENTS),
            flags.isEnabled(FeatureKey.TIPPING),
            flags.isEnabled(FeatureKey.FAN_MESSAGING)));
  }
}
