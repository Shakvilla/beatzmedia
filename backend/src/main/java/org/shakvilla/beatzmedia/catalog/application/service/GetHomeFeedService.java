package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.AlbumView;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetHomeFeed;
import org.shakvilla.beatzmedia.catalog.application.port.in.HomeFeedView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.catalog.domain.Album;

/**
 * Application service for LLFR-CATALOG-01.1 (home feed). Catalog ADD §4.1, WU-CAT-2.
 */
@ApplicationScoped
@Transactional
public class GetHomeFeedService implements GetHomeFeed {

  private static final int TRENDING_LIMIT = 10;
  private static final int TOP10_LIMIT = 10;
  private static final int FEATURED_ALBUMS_LIMIT = 6;

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

    return new HomeFeedView(trending, top10, featuredAlbums);
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
}
