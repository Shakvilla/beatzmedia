package org.shakvilla.beatzmedia.podcasts.adapter.out.settlement;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.application.port.out.SettlementSource;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.podcasts.application.port.out.PodcastRepository;
import org.shakvilla.beatzmedia.podcasts.domain.Podcast;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastEpisode;

/**
 * Settlement for a purchased {@code season-pass} (WU-COM-4): the payee is the show's creator; the pass
 * grants ownership of every <em>current</em> episode of the show (album-like expansion, INV-2 — later
 * episodes are not retroactively granted). No side effect (commerce writes the grants).
 */
@ApplicationScoped
public class SeasonPassSettlementSource implements SettlementSource {

  private final PodcastRepository repository;

  @Inject
  public SeasonPassSettlementSource(PodcastRepository repository) {
    this.repository = repository;
  }

  @Override
  public String entityType() {
    return "season-pass";
  }

  @Override
  public Optional<AccountId> payee(String refId) {
    return repository.findShow(new PodcastId(refId)).flatMap(Podcast::creatorAccountId).map(AccountId::new);
  }

  @Override
  public List<String> ownedEpisodeIds(String refId) {
    return repository.findEpisodes(new PodcastId(refId)).stream()
        .map(PodcastEpisode::id)
        .map(id -> id.value())
        .toList();
  }
}
