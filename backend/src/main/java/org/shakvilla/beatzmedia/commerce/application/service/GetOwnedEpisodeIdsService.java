package org.shakvilla.beatzmedia.commerce.application.service;

import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.commerce.application.port.in.GetOwnedEpisodeIds;
import org.shakvilla.beatzmedia.commerce.application.port.out.OwnershipRepository;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Application service for {@link GetOwnedEpisodeIds}, consumed in-process by the podcasts
 * module's {@code OwnershipReader} adapter (WU-POD-1). Commerce ADD §4.1.
 */
@ApplicationScoped
@Transactional
public class GetOwnedEpisodeIdsService implements GetOwnedEpisodeIds {

  private final OwnershipRepository ownershipRepository;

  @Inject
  public GetOwnedEpisodeIdsService(OwnershipRepository ownershipRepository) {
    this.ownershipRepository = ownershipRepository;
  }

  @Override
  public boolean isOwned(AccountId account, String episodeId) {
    return ownershipRepository.existsActiveForEpisode(account, episodeId);
  }

  @Override
  public Set<String> ownedOf(AccountId account, List<String> candidateEpisodeIds) {
    return Set.copyOf(ownershipRepository.activeEpisodeIds(account, candidateEpisodeIds));
  }
}
