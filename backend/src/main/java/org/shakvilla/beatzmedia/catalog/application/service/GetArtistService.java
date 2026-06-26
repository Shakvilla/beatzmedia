package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.application.port.in.AlbumView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ArtistView;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetArtist;
import org.shakvilla.beatzmedia.catalog.application.port.in.ShowView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;

/**
 * Application service for LLFR-CATALOG-01.4 (artist profile and sub-collections). Catalog ADD
 * §4.1.
 */
@ApplicationScoped
public class GetArtistService implements GetArtist {

  private final CatalogRepository catalogRepository;
  private final OwnershipReader ownershipReader;

  @Inject
  public GetArtistService(CatalogRepository catalogRepository, OwnershipReader ownershipReader) {
    this.catalogRepository = catalogRepository;
    this.ownershipReader = ownershipReader;
  }

  @Override
  public ArtistView getArtist(ArtistId id) {
    ArtistProfile artist = catalogRepository.findArtist(id)
        .orElseThrow(() -> new ArtistNotFoundException(id.value()));
    return toView(artist);
  }

  @Override
  public List<TrackView> tracks(ArtistId id, Optional<String> callerId) {
    // Guard: ensure the artist exists before fetching tracks.
    catalogRepository.findArtist(id)
        .orElseThrow(() -> new ArtistNotFoundException(id.value()));
    return catalogRepository.tracksByArtist(id).stream()
        .map(t -> TrackMapper.toView(t, callerId, ownershipReader))
        .toList();
  }

  @Override
  public List<AlbumView> albums(ArtistId id) {
    catalogRepository.findArtist(id)
        .orElseThrow(() -> new ArtistNotFoundException(id.value()));
    return catalogRepository.albumsByArtist(id).stream()
        .map(a -> new AlbumView(
            a.getId().value(),
            a.getTitle(),
            a.getArtistId().value(),
            a.getArtistName(),
            a.getYear(),
            a.getCoverImage(),
            a.getGenres(),
            a.getTrackIds(),
            null))
        .toList();
  }

  @Override
  public List<ShowView> shows(ArtistId id) {
    ArtistProfile artist = catalogRepository.findArtist(id)
        .orElseThrow(() -> new ArtistNotFoundException(id.value()));
    return artist.getShows().stream()
        .map(s -> new ShowView(s.date(), s.city(), s.venue()))
        .toList();
  }

  private ArtistView toView(ArtistProfile artist) {
    return new ArtistView(
        artist.getId().value(),
        artist.getName(),
        artist.getImage(),
        artist.getCoverImage(),
        artist.isVerified(),
        artist.getMonthlyListeners(),
        artist.getFollowers(),
        artist.getBio(),
        artist.getLocation(),
        artist.getGenres());
  }
}
