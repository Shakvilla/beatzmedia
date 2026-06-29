package org.shakvilla.beatzmedia.catalog.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.catalog.application.port.in.UploadReleaseTrack;
import org.shakvilla.beatzmedia.catalog.application.port.in.UploadedTrackView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.media.application.port.in.UploadCommand;
import org.shakvilla.beatzmedia.media.application.port.in.UploadOriginalUseCase;
import org.shakvilla.beatzmedia.media.domain.MediaHandle;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.UnauthorizedException;

/**
 * Application service for {@link UploadReleaseTrack}. Delegates the binary upload to the media
 * module's {@link UploadOriginalUseCase}, then creates a stub {@link Track} row with status
 * "uploading". LLFR-CATALOG-02.4.
 */
@ApplicationScoped
public class UploadReleaseTrackService implements UploadReleaseTrack {

  private final CatalogRepository repo;
  private final UploadOriginalUseCase uploadOriginalUseCase;
  private final IdGenerator ids;

  @Inject
  public UploadReleaseTrackService(
      CatalogRepository repo, UploadOriginalUseCase uploadOriginalUseCase, IdGenerator ids) {
    this.repo = repo;
    this.uploadOriginalUseCase = uploadOriginalUseCase;
    this.ids = ids;
  }

  @Override
  @Transactional
  public UploadedTrackView upload(ReleaseId releaseId, ArtistId artistId, AudioUpload upload) {
    Release release = repo.findRelease(releaseId)
        .orElseThrow(() -> new ReleaseNotFoundException(releaseId.value()));
    if (!release.getArtistId().equals(artistId.value())) {
      throw new UnauthorizedException("Not your release");
    }

    String trackId = ids.newId();
    String title = filenameWithoutExtension(upload.filename());

    OwnerRef ownerRef = new OwnerRef("catalog", trackId);
    UploadCommand cmd = new UploadCommand(
        ownerRef,
        MediaKind.AUDIO,
        upload.filename(),
        upload.contentType(),
        upload.sizeBytes(),
        upload.body(),
        upload.contentHash());

    MediaHandle handle = uploadOriginalUseCase.uploadOriginal(cmd);

    // Persist stub track in catalog (status = "uploading")
    Track stubTrack = new Track(
        new TrackId(trackId),
        title,
        artistId,
        null, // artistName resolved later
        null, // albumId
        null, // albumTitle
        handle.durationSec(),
        "/images/placeholder.jpg",
        OwnershipStatus.free,
        null, // priceMinor
        0L,
        null, // audioUrl — set after transcoding
        null, // credits
        null, // quality
        null, // year
        "uploading");

    repo.saveTrack(stubTrack);

    return new UploadedTrackView(
        trackId,
        title,
        handle.durationSec(),
        "uploading",
        0,
        null,
        MoneyView.ofMinor(0L),
        false);
  }

  private String filenameWithoutExtension(String filename) {
    if (filename == null) return "Untitled";
    int dot = filename.lastIndexOf('.');
    return dot > 0 ? filename.substring(0, dot) : filename;
  }
}
