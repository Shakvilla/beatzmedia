package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.AlbumView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ArtistView;
import org.shakvilla.beatzmedia.catalog.application.port.in.PlaylistView;
import org.shakvilla.beatzmedia.catalog.application.port.in.Search;
import org.shakvilla.beatzmedia.catalog.application.port.in.SearchResultsView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TopResultView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.MissingQueryException;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.search.application.port.in.QueryService;
import org.shakvilla.beatzmedia.search.domain.SearchHit;
import org.shakvilla.beatzmedia.search.domain.SearchQuery;
import org.shakvilla.beatzmedia.search.domain.SearchResults;

/**
 * Application service for LLFR-CATALOG-01.2 (search read). Delegates FTS to the search module's
 * {@link QueryService}, then hydrates entity ids against the catalog repository. Catalog ADD §4.1,
 * WU-CAT-2.
 */
@ApplicationScoped
@Transactional
public class SearchService implements Search {

  private final QueryService queryService;
  private final CatalogRepository catalogRepository;
  private final OwnershipReader ownershipReader;

  @Inject
  public SearchService(
      QueryService queryService,
      CatalogRepository catalogRepository,
      OwnershipReader ownershipReader) {
    this.queryService = queryService;
    this.catalogRepository = catalogRepository;
    this.ownershipReader = ownershipReader;
  }

  @Override
  public SearchResultsView search(String query, Optional<String> callerId) {
    if (query == null || query.isBlank()) {
      throw new MissingQueryException();
    }

    SearchResults results = queryService.search(SearchQuery.of(query));

    // Hydrate tracks
    List<String> trackIds = ids(results.tracks());
    Map<String, Track> tracksById = catalogRepository.tracksByIds(trackIds).stream()
        .collect(Collectors.toMap(t -> t.getId().value(), t -> t));
    List<TrackView> trackViews = results.tracks().stream()
        .map(hit -> tracksById.get(hit.entityId()))
        .filter(t -> t != null)
        .map(t -> TrackMapper.toView(t, callerId, ownershipReader))
        .toList();

    // Hydrate artists
    List<String> artistIds = ids(results.artists());
    Map<String, ArtistProfile> artistsById = catalogRepository.artistsByIds(artistIds).stream()
        .collect(Collectors.toMap(a -> a.getId().value(), a -> a));
    List<ArtistView> artistViews = results.artists().stream()
        .map(hit -> artistsById.get(hit.entityId()))
        .filter(a -> a != null)
        .map(this::toArtistView)
        .toList();

    // Hydrate albums
    List<String> albumIds = ids(results.albums());
    Map<String, Album> albumsById = catalogRepository.albumsByIds(albumIds).stream()
        .collect(Collectors.toMap(a -> a.getId().value(), a -> a));
    List<AlbumView> albumViews = results.albums().stream()
        .map(hit -> albumsById.get(hit.entityId()))
        .filter(a -> a != null)
        .map(this::toAlbumView)
        .toList();

    // Hydrate playlists
    List<String> playlistIds = ids(results.playlists());
    Map<String, Playlist> playlistsById = catalogRepository.playlistsByIds(playlistIds).stream()
        .collect(Collectors.toMap(p -> p.getId().value(), p -> p));
    List<PlaylistView> playlistViews = results.playlists().stream()
        .map(hit -> playlistsById.get(hit.entityId()))
        .filter(p -> p != null)
        .map(this::toPlaylistView)
        .toList();

    // Map top result
    Optional<TopResultView> topResult = results.topResult()
        .map(this::toTopResultView);

    return new SearchResultsView(trackViews, artistViews, albumViews, playlistViews, topResult);
  }

  private List<String> ids(List<SearchHit> hits) {
    return hits.stream().map(SearchHit::entityId).toList();
  }

  private TopResultView toTopResultView(SearchHit hit) {
    return new TopResultView(
        hit.entityType().name(),
        hit.entityId(),
        hit.title(),
        hit.subtitle(),
        hit.payload());
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

  private PlaylistView toPlaylistView(Playlist p) {
    // Search results carry trackIds only; the detail endpoint embeds tracks. Hydrating them here
    // would cost an ownership lookup per track of every matching playlist on a debounced hot path.
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
        List.of());
  }
}
