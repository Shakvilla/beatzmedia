package org.shakvilla.beatzmedia.catalog.domain;

import java.util.List;

/**
 * Album aggregate root. Catalog ADD §3. List price (INV-5) is stored in {@code listPriceMinor} and
 * recomputed by the application layer on release operations (WU-CAT-3+). Domain-layer; no framework
 * imports.
 */
public final class Album {

  private final AlbumId id;
  private final String title;
  private final ArtistId artistId;
  private final String artistName;
  private final int year;
  private final String coverImage;
  private final List<String> genres;
  /** Ordered track ids belonging to this album. */
  private final List<String> trackIds;
  /** INV-5: stored list price in pesewas; 0 if not priced yet. */
  private final long listPriceMinor;
  /**
   * Whether this album's release permits buyer downloads. An album shares its id with the
   * release that projected it (see {@code ProjectReleaseAlbumService}), so it is sourced by a
   * read-time lookup of that same release's {@code downloadable} rather than a stored/denormalized
   * column — a denormalized snapshot would go stale the moment the artist changes the choice after
   * publish, which {@code UpdateReleaseService} allows on any status.
   */
  private final boolean downloadable;

  /** Legacy constructor — defaults {@code downloadable} to {@code false}; see the full ctor. */
  public Album(
      AlbumId id,
      String title,
      ArtistId artistId,
      String artistName,
      int year,
      String coverImage,
      List<String> genres,
      List<String> trackIds,
      long listPriceMinor) {
    this(id, title, artistId, artistName, year, coverImage, genres, trackIds, listPriceMinor, false);
  }

  public Album(
      AlbumId id,
      String title,
      ArtistId artistId,
      String artistName,
      int year,
      String coverImage,
      List<String> genres,
      List<String> trackIds,
      long listPriceMinor,
      boolean downloadable) {
    this.id = id;
    this.title = title;
    this.artistId = artistId;
    this.artistName = artistName;
    this.year = year;
    this.coverImage = coverImage;
    this.genres = genres;
    this.trackIds = trackIds;
    this.listPriceMinor = listPriceMinor;
    this.downloadable = downloadable;
  }

  public AlbumId getId() {
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

  public int getYear() {
    return year;
  }

  public String getCoverImage() {
    return coverImage;
  }

  public List<String> getGenres() {
    return genres;
  }

  public List<String> getTrackIds() {
    return trackIds;
  }

  public long getListPriceMinor() {
    return listPriceMinor;
  }

  /** See the {@code downloadable} field javadoc. */
  public boolean isDownloadable() {
    return downloadable;
  }
}
