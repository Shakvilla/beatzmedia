package org.shakvilla.beatzmedia.catalog.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.AlbumView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ArtistView;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetArtist;
import org.shakvilla.beatzmedia.catalog.application.port.in.ShowView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.application.port.out.ArtistFollowerCountReader;
import org.shakvilla.beatzmedia.catalog.application.port.out.ArtistMonthlyListenerReader;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Application service for LLFR-CATALOG-01.4 (artist profile and sub-collections). Catalog ADD
 * §4.1.
 */
@ApplicationScoped
@Transactional
public class GetArtistService implements GetArtist {

  private static final Duration LISTENER_WINDOW = Duration.ofDays(28);

  private final CatalogRepository catalogRepository;
  private final OwnershipReader ownershipReader;
  private final ArtistFollowerCountReader followerCountReader;
  private final ArtistMonthlyListenerReader monthlyListenerReader;
  private final Clock clock;

  @Inject
  public GetArtistService(
      CatalogRepository catalogRepository, OwnershipReader ownershipReader,
      ArtistFollowerCountReader followerCountReader,
      ArtistMonthlyListenerReader monthlyListenerReader,
      Clock clock) {
    this.catalogRepository = catalogRepository;
    this.ownershipReader = ownershipReader;
    this.followerCountReader = followerCountReader;
    this.monthlyListenerReader = monthlyListenerReader;
    this.clock = clock;
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
            null,
            a.isDownloadable()))
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
    // The follow graph is authoritative; artist_profile.followers is not updated by follows.
    long followers = followerCountReader.count(artist.getId());
    Instant asOf = clock.now();
    long monthlyListeners = monthlyListenerReader.count(
        artist.getId(), asOf.minus(LISTENER_WINDOW), asOf);
    return new ArtistView(
        artist.getId().value(),
        artist.getName(),
        artist.getImage(),
        artist.getCoverImage(),
        artist.isVerified(),
        monthlyListeners,
        followers,
        artist.getBio(),
        artist.getLocation(),
        artist.getGenres());
  }
}
