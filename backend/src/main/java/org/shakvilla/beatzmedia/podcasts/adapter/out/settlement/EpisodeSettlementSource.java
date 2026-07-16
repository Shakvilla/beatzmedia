package org.shakvilla.beatzmedia.podcasts.adapter.out.settlement;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.application.port.out.SettlementSource;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.podcasts.application.port.out.PodcastRepository;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;
import org.shakvilla.beatzmedia.podcasts.domain.Podcast;

/**
 * Settlement for a purchased {@code episode} (WU-COM-4): the payee is the owning show's creator
 * (episode → podcast → {@code creator_account_id}); the ownable unit is the single episode id, which
 * commerce grants via {@code OwnershipGrant.forEpisode}. No side effect (commerce writes the grant).
 */
@ApplicationScoped
public class EpisodeSettlementSource implements SettlementSource {

  private final PodcastRepository repository;

  @Inject
  public EpisodeSettlementSource(PodcastRepository repository) {
    this.repository = repository;
  }

  @Override
  public String entityType() {
    return "episode";
  }

  @Override
  public Optional<AccountId> payee(String refId) {
    return repository
        .findEpisode(new EpisodeId(refId))
        .flatMap(episode -> repository.findShow(episode.podcastId()))
        .flatMap(Podcast::creatorAccountId)
        .map(AccountId::new);
  }

  @Override
  public List<String> ownedEpisodeIds(String refId) {
    return repository.findEpisode(new EpisodeId(refId)).map(episode -> List.of(refId)).orElse(List.of());
  }
}
