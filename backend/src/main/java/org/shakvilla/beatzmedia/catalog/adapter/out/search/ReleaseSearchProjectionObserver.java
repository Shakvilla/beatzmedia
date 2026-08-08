package org.shakvilla.beatzmedia.catalog.adapter.out.search;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ContentTakenDown;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.search.application.port.in.IndexEntityUseCase;

/**
 * Keeps the search index in step with a release going live or being taken down.
 *
 * <p><strong>Why this exists.</strong> Approving a release flips {@code release.status} to
 * {@code live}, and the direct Postgres reads (home rails, artist page) pick that up on the very
 * next request. Search does not: it answers from {@code search_document}, which was only ever
 * rebuilt by the periodic backfill. So a track published through the admin console stayed
 * {@code visible = false} in the index — invisible to search — until the next reindex happened to
 * run. The reverse was worse: a taken-down release kept its {@code visible = true} document and
 * remained searchable after being pulled.
 *
 * <p>This is the wiring {@code IndexEventObserversStub} described and deferred until the events
 * existed. They exist now: {@code PublishReleaseService} fires {@link ReleaseWentLive} on admin
 * approval, on the scheduled go-live job, and on reinstate, and {@link ContentTakenDown} on
 * takedown — so all four paths are covered by observing the two events rather than by hooking each
 * call site.
 *
 * <p><strong>Direction.</strong> catalog → search, the same way {@link TrackIndexSource} already
 * runs: catalog owns the data and the mapping, search never reads catalog. No new module edge.
 *
 * <p><strong>Timing.</strong> {@code AFTER_SUCCESS} so the index is never updated for a transition
 * that rolled back, and {@code REQUIRES_NEW} because an AFTER_SUCCESS observer runs with no active
 * transaction — without it the repository read would have nothing to enlist in.
 */
@ApplicationScoped
public class ReleaseSearchProjectionObserver {

  private static final Logger LOG = Logger.getLogger(ReleaseSearchProjectionObserver.class);

  private final CatalogRepository repository;
  private final IndexEntityUseCase index;

  @Inject
  public ReleaseSearchProjectionObserver(
      CatalogRepository repository, IndexEntityUseCase index) {
    this.repository = repository;
    this.index = index;
  }

  /** A release went live — its tracks become searchable. */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onReleaseWentLive(
      @Observes(during = TransactionPhase.AFTER_SUCCESS)
          org.shakvilla.beatzmedia.catalog.domain.ReleaseWentLive event) {
    reproject(event.releaseId(), true, "went live");
  }

  /**
   * A release was taken down — its tracks must stop being searchable.
   *
   * <p>Re-indexed with {@code visible = false} rather than deleted: the index is upsert-only, and
   * dropping the document would strand nothing, but a soft hide keeps the row available for the
   * next backfill to reconcile and matches what {@code allTracksForIndex} already does for
   * taken-down content.
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onContentTakenDown(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) ContentTakenDown event) {
    reproject(event.releaseId(), false, "taken down");
  }

  private void reproject(String releaseId, boolean visible, String why) {
    Release release = repository.findRelease(new ReleaseId(releaseId)).orElse(null);
    if (release == null) {
      // The release was deleted between the transition committing and this observer running.
      // Survivable, but silence here is what let the original staleness go unnoticed.
      LOG.warnf("search: release %s %s but no longer exists — nothing to reproject", releaseId, why);
      return;
    }

    List<String> trackIds = release.getTracks().stream().map(ReleaseTrack::trackId).toList();
    if (trackIds.isEmpty()) {
      return;
    }

    List<Track> tracks = repository.tracksByIds(trackIds);
    for (Track track : tracks) {
      index.index(CatalogIndexDocuments.fromTrack(track, visible));
    }
    LOG.infof(
        "search: reprojected %d track(s) of release %s (%s, visible=%s)",
        tracks.size(), releaseId, why, visible);
  }
}
