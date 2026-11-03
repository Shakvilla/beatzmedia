package org.shakvilla.beatzmedia.catalog.domain;

/** Proposed changes to an existing track in a release revision. Track identity is immutable. */
public record ReleaseRevisionTrack(
    String trackId,
    int position,
    long priceMinor,
    String title,
    String image,
    boolean customArtwork,
    String audioAssetId,
    int durationSec) {

  public ReleaseRevisionTrack {
    if (trackId == null || trackId.isBlank()) throw new IllegalArgumentException("trackId required");
    if (position < 0) throw new IllegalArgumentException("position must be non-negative");
    if (priceMinor < 0 || priceMinor > ReleaseTrack.MAX_PRICE_MINOR) {
      throw new InvalidPriceException("revision track price is outside the permitted range");
    }
    if (title == null || title.isBlank()) throw new IllegalArgumentException("track title required");
    if (durationSec < 0) throw new IllegalArgumentException("duration must be non-negative");
  }
}
