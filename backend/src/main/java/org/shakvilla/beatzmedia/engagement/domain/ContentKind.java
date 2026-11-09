package org.shakvilla.beatzmedia.engagement.domain;

import org.shakvilla.beatzmedia.platform.domain.ValidationException;

public enum ContentKind {
  TRACK("track"),
  PODCAST_EPISODE("podcast-episode");

  private final String wireValue;

  ContentKind(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  public static ContentKind fromWire(String value) {
    for (ContentKind kind : values()) {
      if (kind.wireValue.equals(value)) return kind;
    }
    throw new ValidationException("Unknown content kind: " + value, "contentKind");
  }
}
