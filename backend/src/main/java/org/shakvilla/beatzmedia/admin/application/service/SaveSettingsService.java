package org.shakvilla.beatzmedia.admin.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.PlatformSettingsInput;
import org.shakvilla.beatzmedia.admin.application.port.in.PlatformSettingsView;
import org.shakvilla.beatzmedia.admin.application.port.in.SaveSettings;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Application service for {@link SaveSettings} (LLFR-ADMIN-10.1). Super-admin only (enforced at the
 * inbound resource). Persists the scalar settings + the four real feature flags, then appends exactly
 * one {@code AuditEntry} (INV-10, {@code SETTINGS}).
 *
 * <p><strong>Fee change is forward-only.</strong> Only {@code platformFeePct} is stored; payments
 * reads it at settle time via {@code PlatformSettings}, so already-settled sales are never re-priced.
 * {@code creatorSharePct} is kept complementary ({@code 100 − fee}); the other split constants
 * (tip fee, bundle discount, service fee) are preserved from the current settings — they are not on
 * the frontend {@code PlatformSettings} contract.
 *
 * <p><strong>Honest-static inputs.</strong> {@code providers.*} is accepted but not persisted (no
 * per-provider enablement subsystem) — documented in {@link PlatformSettingsView}. All five {@code
 * flags.*} (including {@code fanMessaging}) are real and persisted.
 */
@ApplicationScoped
public class SaveSettingsService implements SaveSettings {

  private final PlatformSettingsProvider settings;
  private final FeatureFlags featureFlags;
  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;

  @Inject
  public SaveSettingsService(
      PlatformSettingsProvider settings,
      FeatureFlags featureFlags,
      AuditWriter auditWriter,
      IdGenerator idGenerator,
      Clock clock) {
    this.settings = settings;
    this.featureFlags = featureFlags;
    this.auditWriter = auditWriter;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public PlatformSettingsView save(String actorId, PlatformSettingsInput input) {
    PlatformSettings current = settings.current();

    int newFeePct = input.platformFeePct();
    Currency currency = parseCurrency(input.defaultCurrency());
    long payoutMinimumMinor = toMinor(input.payoutMinimum());

    PlatformSettings updated =
        new PlatformSettings(
            newFeePct,
            100 - newFeePct, // creatorSharePct kept complementary to the fee
            current.tipFeePct(),
            current.bundleDiscountPct(),
            input.payoutDay(),
            payoutMinimumMinor,
            current.serviceFeeMinor(),
            currency,
            input.maintenanceMode());
    PlatformSettings saved = settings.save(updated);

    // Persist all five real feature flags.
    featureFlags.set(FeatureKey.ARTIST_SIGNUPS, input.flags().artistSignups());
    featureFlags.set(FeatureKey.PODCASTS, input.flags().podcasts());
    featureFlags.set(FeatureKey.EVENTS, input.flags().events());
    featureFlags.set(FeatureKey.TIPPING, input.flags().tipping());
    featureFlags.set(FeatureKey.FAN_MESSAGING, input.flags().fanMessaging());

    String reason =
        current.platformFeePct() != newFeePct
            ? "platformFeePct: " + current.platformFeePct() + " -> " + newFeePct
            : null;
    auditWriter.append(
        new AuditEntry(
            idGenerator.newId(),
            actorId,
            "Updated platform settings",
            "PlatformSettings",
            "settings",
            AuditType.SETTINGS,
            reason,
            clock.now()));

    return GetSettingsService.toView(saved, featureFlags);
  }

  private static Currency parseCurrency(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("defaultCurrency is required", "defaultCurrency");
    }
    try {
      return Currency.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Unsupported currency: " + value, "defaultCurrency");
    }
  }

  /** Upper bound on {@code payoutMinimum} (₵1,000,000) — a defence-in-depth ceiling mirroring the
   * REST {@code @DecimalMax} so a direct port call with an absurd value surfaces a mapped 422 instead
   * of an {@code ArithmeticException} / 500. */
  private static final BigDecimal MAX_PAYOUT_MINIMUM_CEDIS = BigDecimal.valueOf(1_000_000);

  private static long toMinor(BigDecimal cedis) {
    if (cedis == null) {
      throw new ValidationException("payoutMinimum is required", "payoutMinimum");
    }
    if (cedis.signum() < 0 || cedis.compareTo(MAX_PAYOUT_MINIMUM_CEDIS) > 0) {
      throw new ValidationException(
          "payoutMinimum must be between 0 and " + MAX_PAYOUT_MINIMUM_CEDIS, "payoutMinimum");
    }
    return cedis.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
  }
}
