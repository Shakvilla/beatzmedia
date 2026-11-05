package org.shakvilla.beatzmedia.discovery.application.port.out;

import java.util.Set;

public interface PersonalizationSignals {

  Snapshot load(String accountId);

  record Snapshot(
      Set<String> followedArtistIds,
      Set<String> followedShowIds,
      Set<String> excludedTrackIds,
      Set<String> completedContentKeys,
      Set<String> recentContentKeys,
      Set<String> preferredCreatorIds,
      Set<String> preferredTags,
      boolean historyEnabled) {

    public Snapshot {
      followedArtistIds = copy(followedArtistIds);
      followedShowIds = copy(followedShowIds);
      excludedTrackIds = copy(excludedTrackIds);
      completedContentKeys = copy(completedContentKeys);
      recentContentKeys = copy(recentContentKeys);
      preferredCreatorIds = copy(preferredCreatorIds);
      preferredTags = copy(preferredTags);
    }

    public static Snapshot empty() {
      return new Snapshot(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    private static Set<String> copy(Set<String> values) {
      return values == null ? Set.of() : Set.copyOf(values);
    }
  }
}
