package org.shakvilla.beatzmedia.studio.domain;

/**
 * External profile links a creator can publish on their public Studio profile. Every field is
 * optional free text (handles/URLs are not format-validated — the frontend renders them as given).
 * Studio ADD §3 / §6.
 */
public record ProfileLinks(String instagram, String twitter, String youtube, String website) {

  /** Normalizes {@code null} field values to {@code ""} so the wire shape always has 4 strings. */
  public ProfileLinks {
    instagram = instagram == null ? "" : instagram;
    twitter = twitter == null ? "" : twitter;
    youtube = youtube == null ? "" : youtube;
    website = website == null ? "" : website;
  }

  /** No links configured yet (e.g. a not-yet-saved profile). */
  public static ProfileLinks empty() {
    return new ProfileLinks("", "", "", "");
  }
}
