package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * Wire-shaped notification preferences, shared by {@link StudioSettingsView} (output) and {@link
 * SaveStudioSettingsCommand} (input) — the shape is identical in both directions. Field names match
 * {@code StudioSettings.notifications} in {@code Frontend/src/lib/studio-data.ts}. Studio ADD §6.
 */
public record NotificationsView(
    boolean sales,
    boolean tips,
    boolean followers,
    boolean payouts,
    boolean weeklySummary,
    boolean comments,
    boolean marketing) {}
