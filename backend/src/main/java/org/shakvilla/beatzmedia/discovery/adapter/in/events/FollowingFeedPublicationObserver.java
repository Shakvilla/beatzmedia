package org.shakvilla.beatzmedia.discovery.adapter.in.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.domain.ReleaseWentLive;
import org.shakvilla.beatzmedia.discovery.application.service.FollowingFeedProjectionService;
import org.shakvilla.beatzmedia.studio.domain.EpisodePublished;

@ApplicationScoped
public class FollowingFeedPublicationObserver {

  private final FollowingFeedProjectionService projection;

  @Inject
  public FollowingFeedPublicationObserver(FollowingFeedProjectionService projection) {
    this.projection = projection;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onRelease(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) ReleaseWentLive event) {
    projection.release(event.releaseId(), event.wentLiveAt());
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onEpisode(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) EpisodePublished event) {
    projection.episode(event.artistId(), event.episodeId(), event.occurredAt());
  }
}
