package org.shakvilla.beatzmedia.catalog.adapter.in.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.MediaReady;

/**
 * Publishes a catalog track once its audio has finished transcoding.
 *
 * <p><strong>Why this exists.</strong> {@code UploadReleaseTrackService} inserts a stub track with
 * {@code status="uploading"} and {@code durationSec=0}, because neither the delivery renditions nor
 * the ffprobe duration exist yet at upload time. {@code MediaReady} — whose own Javadoc says
 * "Consumers (catalog/studio/podcasts) observe this to flip their track/episode to ready" — was
 * fired but had <em>no observers at all</em>. So an upload could transcode perfectly, reach READY
 * in {@code media_asset}, and its track would still read "uploading" forever, invisible to fans.
 * Every audio upload the platform has ever taken is in that state.
 *
 * <p><strong>Timing — {@code AFTER_SUCCESS}.</strong> Only once the media transaction has durably
 * committed, so a track is never published for an asset whose READY transition rolled back. The
 * {@code REQUIRES_NEW} is required: an AFTER_SUCCESS observer runs with no active transaction, so
 * without it the repository write would have nowhere to enlist.
 *
 * <p><strong>Direction.</strong> catalog → media, the same way {@code catalog} already depends on
 * media's {@code UploadOriginalUseCase}. Media knows nothing about catalog; it publishes a fact and
 * whoever owns that {@code ownerRef} reacts. No new module edge.
 */
@ApplicationScoped
public class MediaReadyObserver {

  private static final Logger LOG = Logger.getLogger(MediaReadyObserver.class);

  /** {@code OwnerRef.module} value catalog stamps on its uploads. */
  private static final String CATALOG_MODULE = "catalog";

  private final CatalogRepository repo;

  @Inject
  public MediaReadyObserver(CatalogRepository repo) {
    this.repo = repo;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onMediaReady(@Observes(during = TransactionPhase.AFTER_SUCCESS) MediaReady event) {
    // Artwork shares this event; only audio decides whether a track is playable.
    if (event.kind() != MediaKind.AUDIO) {
      return;
    }
    // Studio episodes and podcast audio ride the same event with a different owning module.
    if (!CATALOG_MODULE.equals(event.ownerRef().module())) {
      return;
    }

    String trackId = event.ownerRef().entityId();
    repo.findTrack(new TrackId(trackId))
        .ifPresentOrElse(
            track -> {
              Track ready = track.markReady(event.durationSec());
              // markReady is idempotent and returns the same instance when nothing changed, so a
              // re-fired MediaReady (re-transcode) costs no write.
              if (ready != track) {
                repo.saveTrack(ready);
                LOG.infof(
                    "catalog: track %s published after transcode (duration=%ds)",
                    trackId, event.durationSec());
              }
            },
            () ->
                // The asset outlived its track (draft track deleted mid-transcode). Not an error,
                // but silence here is what made the original bug invisible, so say something.
                LOG.warnf(
                    "catalog: MediaReady for asset %s references unknown track %s — nothing to"
                        + " publish",
                    event.assetId().value(), trackId));
  }
}
