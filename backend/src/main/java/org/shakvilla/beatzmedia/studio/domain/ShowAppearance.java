package org.shakvilla.beatzmedia.studio.domain;

/**
 * A live-show appearance listed on a creator's public Studio profile. Free-text {@code date}
 * (display string, e.g. {@code "May 22"}) — mirrors {@code StudioShow} in {@code
 * Frontend/src/lib/studio-data.ts}; not to be confused with {@code events.domain.Event} (ticketed
 * live events, a separate bounded context). Studio ADD §3.
 */
public record ShowAppearance(String id, String venue, String date, String city) {

  public ShowAppearance {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("ShowAppearance id must not be blank");
    }
  }
}
