package org.shakvilla.beatzmedia.catalog.application.port.out;

import java.time.Instant;
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

  /** Save a release and associate it with the given idempotency key. */
  void saveReleaseWithIdempotencyKey(Release release, String idempotencyKey);

  // ---- WU-CAT-4: release state machine + scheduled go-live ----

  /**
   * True if any {@code split_entry} row for a track on this release is still
   * {@code confirmation = 'pending'}. Guards the {@code live} transition (INV-12): a release
   * cannot go live while any split is unconfirmed.
   */
  boolean hasPendingSplits(ReleaseId releaseId);

  /**
   * Returns all releases in status {@code scheduled} whose {@code scheduled_at <= now}. Used by
   * the go-live sweep (INV-7). Backed by {@code idx_release_due} (V305).
   */
  List<Release> dueScheduled(Instant now);

  /**
   * Flips every track belonging to this release from {@code uploading}/{@code error} to
   * {@code ready} so they become publicly streamable, as part of the go-live / immediate-approve
   * transition. A no-op for tracks already {@code ready}.
   */
  void markReleaseTracksReady(ReleaseId releaseId);

  // ---- WU-SRCH-2: search index backfill ----

  /**
   * All tracks eligible for the search index, i.e. those whose audio has finished processing and
   * which are not gated behind a non-live release. Ordering is unspecified. Used only by the
   * catalog-side search indexer (WU-SRCH-2); not a public listing.
   */
  List<Track> allTracksForIndex();

  /** All artist profiles, for the search indexer (WU-SRCH-2). Ordering is unspecified. */
  List<ArtistProfile> allArtistsForIndex();

  /** All albums, for the search indexer (WU-SRCH-2). Ordering is unspecified. */
  List<Album> allAlbumsForIndex();

  /**
   * All playlists — including private ones, so the indexer can write them with {@code
   * visible=false} rather than omitting them (reindex is upsert-only). For the search indexer
   * (WU-SRCH-2).
   */
  List<Playlist> allPlaylistsForIndex();
}
