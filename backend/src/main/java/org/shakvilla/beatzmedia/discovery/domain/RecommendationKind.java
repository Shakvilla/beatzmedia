package org.shakvilla.beatzmedia.discovery.domain;

public enum RecommendationKind {
  single("single"),
  album("album"),
  ep("ep"),
  podcast("podcast");

  private final String wireValue;

  RecommendationKind(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }
}
