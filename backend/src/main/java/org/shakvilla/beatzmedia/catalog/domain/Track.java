package org.shakvilla.beatzmedia.catalog.domain;

import java.util.List;
import java.util.Optional;

/**
 * Track aggregate root. Intrinsic fields only; per-caller ownership/price decoration is applied at
 * the adapter boundary via the {@link OwnershipReader} output port (WU-CAT-1 stub). Domain-layer;
 * no framework imports. Catalog ADD §3.
 */
public final class Track {

  private final TrackId id;
  private final String title;
  private final ArtistId artistId;
  private final String artistName;
  private final AlbumId albumId;
  private final String albumTitle;
  /** Duration in whole seconds. */
  private final int durationSec;
  /** Square cover art URL. */
  private final String image;
  /** Intrinsic ownership for this track (stored; per-caller decoration via OwnershipReader). */
  private final OwnershipStatus ownership;
  /** Stored price in pesewas; present when ownership=for-sale. */
  private final Long priceMinor;
  private final Long plays;
  private final String audioUrl;
  private final List<TrackCredit> credits;
  private final String quality;
  private final Integer year;
  private final String status;

  public Track(
      TrackId id,
      String title,
      ArtistId artistId,
      String artistName,
      AlbumId albumId,
      String albumTitle,
      int durationSec,
      String image,
      OwnershipStatus ownership,
      Long priceMinor,
      Long plays,
      String audioUrl,
      List<TrackCredit> credits,
      String quality,
      Integer year,
      String status) {
    this.id = id;
    this.title = title;
    this.artistId = artistId;
    this.artistName = artistName;
    this.albumId = albumId;
    this.albumTitle = albumTitle;
    this.durationSec = durationSec;
    this.image = image;
    this.ownership = ownership;
    this.priceMinor = priceMinor;
    this.plays = plays;
    this.audioUrl = audioUrl;
    this.credits = credits;
    this.quality = quality;
    this.year = year;
    this.status = status;
  }

  public TrackId getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public ArtistId getArtistId() {
    return artistId;
  }

  public String getArtistName() {
    return artistName;
  }

  public Optional<AlbumId> getAlbumId() {
    return Optional.ofNullable(albumId);
  }

  public Optional<String> getAlbumTitle() {
    return Optional.ofNullable(albumTitle);
  }

  public int getDurationSec() {
    return durationSec;
  }

  public String getImage() {
    return image;
  }

  public OwnershipStatus getOwnership() {
    return ownership;
  }

  public Optional<Long> getPriceMinor() {
    return Optional.ofNullable(priceMinor);
  }

  public Optional<Long> getPlays() {
    return Optional.ofNullable(plays);
  }

  public Optional<String> getAudioUrl() {
    return Optional.ofNullable(audioUrl);
  }

  public Optional<List<TrackCredit>> getCredits() {
    return Optional.ofNullable(credits);
  }

  public Optional<String> getQuality() {
    return Optional.ofNullable(quality);
  }

  public Optional<Integer> getYear() {
    return Optional.ofNullable(year);
  }

  public String getStatus() {
    return status;
  }
}
