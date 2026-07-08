package org.shakvilla.beatzmedia.events.domain;

/** Typed wrapper for an {@link Event}'s primary key (opaque string). Events ADD §3. */
public record EventId(String value) {

  public EventId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("EventId value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
