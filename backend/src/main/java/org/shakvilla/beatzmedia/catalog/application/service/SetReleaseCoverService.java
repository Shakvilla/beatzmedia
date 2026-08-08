package org.shakvilla.beatzmedia.catalog.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.catalog.application.port.in.SetReleaseCover;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.media.application.port.in.UploadCommand;
import org.shakvilla.beatzmedia.media.application.port.in.UploadOriginalUseCase;
import org.shakvilla.beatzmedia.media.application.port.out.MediaService;
import org.shakvilla.beatzmedia.media.domain.MediaHandle;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;

/**
 * Uploads a release's cover art and applies it to the release and its tracks.
 *
 * <p>The wizard has always had a cover picker, but the image never left the browser — it held a
 * {@code blob:} object URL and no request ever carried the file, so "Add cover art before
 * submitting" was satisfied by something that did not exist server-side. This is the missing half.
 *
 * <p>Artwork is processed inline rather than queued: image variants are cheap, and the artist needs
 * the URL back in the same request to render a preview.
 */
@ApplicationScoped
public class SetReleaseCoverService implements SetReleaseCover {

  private static final Logger LOG = Logger.getLogger(SetReleaseCoverService.class);

  /** Kept relative so a CDN can be swapped in behind it without rewriting stored values. */
  private static final String IMAGE_URL_PREFIX = "/v1/media/images/";

  private final CatalogRepository repo;
  private final UploadOriginalUseCase upload;
  private final MediaService media;

  @Inject
  public SetReleaseCoverService(
      CatalogRepository repo, UploadOriginalUseCase upload, MediaService media) {
    this.repo = repo;
    this.upload = upload;
    this.media = media;
  }

  @Override
  @Transactional
  public String setCover(ArtistId artist, ReleaseId releaseId, ImageUpload image) {
    Release release =
        repo.findRelease(releaseId)
            .orElseThrow(() -> new NotFoundException("Release not found: " + releaseId.value()));
    if (!release.getArtistId().equals(artist.value())) {
      // Same message as a genuine miss: an artist must not be able to probe for other artists' ids.
      throw new NotFoundException("Release not found: " + releaseId.value());
    }

    // Format is decided by media's magic-byte sniffing, not the declared content type.
    MediaHandle handle = upload.uploadOriginal(new UploadCommand(
        new OwnerRef("catalog", releaseId.value()),
        MediaKind.ARTWORK,
        image.filename(),
        image.contentType(),
        image.sizeBytes(),
        image.body(),
        image.contentHash()));

    // Copies into the delivery bucket and marks the asset ready; until this runs the image
    // endpoint has nothing to serve.
    media.processArtwork(handle.assetId());

    String url = IMAGE_URL_PREFIX + handle.assetId().value();
    release.setCoverImage(url);
    repo.saveRelease(release);

    // Stamp it onto the tracks. track.image is what fans actually see, and every uploaded track
    // carries '/images/placeholder.jpg' until something replaces it.
    int updated = 0;
    for (ReleaseTrack rt : release.getTracks()) {
      Track track = repo.findTrack(new TrackId(rt.trackId())).orElse(null);
      if (track == null) {
        continue;
      }
      Track withArt = track.withImage(url);
      if (withArt != track) {
        repo.saveTrack(withArt);
        updated++;
      }
    }

    LOG.infof("catalog: release %s cover set, %d track(s) restamped", releaseId.value(), updated);
    return url;
  }
}
