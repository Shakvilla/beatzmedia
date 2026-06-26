package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.GetPlaylist;
import org.shakvilla.beatzmedia.catalog.application.port.in.PlaylistView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;
import org.shakvilla.beatzmedia.catalog.domain.PlaylistId;
import org.shakvilla.beatzmedia.catalog.domain.PlaylistNotFoundException;

/**
 * Application service for LLFR-CATALOG-01.7 (playlist detail). Private playlists accessed by
 * non-owners return 404 (existence hidden). Catalog ADD §4.1.
 */
@ApplicationScoped
@Transactional
public class GetPlaylistService implements GetPlaylist {

  private final CatalogRepository catalogRepository;
  private final OwnershipReader ownershipReader;

  @Inject
  public GetPlaylistService(CatalogRepository catalogRepository, OwnershipReader ownershipReader) {
    this.catalogRepository = catalogRepository;
    this.ownershipReader = ownershipReader;
  }

  @Override
  public PlaylistView get(PlaylistId id, Optional<String> callerId) {
    Playlist playlist = catalogRepository.findPlaylist(id)
        .orElseThrow(() -> new PlaylistNotFoundException(id.value()));

    // LLFR-CATALOG-01.7: private playlist → 404 for ALL callers (existence hidden).
    // WU-CAT-1: "creator" is a display name, not an account id — a real owner check is impossible
    // here. Authenticated-owner access will be unblocked by WU-LIB-1 once the library module
    // provides an owner-check port. Until then every caller (anonymous AND authenticated) receives
    // 404 for a non-public playlist.
    if (!playlist.isPublic()) {
      throw new PlaylistNotFoundException(id.value());
    }

    List<TrackView> trackViews = catalogRepository.tracksByIds(playlist.getTrackIds()).stream()
        .map(t -> TrackMapper.toView(t, callerId, ownershipReader))
        .toList();

    return new PlaylistView(
        playlist.getId().value(),
        playlist.getTitle(),
        playlist.getDescription(),
        playlist.getCreator(),
        playlist.getCreatorAvatar(),
        playlist.getImage(),
        playlist.isPublic(),
        playlist.getFollowers(),
        playlist.getTrackIds(),
        trackViews);
  }
}
