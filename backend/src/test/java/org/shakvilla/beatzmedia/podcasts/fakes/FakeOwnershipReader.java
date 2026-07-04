package org.shakvilla.beatzmedia.podcasts.fakes;

import java.util.HashSet;
import java.util.Set;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.podcasts.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;

/** In-memory fake for {@link OwnershipReader} used in unit tests. */
public class FakeOwnershipReader implements OwnershipReader {

  private final Set<String> owned = new HashSet<>();
  private int ownsEpisodeCalls = 0;
  private int ownedEpisodesCalls = 0;

  public FakeOwnershipReader markOwned(AccountId account, EpisodeId episode) {
    owned.add(key(account, episode));
    return this;
  }

  @Override
  public boolean ownsEpisode(AccountId caller, EpisodeId episode) {
    ownsEpisodeCalls++;
    return owned.contains(key(caller, episode));
  }

  @Override
  public Set<EpisodeId> ownedEpisodes(AccountId caller, Set<EpisodeId> candidates) {
    ownedEpisodesCalls++;
    Set<EpisodeId> result = new HashSet<>();
    for (EpisodeId candidate : candidates) {
      if (owned.contains(key(caller, candidate))) {
        result.add(candidate);
      }
    }
    return result;
  }

  public int ownsEpisodeCalls() {
    return ownsEpisodeCalls;
  }

  public int ownedEpisodesCalls() {
    return ownedEpisodesCalls;
  }

  private String key(AccountId account, EpisodeId episode) {
    return account.value() + "|" + episode.value();
  }
}
