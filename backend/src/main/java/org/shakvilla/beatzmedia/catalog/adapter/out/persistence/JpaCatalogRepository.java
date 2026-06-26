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
 *
 * <p>N+1 avoidance: child collections (track credits, album track-ids) are batch-loaded in a single
 * {@code WHERE id IN (:ids)} query and then grouped in memory. Guard: empty id lists never issue an
 * {@code IN ()} query.
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
    List<ArtistShowEntity> showEntities =
        em.createQuery(
                "SELECT s FROM ArtistShowEntity s WHERE s.artistId = :aid ORDER BY s.position",
                ArtistShowEntity.class)
            .setParameter("aid", id.value())
            .getResultList();
    return Optional.of(toDomain(e, showEntities));
  }

  @Override
  public List<Track> tracksByArtist(ArtistId id) {
    List<TrackEntity> entities =
        em.createQuery(
                "SELECT t FROM TrackEntity t WHERE t.artistId = :aid ORDER BY t.plays DESC",
                TrackEntity.class)
            .setParameter("aid", id.value())
            .getResultList();
    return mapTracksWithBatchedCredits(entities);
  }

  @Override
  public List<Album> albumsByArtist(ArtistId id) {
    List<AlbumEntity> entities =
        em.createQuery(
                "SELECT a FROM AlbumEntity a WHERE a.artistId = :aid", AlbumEntity.class)
            .setParameter("aid", id.value())
            .getResultList();
    return mapAlbumsWithBatchedTrackIds(entities);
  }

  @Override
  public Optional<Album> findAlbum(AlbumId id) {
    AlbumEntity e = em.find(AlbumEntity.class, id.value());
    if (e == null) {
      return Optional.empty();
    }
    return Optional.of(mapAlbumsWithBatchedTrackIds(List.of(e)).get(0));
  }

  @Override
  public List<Track> tracksByAlbum(AlbumId id) {
    List<TrackEntity> entities =
        em.createQuery(
                "SELECT t FROM TrackEntity t WHERE t.albumId = :aid", TrackEntity.class)
            .setParameter("aid", id.value())
            .getResultList();
    return mapTracksWithBatchedCredits(entities);
  }

  @Override
  public Optional<Track> findTrack(TrackId id) {
    TrackEntity e = em.find(TrackEntity.class, id.value());
    if (e == null) {
      return Optional.empty();
    }
    return Optional.of(mapTracksWithBatchedCredits(List.of(e)).get(0));
  }

  @Override
  public Optional<Lyrics> findLyrics(TrackId id) {
    LyricsEntity header = em.find(LyricsEntity.class, id.value());
    if (header == null) {
      return Optional.empty();
    }
    List<LyricLineEntity> lines =
        em.createQuery(
                "SELECT l FROM LyricLineEntity l WHERE l.trackId = :tid ORDER BY l.tSec",
                LyricLineEntity.class)
            .setParameter("tid", id.value())
            .getResultList();
    List<LyricLine> domainLines = lines.stream().map(l -> new LyricLine(l.tSec, l.text)).toList();
    return Optional.of(new Lyrics(id, domainLines));
  }

  @Override
  public Optional<Playlist> findPlaylist(PlaylistId id) {
    PlaylistEntity e = em.find(PlaylistEntity.class, id.value());
    if (e == null) {
      return Optional.empty();
    }
    List<PlaylistTrackEntity> ptEntities =
        em.createQuery(
                "SELECT pt FROM PlaylistTrackEntity pt WHERE pt.playlistId = :pid ORDER BY pt.position",
                PlaylistTrackEntity.class)
            .setParameter("pid", id.value())
            .getResultList();
    List<String> trackIds = ptEntities.stream().map(pt -> pt.trackId).toList();
    return Optional.of(
        new Playlist(
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
    List<TrackEntity> entities =
        em.createQuery(
                "SELECT t FROM TrackEntity t WHERE t.id IN :ids", TrackEntity.class)
            .setParameter("ids", ids)
            .getResultList();
    // Batch-load credits for all fetched tracks in one query.
    List<Track> mapped = mapTracksWithBatchedCredits(entities);
    // Preserve the requested order.
    Map<String, Track> byId =
        mapped.stream().collect(Collectors.toMap(t -> t.getId().value(), t -> t));
    List<Track> ordered = new ArrayList<>();
    for (String id : ids) {
      Track t = byId.get(id);
      if (t != null) {
        ordered.add(t);
      }
    }
    return ordered;
  }

  // ---- Batch-mapping helpers ----

  /**
   * Maps a list of {@link TrackEntity} to domain {@link Track} objects. Batch-loads all
   * {@link TrackCreditEntity} rows for the given tracks in a single {@code IN} query, then groups by
   * {@code trackId} in memory — avoiding N+1 per-track credit queries.
   */
  private List<Track> mapTracksWithBatchedCredits(List<TrackEntity> entities) {
    if (entities.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> trackIds = entities.stream().map(e -> e.id).toList();
    // Single batch query for all credits.
    List<TrackCreditEntity> allCredits =
        em.createQuery(
                "SELECT c FROM TrackCreditEntity c WHERE c.trackId IN :ids", TrackCreditEntity.class)
            .setParameter("ids", trackIds)
            .getResultList();
    // Group by trackId in memory; preserve credit order as returned by DB (by primary key).
    Map<String, List<TrackCreditEntity>> creditsByTrack =
        allCredits.stream().collect(Collectors.groupingBy(c -> c.trackId));

    return entities.stream()
        .map(e -> trackToDomain(e, creditsByTrack.getOrDefault(e.id, Collections.emptyList())))
        .toList();
  }

  /**
   * Maps a list of {@link AlbumEntity} to domain {@link Album} objects. Batch-loads all track ids
   * for the given albums in a single {@code IN} query, then groups by {@code albumId} in memory —
   * avoiding N+1 per-album track-id queries.
   */
  private List<Album> mapAlbumsWithBatchedTrackIds(List<AlbumEntity> entities) {
    if (entities.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> albumIds = entities.stream().map(e -> e.id).toList();
    // Single batch query for all tracks belonging to these albums.
    List<TrackEntity> allTracks =
        em.createQuery(
                "SELECT t FROM TrackEntity t WHERE t.albumId IN :ids ORDER BY t.id",
                TrackEntity.class)
            .setParameter("ids", albumIds)
            .getResultList();
    // Group track ids by albumId in memory.
    Map<String, List<String>> trackIdsByAlbum =
        allTracks.stream()
            .collect(
                Collectors.groupingBy(
                    t -> t.albumId, Collectors.mapping(t -> t.id, Collectors.toList())));

    return entities.stream()
        .map(
            e -> {
              List<String> trackIds = trackIdsByAlbum.getOrDefault(e.id, Collections.emptyList());
              List<String> genres =
                  e.genres != null ? Arrays.asList(e.genres) : Collections.emptyList();
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
            })
        .toList();
  }

  // ---- Low-level mapping helpers ----

  private ArtistProfile toDomain(ArtistProfileEntity e, List<ArtistShowEntity> showEntities) {
    List<Show> shows = showEntities.stream().map(s -> new Show(s.date, s.city, s.venue)).toList();
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

  private Track trackToDomain(TrackEntity e, List<TrackCreditEntity> creditEntities) {
    List<TrackCredit> credits =
        creditEntities.stream()
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
