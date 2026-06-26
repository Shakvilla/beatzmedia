package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.LyricLine;
import org.shakvilla.beatzmedia.catalog.domain.Lyrics;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;
import org.shakvilla.beatzmedia.catalog.domain.PlaylistId;
import org.shakvilla.beatzmedia.catalog.domain.Show;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackCredit;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/**
 * JPA/Panache implementation of {@link CatalogRepository}. Reads catalog tables only; no
 * cross-module joins. Transaction boundary = the application service ({@code @Transactional} on
 * use-case impls). Catalog ADD §5.2.
 */
@ApplicationScoped
public class JpaCatalogRepository implements CatalogRepository {

  private final EntityManager em;

  @Inject
  public JpaCatalogRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public Optional<ArtistProfile> findArtist(ArtistId id) {
    ArtistProfileEntity e = em.find(ArtistProfileEntity.class, id.value());
    if (e == null) {
      return Optional.empty();
    }
    List<ArtistShowEntity> showEntities = em.createQuery(
            "SELECT s FROM ArtistShowEntity s WHERE s.artistId = :aid ORDER BY s.position",
            ArtistShowEntity.class)
        .setParameter("aid", id.value())
        .getResultList();
    return Optional.of(toDomain(e, showEntities));
  }

  @Override
  public List<Track> tracksByArtist(ArtistId id) {
    List<TrackEntity> entities = em.createQuery(
            "SELECT t FROM TrackEntity t WHERE t.artistId = :aid ORDER BY t.plays DESC",
            TrackEntity.class)
        .setParameter("aid", id.value())
        .getResultList();
    return entities.stream().map(this::trackToDomain).toList();
  }

  @Override
  public List<Album> albumsByArtist(ArtistId id) {
    List<AlbumEntity> entities = em.createQuery(
            "SELECT a FROM AlbumEntity a WHERE a.artistId = :aid",
            AlbumEntity.class)
        .setParameter("aid", id.value())
        .getResultList();
    return entities.stream().map(this::albumToDomain).toList();
  }

  @Override
  public Optional<Album> findAlbum(AlbumId id) {
    AlbumEntity e = em.find(AlbumEntity.class, id.value());
    return Optional.ofNullable(e).map(this::albumToDomain);
  }

  @Override
  public List<Track> tracksByAlbum(AlbumId id) {
    List<TrackEntity> entities = em.createQuery(
            "SELECT t FROM TrackEntity t WHERE t.albumId = :aid",
            TrackEntity.class)
        .setParameter("aid", id.value())
        .getResultList();
    return entities.stream().map(this::trackToDomain).toList();
  }

  @Override
  public Optional<Track> findTrack(TrackId id) {
    TrackEntity e = em.find(TrackEntity.class, id.value());
    return Optional.ofNullable(e).map(this::trackToDomain);
  }

  @Override
  public Optional<Lyrics> findLyrics(TrackId id) {
    LyricsEntity header = em.find(LyricsEntity.class, id.value());
    if (header == null) {
      return Optional.empty();
    }
    List<LyricLineEntity> lines = em.createQuery(
            "SELECT l FROM LyricLineEntity l WHERE l.trackId = :tid ORDER BY l.tSec",
            LyricLineEntity.class)
        .setParameter("tid", id.value())
        .getResultList();
    List<LyricLine> domainLines = lines.stream()
        .map(l -> new LyricLine(l.tSec, l.text))
        .toList();
    return Optional.of(new Lyrics(id, domainLines));
  }

  @Override
  public Optional<Playlist> findPlaylist(PlaylistId id) {
    PlaylistEntity e = em.find(PlaylistEntity.class, id.value());
    if (e == null) {
      return Optional.empty();
    }
    List<PlaylistTrackEntity> ptEntities = em.createQuery(
            "SELECT pt FROM PlaylistTrackEntity pt WHERE pt.playlistId = :pid ORDER BY pt.position",
            PlaylistTrackEntity.class)
        .setParameter("pid", id.value())
        .getResultList();
    List<String> trackIds = ptEntities.stream().map(pt -> pt.trackId).toList();
    return Optional.of(new Playlist(
        new PlaylistId(e.id),
        e.title,
        e.description,
        e.creator,
        e.creatorAvatar,
        e.image,
        e.isPublic,
        e.followers,
        trackIds));
  }

  @Override
  public List<Track> tracksByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    List<TrackEntity> entities = em.createQuery(
            "SELECT t FROM TrackEntity t WHERE t.id IN :ids",
            TrackEntity.class)
        .setParameter("ids", ids)
        .getResultList();
    // Preserve requested order
    Map<String, Track> byId = entities.stream()
        .map(this::trackToDomain)
        .collect(Collectors.toMap(t -> t.getId().value(), t -> t));
    List<Track> ordered = new ArrayList<>();
    for (String id : ids) {
      Track t = byId.get(id);
      if (t != null) {
        ordered.add(t);
      }
    }
    return ordered;
  }

  // ---- Mapping helpers ----

  private ArtistProfile toDomain(ArtistProfileEntity e, List<ArtistShowEntity> showEntities) {
    List<Show> shows = showEntities.stream()
        .map(s -> new Show(s.date, s.city, s.venue))
        .toList();
    List<String> genres = e.genres != null ? Arrays.asList(e.genres) : Collections.emptyList();
    return new ArtistProfile(
        new ArtistId(e.id),
        e.name,
        e.image,
        e.coverImage,
        e.verified,
        e.monthlyListeners,
        e.followers,
        e.bio,
        e.location,
        genres,
        shows);
  }

  private Album albumToDomain(AlbumEntity e) {
    // Load track ids in album order
    List<TrackEntity> tracks = em.createQuery(
            "SELECT t FROM TrackEntity t WHERE t.albumId = :aid",
            TrackEntity.class)
        .setParameter("aid", e.id)
        .getResultList();
    List<String> trackIds = tracks.stream().map(t -> t.id).toList();
    List<String> genres = e.genres != null ? Arrays.asList(e.genres) : Collections.emptyList();
    return new Album(
        new AlbumId(e.id),
        e.title,
        new ArtistId(e.artistId),
        e.artistName,
        e.year,
        e.coverImage,
        genres,
        trackIds,
        e.listPriceMinor);
  }

  private Track trackToDomain(TrackEntity e) {
    // Load credits for this track
    List<TrackCreditEntity> creditEntities = em.createQuery(
            "SELECT c FROM TrackCreditEntity c WHERE c.trackId = :tid",
            TrackCreditEntity.class)
        .setParameter("tid", e.id)
        .getResultList();
    List<TrackCredit> credits = creditEntities.stream()
        .map(c -> new TrackCredit(c.role, Arrays.asList(c.names)))
        .toList();

    OwnershipStatus ownership;
    try {
      ownership = OwnershipStatus.valueOf(e.ownership.replace('-', '_'));
    } catch (IllegalArgumentException ex) {
      ownership = OwnershipStatus.free;
    }

    return new Track(
        new TrackId(e.id),
        e.title,
        new ArtistId(e.artistId),
        e.artistName,
        e.albumId != null ? new AlbumId(e.albumId) : null,
        e.albumTitle,
        e.durationSec,
        e.image,
        ownership,
        e.priceMinor,
        e.plays,
        e.audioUrl,
        credits.isEmpty() ? null : credits,
        e.quality,
        e.year,
        e.status);
  }
}
