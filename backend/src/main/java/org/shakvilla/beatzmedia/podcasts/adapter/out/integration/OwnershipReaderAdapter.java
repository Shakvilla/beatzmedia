package org.shakvilla.beatzmedia.podcasts.adapter.out.integration;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.application.port.in.GetOwnedEpisodeIds;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.podcasts.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;

/**
 * Implements podcasts' {@link OwnershipReader} output port by calling commerce's
 * {@link GetOwnedEpisodeIds} INPUT port in-process — podcasts never reads commerce's
 * {@code ownership_grant} table directly. ADD §5.2.
 */
@ApplicationScoped
public class OwnershipReaderAdapter implements OwnershipReader {

  private final GetOwnedEpisodeIds getOwnedEpisodeIds;

  @Inject
  public OwnershipReaderAdapter(GetOwnedEpisodeIds getOwnedEpisodeIds) {
    this.getOwnedEpisodeIds = getOwnedEpisodeIds;
  }

  @Override
  public boolean ownsEpisode(AccountId caller, EpisodeId episode) {
    return getOwnedEpisodeIds.isOwned(caller, episode.value());
  }

  @Override
  public Set<EpisodeId> ownedEpisodes(AccountId caller, Set<EpisodeId> candidates) {
    if (candidates.isEmpty()) {
      return Set.of();
    }
    List<String> candidateIds = candidates.stream().map(EpisodeId::value).toList();
    Set<String> ownedIds = getOwnedEpisodeIds.ownedOf(caller, candidateIds);
    return ownedIds.stream().map(EpisodeId::new).collect(Collectors.toUnmodifiableSet());
  }
}
