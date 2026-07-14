package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.AlbumView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ArtistView;
import org.shakvilla.beatzmedia.catalog.application.port.in.PlaylistView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ResolveCatalog;
import org.shakvilla.beatzmedia.catalog.application.port.in.ResolvedCatalogView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Application service for the batch catalog resolve endpoint. Delegates to the existing
 * {@code …ByIds} batch reads and view mappers — no new persistence access. Catalog ADD §4.1.
 */
@ApplicationScoped
@Transactional
public class ResolveCatalogService implements ResolveCatalog {

  /** Per-kind id cap; a single list larger than this is rejected rather than silently truncated. */
  private static final int MAX_IDS_PER_KIND = 200;

  private final CatalogRepository catalogRepository;
  private final OwnershipReader ownershipReader;

  @Inject
  public ResolveCatalogService(CatalogRepository catalogRepository, OwnershipReader ownershipReader) {
    this.catalogRepository = catalogRepository;
    this.ownershipReader = ownershipReader;
  }

  @Override
  public ResolvedCatalogView resolve(Command command, Optional<String> callerId) {
    List<String> trackIds = orEmpty(command.trackIds());
    List<String> artistIds = orEmpty(command.artistIds());
    List<String> albumIds = orEmpty(command.albumIds());
    List<String> playlistIds = orEmpty(command.playlistIds());

    requireWithinCap(trackIds, "trackIds");
    requireWithinCap(artistIds, "artistIds");
    requireWithinCap(albumIds, "albumIds");
    requireWithinCap(playlistIds, "playlistIds");

    List<TrackView> tracks = catalogRepository.tracksByIds(trackIds).stream()
        .map(t -> TrackMapper.toView(t, callerId, ownershipReader))
        .toList();

    List<ArtistView> artists = catalogRepository.artistsByIds(artistIds).stream()
        .map(this::toArtistView)
        .toList();

    List<AlbumView> albums = catalogRepository.albumsByIds(albumIds).stream()
        .map(this::toAlbumView)
        .toList();

    // LLFR-CATALOG-01.7 / GetPlaylistService parity: non-public playlists are hidden from every
    // caller (owner-check not yet available — WU-CAT-1 note), so they're omitted, not errored.
    List<PlaylistView> playlists = catalogRepository.playlistsByIds(playlistIds).stream()
        .filter(Playlist::isPublic)
        .map(p -> toPlaylistView(p, callerId))
        .toList();

    return new ResolvedCatalogView(tracks, artists, albums, playlists);
  }

  private static List<String> orEmpty(List<String> ids) {
    return ids == null ? List.of() : ids;
  }

  private static void requireWithinCap(List<String> ids, String field) {
    if (ids.size() > MAX_IDS_PER_KIND) {
      throw new ValidationException(
          field + " must not exceed " + MAX_IDS_PER_KIND + " ids", field);
    }
  }

  private ArtistView toArtistView(ArtistProfile a) {
    return new ArtistView(
        a.getId().value(),
        a.getName(),
        a.getImage(),
        a.getCoverImage(),
        a.isVerified(),
        a.getMonthlyListeners(),
        a.getFollowers(),
        a.getBio(),
        a.getLocation(),
        a.getGenres());
  }

  private AlbumView toAlbumView(Album album) {
    return new AlbumView(
        album.getId().value(),
        album.getTitle(),
        album.getArtistId().value(),
        album.getArtistName(),
        album.getYear(),
        album.getCoverImage(),
        album.getGenres(),
        album.getTrackIds(),
        null);
  }

  private PlaylistView toPlaylistView(Playlist p, Optional<String> callerId) {
    List<TrackView> tracks = catalogRepository.tracksByIds(p.getTrackIds()).stream()
        .map(t -> TrackMapper.toView(t, callerId, ownershipReader))
        .toList();
    return new PlaylistView(
        p.getId().value(),
        p.getTitle(),
        p.getDescription(),
        p.getCreator(),
        p.getCreatorAvatar(),
        p.getImage(),
        p.isPublic(),
        p.getFollowers(),
        p.getTrackIds(),
        tracks);
  }
}
