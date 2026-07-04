package org.shakvilla.beatzmedia.podcasts.domain;

/**
 * Podcast show category. Lifted verbatim from the {@code PodcastCategory} TypeScript union in
 * {@code Frontend/src/types/index.ts}. ADD §3.
 */
public enum PodcastCategory {
  NEWS_AND_POLITICS("News & Politics"),
  COMEDY("Comedy"),
  BUSINESS("Business"),
  SPORTS("Sports"),
  CULTURE("Culture"),
  TECH("Tech"),
  HEALTH("Health"),
  STORYTELLING("Storytelling");

  private final String wireValue;

  PodcastCategory(String wireValue) {
    this.wireValue = wireValue;
  }

  /** The exact wire string used by the frontend/API (e.g. {@code "News & Politics"}). */
  public String wireValue() {
    return wireValue;
  }

  /** Parse the wire string back to the enum constant. */
  public static PodcastCategory fromWireValue(String wireValue) {
    for (PodcastCategory category : values()) {
      if (category.wireValue.equals(wireValue)) {
        return category;
      }
    }
    throw new IllegalArgumentException("Unknown podcast category: " + wireValue);
  }
}
