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
import org.shakvilla.beatzmedia.catalog.domain.SplitEntry;
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

  /**
   * WU-CAT-6 — wholesale-replace one track's collaborator splits in {@code split_entry}. Deletes
   * the track's existing rows and inserts {@code splits} (empty list clears them). Decoupled from
   * {@link #saveRelease} so an unrelated save (upload, scheduler go-live) can never wipe splits.
   */
  void saveTrackSplits(String trackId, List<SplitEntry> splits);

  /** Save a new Track row (used when creating a stub track during upload). */
  void saveTrack(Track track);

  /**
   * Delete a Track row by id. Used when a draft track is removed from its release ({@code DELETE
   * .../tracks/:trackId}) — a no-op if the track no longer exists. WU-CAT-5.
   */
  void deleteTrack(TrackId id);

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
   * A catalog track plus whether it should currently surface in search. {@code visible} is {@code
   * false} when the track's owning release — found via the {@code release_track} join, the
   * authoritative track ↔ release link ({@code track.release_id} is never populated) — exists and
   * is not {@code live} (e.g. {@code draft}, {@code in_review}, {@code scheduled}, {@code
   * takedown}). A track with no owning release at all is always visible. WU-SRCH-2.
   */
  record IndexableTrack(Track track, boolean visible) {}

  /**
   * All tracks eligible for the search index, i.e. those whose audio has finished processing
   * ({@code status = 'ready'}), paired with whether each should currently be visible in search.
   * Reindex is upsert-only, so a track that must be hidden (e.g. after a takedown) is still
   * returned here — with {@code visible=false} — rather than omitted, since omitting it would
   * strand its previous {@code visible=true} document in the index forever. Ordering is
   * unspecified. Used only by the catalog-side search indexer (WU-SRCH-2); not a public listing.
   */
  List<IndexableTrack> allTracksForIndex();

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
