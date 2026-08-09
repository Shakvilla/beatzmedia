package org.shakvilla.beatzmedia.catalog.adapter.in.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.application.port.in.ProjectReleaseAlbum;
import org.shakvilla.beatzmedia.catalog.domain.ContentTakenDown;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseWentLive;

/**
 * Keeps the {@code album} row in step with a release going live and coming down.
 *
 * <p>Listens to the same two events as the search projection, for the same reason: both the
 * immediate admin approval and the scheduled go-live sweep publish {@link ReleaseWentLive}, so
 * projecting from the event covers both paths without either having to know about albums.
 *
 * <p>{@code AFTER_SUCCESS} so nothing is projected for a transition that rolled back. The service
 * opens its own transaction ({@code REQUIRES_NEW}) because an AFTER_SUCCESS observer runs with none
 * active.
 */
@ApplicationScoped
public class ReleaseAlbumProjectionObserver {

  private final ProjectReleaseAlbum projector;

  @Inject
  public ReleaseAlbumProjectionObserver(ProjectReleaseAlbum projector) {
    this.projector = projector;
  }

  public void onReleaseWentLive(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) ReleaseWentLive event) {
    projector.project(event.releaseId());
  }

  public void onContentTakenDown(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) ContentTakenDown event) {
    projector.remove(event.releaseId());
  }
}
