package org.shakvilla.beatzmedia.studio.application.port.in;

import java.util.List;

/**
 * Read-model / DTO for a creator's Studio settings. Field names match {@code StudioSettings} in
 * {@code Frontend/src/lib/studio-data.ts} exactly. Studio ADD §6 / §16 / LLFR-STUDIO-04.2.
 *
 * <p>Category A fields ({@code notifications}, {@code defaults}, {@code payouts}, {@code privacy},
 * {@code team}) are genuinely persisted and writable via {@code PUT /studio/settings}. Category B
 * fields ({@code email}, {@code phone}, {@code country}, {@code language}, {@code timezone},
 * {@code twoFactor}, {@code sessions}, {@code connectedApps}, {@code verification}, {@code billing})
 * have no backing subsystem (except {@code email}, sourced from {@code identity}) and are honest
 * static/derived defaults — see studio.md §16 for the full rationale per field. None of the
 * Category B fields are ever accepted from {@code PUT /studio/settings}.
 */
public record StudioSettingsView(
    String email,
    String phone,
    String country,
    String language,
    String timezone,
    boolean twoFactor,
    List<SessionView> sessions,
    List<ConnectedAppView> connectedApps,
    VerificationView verification,
    BillingView billing,
    NotificationsView notifications,
    StudioDefaultsView defaults,
    PayoutSettingsView payouts,
    PrivacySettingsView privacy,
    List<TeamMemberView> team) {}
