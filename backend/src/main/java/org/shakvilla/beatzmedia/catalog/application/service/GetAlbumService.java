package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.AlbumView;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetAlbum;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.catalog.domain.AlbumNotFoundException;

/**
 * Application service for LLFR-CATALOG-01.5 (album detail + optional track embedding). Catalog
 * ADD §4.1.
 */
@ApplicationScoped
@Transactional
public class GetAlbumService implements GetAlbum {

  private final CatalogRepository catalogRepository;
  private final OwnershipReader ownershipReader;

  @Inject
  public GetAlbumService(CatalogRepository catalogRepository, OwnershipReader ownershipReader) {
    this.catalogRepository = catalogRepository;
    this.ownershipReader = ownershipReader;
  }

  @Override
  public AlbumView get(AlbumId id, boolean includeTracks, Optional<String> callerId) {
    Album album = catalogRepository.findAlbum(id)
        .orElseThrow(() -> new AlbumNotFoundException(id.value()));

    List<TrackView> trackViews = null;
    if (includeTracks) {
      trackViews = catalogRepository.tracksByAlbum(id).stream()
          .map(t -> TrackMapper.toView(t, callerId, ownershipReader))
          .toList();
    }

    return new AlbumView(
        album.getId().value(),
        album.getTitle(),
        album.getArtistId().value(),
        album.getArtistName(),
        album.getYear(),
        album.getCoverImage(),
        album.getGenres(),
        album.getTrackIds(),
        trackViews);
  }
}
