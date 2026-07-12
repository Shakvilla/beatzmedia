package org.shakvilla.beatzmedia.admin.application.port.in;

import java.math.BigDecimal;

/**
 * Read model for {@code GET /v1/admin/settings} (LLFR-ADMIN-10.1), matching the frontend {@code
 * PlatformSettings} in {@code Frontend/src/lib/admin-data.ts}: {@code { platformFeePct, payoutDay,
 * payoutMinimum, defaultCurrency, maintenanceMode, providers{…}, flags{…} }}.
 *
 * <p><strong>Money.</strong> {@code payoutMinimum} is bare decimal cedis (admin dashboard convention,
 * WU-ADM-1) converted from the domain's {@code payoutMinimumMinor}.
 *
 * <p><strong>flags</strong> ({@code artistSignups/podcasts/events/tipping/fanMessaging}) are all real
 * platform-kernel feature flags ({@code FeatureKey}s, seeded in V2). <strong>providers</strong>
 * ({@code momo/vodafone/airteltigo/card/bank}) are honest-static ({@code true} — no per-provider
 * enablement subsystem exists), a documented carryover (admin ADD as-built).
 */
public record PlatformSettingsView(
    int platformFeePct,
    String payoutDay,
    BigDecimal payoutMinimum,
    String defaultCurrency,
    boolean maintenanceMode,
    Providers providers,
    Flags flags) {

  /** Payment-provider enablement toggles — matches {@code providers{ momo, vodafone, airteltigo, card, bank }}. */
  public record Providers(
      boolean momo, boolean vodafone, boolean airteltigo, boolean card, boolean bank) {}

  /** Feature flags — matches {@code flags{ artistSignups, podcasts, events, tipping, fanMessaging }}. */
  public record Flags(
      boolean artistSignups,
      boolean podcasts,
      boolean events,
      boolean tipping,
      boolean fanMessaging) {}
}
