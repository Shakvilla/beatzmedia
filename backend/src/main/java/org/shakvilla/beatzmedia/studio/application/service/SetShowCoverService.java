package org.shakvilla.beatzmedia.studio.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.media.application.port.in.UploadCommand;
import org.shakvilla.beatzmedia.media.application.port.out.MediaService;
import org.shakvilla.beatzmedia.media.application.port.in.UploadOriginalUseCase;
import org.shakvilla.beatzmedia.media.domain.MediaHandle;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;
import org.shakvilla.beatzmedia.studio.application.port.in.SetShowCover;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;
import org.shakvilla.beatzmedia.studio.domain.ShowId;

/**
 * Uploads a show's cover art and points the show at it.
 *
 * <p>Artwork is processed inline rather than queued. Unlike audio — where ffmpeg can take minutes
 * and the upload returns {@code UPLOADING} — image variants are cheap, and the artist needs the URL
 * back in the same request to render a preview. Publishing also gates on the cover being present,
 * so an eventually-consistent cover would let an episode publish into a show whose art had not
 * landed yet.
 */
@ApplicationScoped
public class SetShowCoverService implements SetShowCover {

  /** Path the stored URL takes. Kept relative so a CDN can be swapped in behind it later. */
  private static final String IMAGE_URL_PREFIX = "/v1/media/images/";

  private final StudioRepository repo;
  private final UploadOriginalUseCase upload;
  private final MediaService media;

  @Inject
  public SetShowCoverService(
      StudioRepository repo, UploadOriginalUseCase upload, MediaService media) {
    this.repo = repo;
    this.upload = upload;
    this.media = media;
  }

  @Override
  @Transactional
  public String setCover(ArtistId artist, ShowId showId, ImageUpload image) {
    PodcastShow show =
        repo.findShow(artist, showId)
            .orElseThrow(() -> new NotFoundException("Show not found: " + showId.value()));

    // Format is decided by media's magic-byte sniffing, not by the declared content type — a client
    // can claim image/png for anything.
    MediaHandle handle = upload.uploadOriginal(new UploadCommand(
        new OwnerRef("studio", showId.value()),
        MediaKind.ARTWORK,
        image.filename(),
        image.contentType(),
        image.sizeBytes(),
        image.body(),
        image.contentHash()));

    // Copies into the delivery bucket and marks the asset ready; until this runs there is nothing
    // to serve and the image endpoint would 404.
    media.processArtwork(handle.assetId());

    String url = IMAGE_URL_PREFIX + handle.assetId().value();
    repo.saveShow(show.withArtwork(url, show.description()));
    return url;
  }
}
