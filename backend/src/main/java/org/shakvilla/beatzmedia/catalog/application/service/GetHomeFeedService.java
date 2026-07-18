package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.AlbumView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ArtistView;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetHomeFeed;
import org.shakvilla.beatzmedia.catalog.application.port.in.HomeFeedView;
import org.shakvilla.beatzmedia.catalog.application.port.in.PlaylistView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;

/**
 * Application service for LLFR-CATALOG-01.1 (home feed). Catalog ADD §4.1, WU-CAT-2.
 */
@ApplicationScoped
@Transactional
public class GetHomeFeedService implements GetHomeFeed {

  private static final int TRENDING_LIMIT = 10;
  private static final int TOP10_LIMIT = 10;
  private static final int FEATURED_ALBUMS_LIMIT = 6;
  private static final int NEW_RELEASES_LIMIT = 10;
  private static final int POPULAR_ARTISTS_LIMIT = 10;
  private static final int CURATED_PLAYLISTS_LIMIT = 6;

  private final CatalogRepository catalogRepository;
  private final OwnershipReader ownershipReader;

  @Inject
  public GetHomeFeedService(CatalogRepository catalogRepository, OwnershipReader ownershipReader) {
    this.catalogRepository = catalogRepository;
    this.ownershipReader = ownershipReader;
  }

  @Override
  public HomeFeedView get(Optional<String> callerId) {
    List<TrackView> trending = catalogRepository.trendingTracks(TRENDING_LIMIT).stream()
        .map(t -> TrackMapper.toView(t, callerId, ownershipReader))
        .toList();

    List<TrackView> top10 = catalogRepository.top10Tracks(TOP10_LIMIT).stream()
        .map(t -> TrackMapper.toView(t, callerId, ownershipReader))
        .toList();

    List<AlbumView> featuredAlbums = catalogRepository.featuredAlbums(FEATURED_ALBUMS_LIMIT).stream()
        .map(this::toAlbumView)
        .toList();

    HomeFeedView.RailsView rails = new HomeFeedView.RailsView(
        catalogRepository.newestAlbums(NEW_RELEASES_LIMIT).stream().map(this::toAlbumView).toList(),
        catalogRepository.popularArtists(POPULAR_ARTISTS_LIMIT).stream().map(this::toArtistView).toList(),
        catalogRepository.curatedPlaylists(CURATED_PLAYLISTS_LIMIT).stream().map(this::toPlaylistView).toList());

    return new HomeFeedView(trending, top10, featuredAlbums, rails);
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

  private ArtistView toArtistView(ArtistProfile a) {
    return new ArtistView(
        a.getId().value(), a.getName(), a.getImage(), a.getCoverImage(), a.isVerified(),
        a.getMonthlyListeners(), a.getFollowers(), a.getBio(), a.getLocation(), a.getGenres());
  }

  private PlaylistView toPlaylistView(Playlist p) {
    // List-summary: carry trackIds; embed tracks only in the detail endpoint.
    return new PlaylistView(
        p.getId().value(), p.getTitle(), p.getDescription(), p.getCreator(), p.getCreatorAvatar(),
        p.getImage(), p.isPublic(), p.getFollowers(), p.getTrackIds(), List.of());
  }
}
