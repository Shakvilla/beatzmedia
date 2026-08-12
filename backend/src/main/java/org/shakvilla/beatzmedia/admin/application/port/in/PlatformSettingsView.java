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
 * ({@code mtn/telecel/airteltigo/card/bank}) are equally real as of GAP-13: each is a
 * {@code PROVIDER_*} {@code FeatureKey} seeded in V978, and switching one off stops charges on that
 * rail. They were previously honest-static {@code true} — accepted and thrown away — and this
 * javadoc said so; it is called out because the wrong version of this sentence would tell a reader
 * these values are decorative when they now decide whether money moves.
 */
public record PlatformSettingsView(
    int platformFeePct,
    String payoutDay,
    BigDecimal payoutMinimum,
    String defaultCurrency,
    boolean maintenanceMode,
    Providers providers,
    Flags flags) {

  /**
   * Per-rail payment enablement (GAP-13) — matches {@code providers{ mtn, telecel, airteltigo, card,
   * bank }}. Real and persisted as of this change — previously the whole object was accepted and
   * thrown away.
   *
   * <p>Keys match payments' {@code Provider} enum exactly. They used to be {@code momo} and
   * {@code vodafone}; {@code momo} was labelled "MTN MoMo" so it always meant MTN specifically, and
   * {@code vodafone} named a brand that ceased to exist in 2023 — checkout and payouts already said
   * Telecel, leaving the admin console the only surface still using the old name.
   */
  public record Providers(
      boolean mtn, boolean telecel, boolean airteltigo, boolean card, boolean bank) {}

  /** Feature flags — matches {@code flags{ artistSignups, podcasts, events, tipping, fanMessaging }}. */
  public record Flags(
      boolean artistSignups,
      boolean podcasts,
      boolean events,
      boolean tipping,
      boolean fanMessaging) {}
}
