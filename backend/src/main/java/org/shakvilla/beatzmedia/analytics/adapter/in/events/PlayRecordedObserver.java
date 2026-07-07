package org.shakvilla.beatzmedia.analytics.adapter.in.events;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.analytics.application.port.out.ArtistResolver;
import org.shakvilla.beatzmedia.analytics.application.port.out.PlayFactRepository;
import org.shakvilla.beatzmedia.analytics.domain.PlayFact;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.playback.domain.PlayRecorded;

/**
 * CDI event observer that turns playback's {@link PlayRecorded} into a staged {@link PlayFact}
 * (LLFR-ANALYTICS-01.1). The artist id is resolved from the event's {@code trackId} via
 * {@link ArtistResolver} (which calls catalog's {@code GetTrack} INPUT port in-process — never a
 * catalog table read). Same no-cross-module-reads / {@code AFTER_SUCCESS} guarantees as
 * {@link SaleRecordedObserver}. Analytics ADD §4.1 / §8.3.
 *
 * <p>A track whose artist cannot be resolved (deleted/unknown) is logged and skipped — a play
 * fact is never staged against a fabricated artist id.
 */
@ApplicationScoped
public class PlayRecordedObserver {

  private static final Logger LOG = Logger.getLogger(PlayRecordedObserver.class);

  private final PlayFactRepository playFacts;
  private final ArtistResolver artistResolver;
  private final IdGenerator ids;

  @Inject
  public PlayRecordedObserver(PlayFactRepository playFacts, ArtistResolver artistResolver, IdGenerator ids) {
    this.playFacts = playFacts;
    this.artistResolver = artistResolver;
    this.ids = ids;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onPlayRecorded(@Observes(during = TransactionPhase.AFTER_SUCCESS) PlayRecorded event) {
    Optional<ArtistId> artist = artistResolver.artistOfTrack(new TrackId(event.trackId()));
    if (artist.isEmpty()) {
      LOG.debugf("PlayRecorded for unresolvable track %s; skipping analytics fact", event.trackId());
      return;
    }
    playFacts.append(
        PlayFact.unprocessed(ids.newId(), artist.get().value(), event.accountId(), event.at()));
  }
}
