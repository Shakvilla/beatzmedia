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
    this.id = id;
    this.title = title;
    this.artistId = artistId;
    this.artistName = artistName;
    this.year = year;
    this.coverImage = coverImage;
    this.genres = genres;
    this.trackIds = trackIds;
    this.listPriceMinor = listPriceMinor;
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
}
