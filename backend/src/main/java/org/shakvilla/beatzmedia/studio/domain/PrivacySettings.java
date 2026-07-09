package org.shakvilla.beatzmedia.studio.domain;

/**
 * A creator's public-profile privacy preferences (discoverability, real-name display, booking
 * requests, DMs). Embedded in {@link StudioSettings}. Studio ADD §3 / §6.
 */
public record PrivacySettings(
    boolean discoverable, boolean showRealName, boolean acceptBookings, boolean allowDms) {

  /** A not-yet-configured artist's privacy preferences: everything off. */
  public static PrivacySettings blank() {
    return new PrivacySettings(false, false, false, false);
  }
}
