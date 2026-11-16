package org.shakvilla.beatzmedia.engagement.domain;

import org.shakvilla.beatzmedia.platform.domain.ValidationException;

public enum EngagementEventType {
  START("start"),
  QUALIFIED_PLAY("qualified-play"),
  PROGRESS("progress"),
  COMPLETION("completion"),
  SKIP("skip"),
  REPEAT("repeat");

  private final String wireValue;

  EngagementEventType(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  public static EngagementEventType fromWire(String value) {
    for (EngagementEventType type : values()) {
      if (type.wireValue.equals(value)) return type;
    }
    throw new ValidationException("Unknown engagement event type: " + value, "eventType");
  }
}
