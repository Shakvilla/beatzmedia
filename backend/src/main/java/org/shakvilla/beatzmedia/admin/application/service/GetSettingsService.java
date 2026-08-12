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
    return toView(
        s,
        new PlatformSettingsView.Flags(
            flags.isEnabled(FeatureKey.ARTIST_SIGNUPS),
            flags.isEnabled(FeatureKey.PODCASTS),
            flags.isEnabled(FeatureKey.EVENTS),
            flags.isEnabled(FeatureKey.TIPPING),
            flags.isEnabled(FeatureKey.FAN_MESSAGING)),
        providersFrom(flags));
  }

  /**
   * Reads the per-rail flags for display (GAP-13).
   *
   * <p>Fail-<em>open</em> here, unlike the charge path. This is a settings screen: showing a rail as
   * off when its row is merely unreadable would tell an operator they had already done something
   * they had not. The authoritative, fail-closed read is {@code PaymentProviderPolicy}, which is
   * what actually decides whether money moves — and a missing row stops the app booting anyway.
   */
  static PlatformSettingsView.Providers providersFrom(FeatureFlags flags) {
    return new PlatformSettingsView.Providers(
        flags.isEnabled(FeatureKey.PROVIDER_MTN),
        flags.isEnabled(FeatureKey.PROVIDER_TELECEL),
        flags.isEnabled(FeatureKey.PROVIDER_AIRTELTIGO),
        flags.isEnabled(FeatureKey.PROVIDER_CARD),
        flags.isEnabled(FeatureKey.PROVIDER_BANK));
  }

  /**
   * Same view, from flag values the caller already holds.
   *
   * <p>For the save path, which must not read its own uncommitted writes. Reading through
   * {@link FeatureFlags} there went to the cache, which reloads in a new transaction and therefore
   * could not see flags written moments earlier in the still-open one — so a settings save returned
   * the values it had just replaced.
   */
  static PlatformSettingsView toView(PlatformSettings s, PlatformSettingsView.Flags flags) {
    return toView(s, flags, new PlatformSettingsView.Providers(true, true, true, true, true));
  }

  /** As above, with the provider values the caller already holds — same read-your-write reason. */
  static PlatformSettingsView toView(
      PlatformSettings s, PlatformSettingsView.Flags flags, PlatformSettingsView.Providers providers) {
    BigDecimal payoutMinimum =
        BigDecimal.valueOf(s.payoutMinimumMinor()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    return new PlatformSettingsView(
        s.platformFeePct(),
        s.payoutDay(),
        payoutMinimum,
        s.defaultCurrency().name(),
        s.maintenanceMode(),
        providers,
        flags);
  }
}
