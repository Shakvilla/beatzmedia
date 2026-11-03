package org.shakvilla.beatzmedia.notifications.adapter.in.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.domain.ReleaseWentLive;
import org.shakvilla.beatzmedia.notifications.application.service.PublicationFanoutService;
import org.shakvilla.beatzmedia.studio.domain.EpisodePublished;

/** Creates durable notification-fanout jobs only after a publication transaction succeeds. */
@ApplicationScoped
public class PublicationFanoutObserver {

  private final PublicationFanoutService fanout;

  @Inject
  public PublicationFanoutObserver(PublicationFanoutService fanout) {
    this.fanout = fanout;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onRelease(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) ReleaseWentLive event) {
    fanout.enqueueRelease(event.releaseId());
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onEpisode(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) EpisodePublished event) {
    fanout.enqueueEpisode(event.artistId(), event.episodeId());
  }
}
