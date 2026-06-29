package org.shakvilla.beatzmedia.catalog.application.port.out;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.BrowseCategory;
import org.shakvilla.beatzmedia.catalog.domain.Lyrics;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;
import org.shakvilla.beatzmedia.catalog.domain.PlaylistId;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Output port: catalog read access. Implemented by the JPA/Panache persistence adapter. Catalog
 * ADD §4.2.
 */
public interface CatalogRepository {

  Optional<ArtistProfile> findArtist(ArtistId id);

  List<Track> tracksByArtist(ArtistId id);

  List<Album> albumsByArtist(ArtistId id);

  Optional<Album> findAlbum(AlbumId id);

  List<Track> tracksByAlbum(AlbumId id);

  Optional<Track> findTrack(TrackId id);

  Optional<Lyrics> findLyrics(TrackId id);

  Optional<Playlist> findPlaylist(PlaylistId id);

  List<Track> tracksByIds(List<String> ids);

  // ---- WU-CAT-2: home feed + browse ----

  List<BrowseCategory> browseCategories();

  List<Track> trendingTracks(int limit);

  List<Track> top10Tracks(int limit);

  List<Album> featuredAlbums(int limit);

  List<Album> albumsByIds(List<String> ids);

  List<ArtistProfile> artistsByIds(List<String> ids);

  List<Playlist> playlistsByIds(List<String> ids);

  // ---- WU-CAT-3: studio release lifecycle ----

  Page<Release> releasesByArtist(ArtistId owner, Optional<ReleaseStatus> status, PageRequest pageRequest);

  Optional<Release> findRelease(ReleaseId id);

  void saveRelease(Release release);

  void deleteRelease(ReleaseId id);

  /** Save a new Track row (used when creating a stub track during upload). */
  void saveTrack(Track track);

  /** Check whether an idempotency key was already used; returns the saved view if so. */
  Optional<Release> findReleaseByIdempotencyKey(String idempotencyKey);
}
