package org.shakvilla.beatzmedia.studio.application.port.in;

import java.math.BigDecimal;

/**
 * Wire-shaped payout CONFIGURATION (auto-withdraw toggle/threshold, tax id) — distinct from the
 * {@code payments}-owned payout methods/ledger surfaced by {@code GET /studio/payouts}. Shared by
 * {@link StudioSettingsView} (output) and {@link SaveStudioSettingsCommand} (input). {@code
 * autoWithdrawThreshold} is a bare decimal-cedis number, same convention as {@link
 * StudioDefaultsView#trackPrice()}. Studio ADD §6.
 */
public record PayoutSettingsView(boolean autoWithdraw, BigDecimal autoWithdrawThreshold, String taxId) {}
