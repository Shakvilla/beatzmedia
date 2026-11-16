package org.shakvilla.beatzmedia.studio.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.media.application.port.in.UploadCommand;
import org.shakvilla.beatzmedia.media.application.port.in.UploadOriginalUseCase;
import org.shakvilla.beatzmedia.media.application.port.out.MediaService;
import org.shakvilla.beatzmedia.media.domain.MediaHandle;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.studio.application.port.in.SetStudioProfileImage;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.StudioProfile;
import org.shakvilla.beatzmedia.studio.domain.StudioProfileImagesUpdated;

/** Stores profile artwork and persists its stable public image URL on the artist's profile. */
@ApplicationScoped
public class SetStudioProfileImageService implements SetStudioProfileImage {

  private static final String IMAGE_URL_PREFIX = "/v1/media/images/";

  private final StudioRepository repository;
  private final UploadOriginalUseCase upload;
  private final MediaService media;
  private final Clock clock;
  private final Event<StudioProfileImagesUpdated> imagesUpdatedEvent;

  @Inject
  public SetStudioProfileImageService(
      StudioRepository repository,
      UploadOriginalUseCase upload,
      MediaService media,
      Clock clock,
      Event<StudioProfileImagesUpdated> imagesUpdatedEvent) {
    this.repository = repository;
    this.upload = upload;
    this.media = media;
    this.clock = clock;
    this.imagesUpdatedEvent = imagesUpdatedEvent;
  }

  @Override
  @Transactional
  public String upload(ArtistId artist, ImageType type, ImageUpload image) {
    MediaHandle handle = upload.uploadOriginal(new UploadCommand(
        new OwnerRef("studio", artist.value()),
        MediaKind.ARTWORK,
        image.filename(),
        image.contentType(),
        image.sizeBytes(),
        image.body(),
        image.contentHash()));
    media.processArtwork(handle.assetId());

    String url = IMAGE_URL_PREFIX + handle.assetId().value();
    StudioProfile current = repository.findProfile(artist).orElseGet(() -> StudioProfile.blank(artist));
    var updatedAt = clock.now();
    StudioProfile updated = new StudioProfile(
        artist,
        current.username(),
        current.displayName(),
        current.hometown(),
        current.genres(),
        current.bio(),
        type == ImageType.AVATAR ? url : current.avatarUrl(),
        type == ImageType.BANNER ? url : current.bannerUrl(),
        current.links(),
        current.shows(),
        current.featuredTrackId(),
        current.bookingEmail(),
        current.pressAssets(),
        updatedAt);
    repository.saveProfile(updated);
    imagesUpdatedEvent.fire(new StudioProfileImagesUpdated(
        artist.value(), updated.avatarUrl(), updated.bannerUrl(), updatedAt));
    return url;
  }
}
