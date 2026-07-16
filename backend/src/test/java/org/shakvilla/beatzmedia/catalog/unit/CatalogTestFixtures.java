package org.shakvilla.beatzmedia.catalog.unit;

import java.util.List;

import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;
import org.shakvilla.beatzmedia.catalog.domain.PlaylistId;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/**
 * Minimal domain-object builders for catalog unit tests that don't need a full-fidelity aggregate
 * — just real constructors with sane defaults so tests only specify what they're asserting on.
 * WU-SRCH-2.
 */
final class CatalogTestFixtures {

  private CatalogTestFixtures() {}

  static Track track(String id, String title, String artistId, String artistName, Long plays) {
    return track(id, title, artistId, artistName, plays, null);
  }

  /** Overload for tests that need to pin a price (pesewas), e.g. exercising the putPrice branch. */
  static Track track(
      String id, String title, String artistId, String artistName, Long plays, Long priceMinor) {
    return new Track(
        new TrackId(id),
        title,
        new ArtistId(artistId),
        artistName,
        null,
        null,
        200,
        "https://img.test/track.jpg",
        priceMinor == null ? OwnershipStatus.free : OwnershipStatus.for_sale,
        priceMinor,
        plays,
        null,
        null,
        "hd",
        2023,
        "ready");
  }

  static ArtistProfile artist(String id, String name, Long monthlyListeners) {
    return new ArtistProfile(
        new ArtistId(id),
        name,
        "https://img.test/artist.jpg",
        null,
        false,
        monthlyListeners,
        0L,
        null,
        "Accra, Ghana",
        List.of("Afrobeats"),
        List.of());
  }

  static Album album(String id, String title, String artistId, String artistName) {
    return new Album(
        new AlbumId(id),
        title,
        new ArtistId(artistId),
        artistName,
        2023,
        "https://img.test/album.jpg",
        List.of("Hip Hop"),
        List.of(),
        500L);
  }

  static Playlist playlist(String id, String title, String creator, boolean isPublic, Long followers) {
    return new Playlist(
        new PlaylistId(id), title, null, creator, null, "https://img.test/playlist.jpg", isPublic, followers,
        List.of());
  }
}
