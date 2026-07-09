package org.shakvilla.beatzmedia.studio.application.port.in;

import java.util.List;

/**
 * Command for {@code PUT /studio/settings} — {@link SaveStudioSettings}. Field names match {@code
 * SaveStudioSettingsDto} = the writable SUBSET of {@code StudioSettingsDto} — Category A only
 * ({@code notifications}, {@code defaults}, {@code payouts}, {@code privacy}, {@code team}).
 * Category B fields ({@code email}, {@code sessions}, {@code verification}, {@code billing}, etc.)
 * are never accepted here (studio.md §16). Studio ADD §4.1 / LLFR-STUDIO-04.2.
 */
public record SaveStudioSettingsCommand(
    NotificationsView notifications,
    StudioDefaultsView defaults,
    PayoutSettingsView payouts,
    PrivacySettingsView privacy,
    List<TeamMemberView> team) {}
