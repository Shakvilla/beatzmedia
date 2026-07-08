package org.shakvilla.beatzmedia.studio.domain;

/**
 * A press/media kit asset (photo, one-sheet, etc.) linked from a creator's public Studio profile.
 * Studio ADD §3.
 */
public record PressAsset(String id, String name, String url) {

  public PressAsset {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("PressAsset id must not be blank");
    }
  }
}
