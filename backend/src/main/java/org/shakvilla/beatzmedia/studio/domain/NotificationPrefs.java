package org.shakvilla.beatzmedia.studio.domain;

/**
 * Studio notification preferences (sale/tip/follower/payout alerts, weekly summary, comments,
 * marketing). Embedded in {@link StudioSettings}. Studio ADD §3 / §6.
 */
public record NotificationPrefs(
    boolean sales,
    boolean tips,
    boolean followers,
    boolean payouts,
    boolean weeklySummary,
    boolean comments,
    boolean marketing) {

  /** A not-yet-configured artist's notification preferences: everything off. */
  public static NotificationPrefs blank() {
    return new NotificationPrefs(false, false, false, false, false, false, false);
  }
}
