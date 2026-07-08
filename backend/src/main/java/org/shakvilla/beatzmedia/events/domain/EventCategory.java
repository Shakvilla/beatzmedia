package org.shakvilla.beatzmedia.events.domain;

/**
 * Live-event category. Lifted verbatim from the {@code EventCategory} TypeScript union in
 * {@code Frontend/src/types/index.ts}. Events ADD §3.
 */
public enum EventCategory {
  CONCERT("Concert"),
  FESTIVAL("Festival"),
  CLUB_NIGHT("Club Night"),
  LISTENING_PARTY("Listening Party"),
  TOUR("Tour");

  private final String wireValue;

  EventCategory(String wireValue) {
    this.wireValue = wireValue;
  }

  /** The exact wire string used by the frontend/API (e.g. {@code "Club Night"}). */
  public String wireValue() {
    return wireValue;
  }

  /** Parse the wire string back to the enum constant. */
  public static EventCategory fromWireValue(String wireValue) {
    for (EventCategory category : values()) {
      if (category.wireValue.equals(wireValue)) {
        return category;
      }
    }
    throw new IllegalArgumentException("Unknown event category: " + wireValue);
  }
}
