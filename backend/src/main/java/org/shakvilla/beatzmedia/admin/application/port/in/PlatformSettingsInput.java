package org.shakvilla.beatzmedia.admin.application.port.in;

import java.math.BigDecimal;

/**
 * Command for {@code PUT /v1/admin/settings} (LLFR-ADMIN-10.1). Same shape as {@link
 * PlatformSettingsView}. {@code platformFeePct} 0–100 and {@code payoutMinimum} ≥ 0 are validated at
 * the REST boundary; changing {@code platformFeePct} is forward-only (payments reads it at settle
 * time) and audited. All five {@code flags} are real and persisted; {@code providers} is accepted but
 * honest-static (no per-provider enablement subsystem) — see {@link PlatformSettingsView}.
 */
public record PlatformSettingsInput(
    int platformFeePct,
    String payoutDay,
    BigDecimal payoutMinimum,
    String defaultCurrency,
    boolean maintenanceMode,
    PlatformSettingsView.Providers providers,
    PlatformSettingsView.Flags flags) {}
