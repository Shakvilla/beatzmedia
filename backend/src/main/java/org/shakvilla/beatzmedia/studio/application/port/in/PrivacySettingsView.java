package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * Wire-shaped public-profile privacy preferences, shared by {@link StudioSettingsView} (output) and
 * {@link SaveStudioSettingsCommand} (input). Studio ADD §6.
 */
public record PrivacySettingsView(
    boolean discoverable, boolean showRealName, boolean acceptBookings, boolean allowDms) {}
