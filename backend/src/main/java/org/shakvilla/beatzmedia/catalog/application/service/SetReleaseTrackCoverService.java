package org.shakvilla.beatzmedia.catalog.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.SetReleaseTrackCover;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.application.port.out.TrackSearchProjection;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.media.application.port.in.UploadCommand;
import org.shakvilla.beatzmedia.media.application.port.in.UploadOriginalUseCase;
import org.shakvilla.beatzmedia.media.application.port.out.MediaService;
import org.shakvilla.beatzmedia.media.domain.MediaHandle;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;

/** Stores track artwork without allowing a later release-cover edit to overwrite it. */
@ApplicationScoped
public class SetReleaseTrackCoverService implements SetReleaseTrackCover {

  private static final String IMAGE_URL_PREFIX = "/v1/media/images/";

  private final CatalogRepository repository;
  private final UploadOriginalUseCase upload;
  private final MediaService media;
  private final TrackSearchProjection search;

  @Inject
  public SetReleaseTrackCoverService(
      CatalogRepository repository,
      UploadOriginalUseCase upload,
      MediaService media,
      TrackSearchProjection search) {
    this.repository = repository;
    this.upload = upload;
    this.media = media;
    this.search = search;
  }

  @Override
  @Transactional
  public String setCover(ArtistId artist, ReleaseId releaseId, TrackId trackId, ImageUpload image) {
    Release release = repository.findRelease(releaseId)
        .orElseThrow(() -> new ReleaseNotFoundException(releaseId.value()));
    if (!release.getArtistId().equals(artist.value())
        || release.getTracks().stream().map(ReleaseTrack::trackId).noneMatch(trackId.value()::equals)) {
      throw new NotFoundException("Release track not found");
    }
    if (release.getStatus() == ReleaseStatus.live || release.getStatus() == ReleaseStatus.scheduled) {
      throw new IllegalTransitionException(release.getStatus(), "SET_TRACK_COVER_DIRECTLY");
    }
    Track track = repository.findTrack(trackId)
        .orElseThrow(() -> new NotFoundException("Release track not found"));

    MediaHandle handle = upload.uploadOriginal(new UploadCommand(
        new OwnerRef("catalog", trackId.value()),
        MediaKind.ARTWORK,
        image.filename(),
        image.contentType(),
        image.sizeBytes(),
        image.body(),
        image.contentHash()));
    media.processArtwork(handle.assetId());

    String url = IMAGE_URL_PREFIX + handle.assetId().value();
    Track updated = track.withImage(url);
    repository.saveTrack(updated);
    search.index(updated, release.getStatus() == ReleaseStatus.live);
    return url;
  }
}
