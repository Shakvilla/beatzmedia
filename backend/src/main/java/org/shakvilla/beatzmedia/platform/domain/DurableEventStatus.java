package org.shakvilla.beatzmedia.platform.domain;

import java.util.Locale;

public enum DurableEventStatus {
  PENDING,
  PROCESSING,
  COMPLETED,
  FAILED;

  public String wireValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static DurableEventStatus fromWireValue(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException invalid) {
      throw new ValidationException("Unknown durable event status: " + value, "status");
    }
  }
}
