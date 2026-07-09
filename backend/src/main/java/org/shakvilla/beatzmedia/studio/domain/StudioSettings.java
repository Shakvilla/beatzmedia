package org.shakvilla.beatzmedia.studio.domain;

import java.time.Instant;
import java.util.List;

/**
 * Studio settings aggregate root — one per artist (Studio ADD §3 / §4.1, LLFR-STUDIO-04.2). Domain
 * layer; no framework imports. Covers ONLY the genuinely backend-owned config sub-objects
 * ({@code notifications}, {@code defaults}, {@code payouts}, {@code privacy}, {@code team}) — the
 * remaining {@code StudioSettingsView} fields (email, sessions, connectedApps, verification,
 * billing, twoFactor, phone, language, timezone, country) have no backing subsystem and are
 * composed as honest static/derived defaults at the application layer; see studio.md §16. Natural
 * upsert semantics — a flat config object, not a lifecycle entity (no FSM).
 */
public final class StudioSettings {

  private final ArtistId artistId;
  private final NotificationPrefs notifications;
  private final StudioDefaults defaults;
  private final PayoutSettings payouts;
  private final PrivacySettings privacy;
  private final List<TeamMember> team;
  private final Instant updatedAt;

  public StudioSettings(
      ArtistId artistId,
      NotificationPrefs notifications,
      StudioDefaults defaults,
      PayoutSettings payouts,
      PrivacySettings privacy,
      List<TeamMember> team,
      Instant updatedAt) {
    if (artistId == null) {
      throw new IllegalArgumentException("artistId must not be null");
    }
    this.artistId = artistId;
    this.notifications = notifications == null ? NotificationPrefs.blank() : notifications;
    this.defaults = defaults == null ? StudioDefaults.blank() : defaults;
    this.payouts = payouts == null ? PayoutSettings.blank() : payouts;
    this.privacy = privacy == null ? PrivacySettings.blank() : privacy;
    this.team = team == null ? List.of() : List.copyOf(team);
    this.updatedAt = updatedAt;
  }

  /**
   * A not-yet-configured settings object for an artist who has never called {@code PUT
   * /studio/settings}. {@code GET /studio/settings} never 404s — it resolves to this blank shell
   * instead (same "never 404s" precedent as {@code StudioProfile#blank}).
   */
  public static StudioSettings blank(ArtistId artistId) {
    return new StudioSettings(
        artistId, NotificationPrefs.blank(), StudioDefaults.blank(), PayoutSettings.blank(),
        PrivacySettings.blank(), List.of(), null);
  }

  public ArtistId artistId() {
    return artistId;
  }

  public NotificationPrefs notifications() {
    return notifications;
  }

  public StudioDefaults defaults() {
    return defaults;
  }

  public PayoutSettings payouts() {
    return payouts;
  }

  public PrivacySettings privacy() {
    return privacy;
  }

  public List<TeamMember> team() {
    return team;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
