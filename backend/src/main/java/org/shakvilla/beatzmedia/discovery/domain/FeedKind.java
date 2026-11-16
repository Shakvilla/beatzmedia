package org.shakvilla.beatzmedia.discovery.domain;

public enum FeedKind {
  release,
  podcast_episode;

  public String wireValue() {
    return this == podcast_episode ? "podcast-episode" : "release";
  }
}
