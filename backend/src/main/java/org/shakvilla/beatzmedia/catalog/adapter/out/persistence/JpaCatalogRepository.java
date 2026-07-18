package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository.IndexableTrack;
import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistProfile;
import org.shakvilla.beatzmedia.catalog.domain.BrowseCategory;
import org.shakvilla.beatzmedia.catalog.domain.LyricLine;
import org.shakvilla.beatzmedia.catalog.domain.Lyrics;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Playlist;
import org.shakvilla.beatzmedia.catalog.domain.PlaylistId;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.Show;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackCredit;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

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

  // ---- WU-CAT-2: home feed + browse ----

  @Override
  public List<BrowseCategory> browseCategories() {
    return em.createQuery(
            "SELECT b FROM BrowseCategoryEntity b ORDER BY b.id", BrowseCategoryEntity.class)
        .getResultList()
        .stream()
        .map(e -> new BrowseCategory(e.id, e.title, e.colorClass))
        .toList();
  }

  @Override
  public List<Track> trendingTracks(int limit) {
    List<TrackEntity> entities =
        em.createQuery(
                "SELECT t FROM TrackEntity t WHERE t.status = 'ready' ORDER BY t.plays DESC",
                TrackEntity.class)
            .setMaxResults(limit)
            .getResultList();
    return mapTracksWithBatchedCredits(entities);
  }

  @Override
  public List<Track> top10Tracks(int limit) {
    List<TrackEntity> entities =
        em.createQuery(
                "SELECT t FROM TrackEntity t WHERE t.status = 'ready' ORDER BY t.plays DESC",
                TrackEntity.class)
            .setMaxResults(limit)
            .getResultList();
    return mapTracksWithBatchedCredits(entities);
  }

  @Override
  public List<Album> featuredAlbums(int limit) {
    List<AlbumEntity> entities =
        em.createQuery("SELECT a FROM AlbumEntity a ORDER BY a.id", AlbumEntity.class)
            .setMaxResults(limit)
            .getResultList();
    return mapAlbumsWithBatchedTrackIds(entities);
  }

  @Override
  public List<Album> albumsByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    List<AlbumEntity> entities =
        em.createQuery("SELECT a FROM AlbumEntity a WHERE a.id IN :ids", AlbumEntity.class)
            .setParameter("ids", ids)
            .getResultList();
    List<Album> mapped = mapAlbumsWithBatchedTrackIds(entities);
    Map<String, Album> byId =
        mapped.stream().collect(Collectors.toMap(a -> a.getId().value(), a -> a));
    return ids.stream().map(byId::get).filter(a -> a != null).toList();
  }

  @Override
  public List<ArtistProfile> artistsByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    List<ArtistProfileEntity> entities =
        em.createQuery(
                "SELECT a FROM ArtistProfileEntity a WHERE a.id IN :ids",
                ArtistProfileEntity.class)
            .setParameter("ids", ids)
            .getResultList();
    // Batch-load shows for these artists.
    List<ArtistShowEntity> allShows =
        em.createQuery(
                "SELECT s FROM ArtistShowEntity s WHERE s.artistId IN :ids ORDER BY s.position",
                ArtistShowEntity.class)
            .setParameter("ids", ids)
            .getResultList();
    Map<String, List<ArtistShowEntity>> showsByArtist =
        allShows.stream().collect(Collectors.groupingBy(s -> s.artistId));
    Map<String, ArtistProfile> byId = entities.stream()
        .collect(Collectors.toMap(
            e -> e.id,
            e -> toDomain(e, showsByArtist.getOrDefault(e.id, Collections.emptyList()))));
    return ids.stream().map(byId::get).filter(a -> a != null).toList();
  }

  @Override
  public List<Playlist> playlistsByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    List<PlaylistEntity> entities =
        em.createQuery(
                "SELECT p FROM PlaylistEntity p WHERE p.id IN :ids", PlaylistEntity.class)
            .setParameter("ids", ids)
            .getResultList();
    List<PlaylistTrackEntity> allTracks =
        em.createQuery(
                "SELECT pt FROM PlaylistTrackEntity pt WHERE pt.playlistId IN :ids ORDER BY pt.position",
                PlaylistTrackEntity.class)
            .setParameter("ids", ids)
            .getResultList();
    Map<String, List<String>> trackIdsByPlaylist =
        allTracks.stream()
            .collect(
                Collectors.groupingBy(
                    pt -> pt.playlistId,
                    Collectors.mapping(pt -> pt.trackId, Collectors.toList())));
    Map<String, Playlist> byId = entities.stream()
        .collect(Collectors.toMap(
            e -> e.id,
            e -> new Playlist(
                new PlaylistId(e.id),
                e.title,
                e.description,
                e.creator,
                e.creatorAvatar,
                e.image,
                e.isPublic,
                e.followers,
                trackIdsByPlaylist.getOrDefault(e.id, Collections.emptyList()))));
    return ids.stream().map(byId::get).filter(p -> p != null).toList();
  }

  // ---- WU-CAT-3: studio release lifecycle ----

  @Override
  public Page<Release> releasesByArtist(
      ArtistId owner, Optional<ReleaseStatus> status, PageRequest pageRequest) {
    String whereJpql = "FROM ReleaseEntity r WHERE r.artistId = :aid"
        + (status.isPresent() ? " AND r.status = :status" : "");

    var countQuery = em.createQuery("SELECT COUNT(r) " + whereJpql, Long.class)
        .setParameter("aid", owner.value());
    var listQuery = em.createQuery(
            "SELECT r " + whereJpql + " ORDER BY r.createdAt DESC", ReleaseEntity.class)
        .setParameter("aid", owner.value())
        .setFirstResult(pageRequest.offset())
        .setMaxResults(pageRequest.size());

    if (status.isPresent()) {
      countQuery.setParameter("status", status.get().name());
      listQuery.setParameter("status", status.get().name());
    }

    long total = countQuery.getSingleResult();
    List<ReleaseEntity> entities = listQuery.getResultList();

    List<String> releaseIds = entities.stream().map(e -> e.id).toList();
    Map<String, List<ReleaseTrackEntity>> tracksByRelease =
        releaseIds.isEmpty()
            ? Map.of()
            : em.createQuery(
                    "SELECT rt FROM ReleaseTrackEntity rt WHERE rt.pk.releaseId IN :ids ORDER BY rt.pk.position",
                    ReleaseTrackEntity.class)
                .setParameter("ids", releaseIds)
                .getResultList()
                .stream()
                .collect(Collectors.groupingBy(rt -> rt.pk.releaseId));

    List<Release> items = entities.stream()
        .map(e -> toReleaseDomain(e, tracksByRelease.getOrDefault(e.id, List.of())))
        .toList();

    return Page.of(items, pageRequest.page(), pageRequest.size(), total);
  }

  @Override
  public Optional<Release> findRelease(ReleaseId id) {
    ReleaseEntity e = em.find(ReleaseEntity.class, id.value());
    if (e == null) return Optional.empty();
    List<ReleaseTrackEntity> tracks = em.createQuery(
            "SELECT rt FROM ReleaseTrackEntity rt WHERE rt.pk.releaseId = :rid ORDER BY rt.pk.position",
            ReleaseTrackEntity.class)
        .setParameter("rid", id.value())
        .getResultList();
    return Optional.of(toReleaseDomain(e, tracks));
  }

  @Override
  public void saveRelease(Release release) {
    ReleaseEntity e = em.find(ReleaseEntity.class, release.getId());
    if (e == null) {
      e = new ReleaseEntity();
      e.id = release.getId();
      e.createdAt = release.getCreatedAt();
    }
    // idempotencyKey is preserved on existing entity; not overwritten on updates.
    e.artistId = release.getArtistId();
    e.title = release.getTitle();
    e.type = release.getType().name();
    e.status = release.getStatus().name();
    e.visibility = release.getVisibility().toDbValue();
    e.scheduledAt = release.getScheduledAt();
    e.wentLiveAt = release.getWentLiveAt();
    e.listPriceMinor = release.getListPriceMinor();
    e.updatedAt = release.getUpdatedAt();
    e.genre = release.getGenre();
    e.description = release.getDescription();
    em.merge(e);

    // Upsert release_track rows. Remove existing managed entities (rather than a bulk JPQL
    // DELETE) so the persistence context stays in sync — a bulk DELETE bypasses the L1 cache and
    // leaves stale managed ReleaseTrackEntity instances behind, causing a NonUniqueObjectException
    // when persisting new rows with the same composite id.
    List<ReleaseTrackEntity> existingTracks = em.createQuery(
            "SELECT rt FROM ReleaseTrackEntity rt WHERE rt.pk.releaseId = :rid",
            ReleaseTrackEntity.class)
        .setParameter("rid", release.getId())
        .getResultList();
    existingTracks.forEach(em::remove);
    em.flush();
    for (ReleaseTrack rt : release.getTracks()) {
      ReleaseTrackEntity rte = new ReleaseTrackEntity();
      rte.pk = new ReleaseTrackEntity.ReleaseTrackId(release.getId(), rt.position());
      rte.trackId = rt.trackId();
      rte.priceMinor = rt.priceMinor();
      em.persist(rte);
    }
  }

  @Override
  public void deleteRelease(ReleaseId id) {
    em.createQuery("DELETE FROM ReleaseTrackEntity rt WHERE rt.pk.releaseId = :rid")
        .setParameter("rid", id.value())
        .executeUpdate();
    ReleaseEntity e = em.find(ReleaseEntity.class, id.value());
    if (e != null) em.remove(e);
  }

  @Override
  public void saveTrack(Track track) {
    TrackEntity e = new TrackEntity();
    e.id = track.getId().value();
    e.title = track.getTitle();
    e.artistId = track.getArtistId().value();
    e.artistName = track.getArtistName() != null ? track.getArtistName() : "";
    e.albumId = track.getAlbumId().map(AlbumId::value).orElse(null);
    e.albumTitle = track.getAlbumTitle().orElse(null);
    e.durationSec = track.getDurationSec();
    e.image = track.getImage();
    e.ownership = track.getOwnership().name();
    e.priceMinor = track.getPriceMinor().orElse(null);
    e.plays = track.getPlays().orElse(0L);
    e.audioUrl = track.getAudioUrl().orElse(null);
    e.quality = track.getQuality().orElse(null);
    e.year = track.getYear().orElse(null);
    e.status = track.getStatus();
    em.persist(e);
  }

  @Override
  public void deleteTrack(TrackId id) {
    TrackEntity e = em.find(TrackEntity.class, id.value());
    if (e != null) {
      em.remove(e);
    }
  }

  @Override
  public void saveArtistProfile(ArtistProfile profile) {
    // Insert-only provisioning (WU-CAT-7): never clobber an already-provisioned profile's curated
    // fields. The find guard also makes re-delivery of ArtistUpgraded a safe no-op; the PRIMARY KEY
    // is the ultimate backstop against a concurrent double-insert.
    if (em.find(ArtistProfileEntity.class, profile.getId().value()) != null) {
      return;
    }
    Instant now = Instant.now();
    ArtistProfileEntity e = new ArtistProfileEntity();
    e.id = profile.getId().value();
    e.name = profile.getName();
    e.image = profile.getImage();
    e.coverImage = profile.getCoverImage();
    e.verified = profile.isVerified();
    e.monthlyListeners = profile.getMonthlyListeners();
    e.followers = profile.getFollowers();
    e.bio = profile.getBio();
    e.location = profile.getLocation();
    e.genres = profile.getGenres().toArray(new String[0]);
    e.createdAt = now;
    e.updatedAt = now;
    em.persist(e);
  }

  @Override
  public void saveReleaseWithIdempotencyKey(Release release, String idempotencyKey) {
    saveRelease(release);
    if (idempotencyKey != null) {
      em.createQuery("UPDATE ReleaseEntity r SET r.idempotencyKey = :key WHERE r.id = :id")
          .setParameter("key", idempotencyKey)
          .setParameter("id", release.getId())
          .executeUpdate();
    }
  }

  @Override
  public Optional<Release> findReleaseByIdempotencyKey(String idempotencyKey) {
    List<ReleaseEntity> results = em.createQuery(
            "SELECT r FROM ReleaseEntity r WHERE r.idempotencyKey = :key", ReleaseEntity.class)
        .setParameter("key", idempotencyKey)
        .getResultList();
    if (results.isEmpty()) return Optional.empty();
    ReleaseEntity e = results.get(0);
    List<ReleaseTrackEntity> tracks = em.createQuery(
            "SELECT rt FROM ReleaseTrackEntity rt WHERE rt.pk.releaseId = :rid ORDER BY rt.pk.position",
            ReleaseTrackEntity.class)
        .setParameter("rid", e.id)
        .getResultList();
    return Optional.of(toReleaseDomain(e, tracks));
  }

  // ---- WU-CAT-4: release state machine + scheduled go-live ----

  @Override
  public boolean hasPendingSplits(ReleaseId releaseId) {
    List<String> trackIds = em.createQuery(
            "SELECT rt.trackId FROM ReleaseTrackEntity rt WHERE rt.pk.releaseId = :rid",
            String.class)
        .setParameter("rid", releaseId.value())
        .getResultList();
    if (trackIds.isEmpty()) {
      return false;
    }
    Long pendingCount = em.createQuery(
            "SELECT COUNT(s) FROM SplitEntryEntity s "
                + "WHERE s.trackId IN :trackIds AND s.confirmation = 'pending'",
            Long.class)
        .setParameter("trackIds", trackIds)
        .getSingleResult();
    return pendingCount != null && pendingCount > 0;
  }

  @Override
  public List<Release> dueScheduled(Instant now) {
    // Row-lock the due candidates so a concurrent sweep (e.g. overlapping instances despite the
    // scheduler advisory lock) cannot pick up the same release twice. PESSIMISTIC_WRITE is safe
    // under Postgres READ COMMITTED: a second transaction blocks until the first commits, then
    // re-reads the now-updated row and finds it no longer 'scheduled'.
    List<ReleaseEntity> entities = em.createQuery(
            "SELECT r FROM ReleaseEntity r WHERE r.status = 'scheduled' AND r.scheduledAt <= :now",
            ReleaseEntity.class)
        .setParameter("now", now)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultList();

    List<String> releaseIds = entities.stream().map(e -> e.id).toList();
    Map<String, List<ReleaseTrackEntity>> tracksByRelease =
        releaseIds.isEmpty()
            ? Map.of()
            : em.createQuery(
                    "SELECT rt FROM ReleaseTrackEntity rt WHERE rt.pk.releaseId IN :ids "
                        + "ORDER BY rt.pk.position",
                    ReleaseTrackEntity.class)
                .setParameter("ids", releaseIds)
                .getResultList()
                .stream()
                .collect(Collectors.groupingBy(rt -> rt.pk.releaseId));

    return entities.stream()
        .map(e -> toReleaseDomain(e, tracksByRelease.getOrDefault(e.id, List.of())))
        .toList();
  }

  @Override
  public void markReleaseTracksReady(ReleaseId releaseId) {
    // Join through release_track (authoritative link, always populated on submit) rather than
    // relying solely on track.release_id, which upload-time stub tracks may not yet carry.
    List<String> trackIds = em.createQuery(
            "SELECT rt.trackId FROM ReleaseTrackEntity rt WHERE rt.pk.releaseId = :rid",
            String.class)
        .setParameter("rid", releaseId.value())
        .getResultList();
    if (trackIds.isEmpty()) {
      return;
    }
    em.createQuery(
            "UPDATE TrackEntity t SET t.status = 'ready' "
                + "WHERE t.id IN :ids AND t.status <> 'ready'")
        .setParameter("ids", trackIds)
        .executeUpdate();
  }

  // ---- WU-SRCH-2: search index backfill ----

  @Override
  public List<IndexableTrack> allTracksForIndex() {
    // track.release_id is never written by anything (saveTrack omits it; no migration backfills
    // it), so it cannot be used to gate visibility. The authoritative track -> release link is the
    // release_track join table (same rationale as markReleaseTracksReady above). A track is hidden
    // only when it has a release_track row whose release exists and is not 'live'; a track with no
    // release_track row at all (e.g. every dev-seed track) is always visible.
    Set<String> hiddenTrackIds =
        new HashSet<>(
            em.createQuery(
                    "SELECT rt.trackId FROM ReleaseTrackEntity rt, ReleaseEntity r "
                        + "WHERE r.id = rt.pk.releaseId AND r.status <> 'live'",
                    String.class)
                .getResultList());

    // "ready" is enumerated (not "not yet indexed"): a track only reaches 'ready' via
    // markReleaseTracksReady at go-live/reinstate, so it may already have a stale visible document
    // that a takedown needs to overwrite with visible=false. 'uploading'/'error' tracks were never
    // indexed, so there is nothing stale to strand by skipping them.
    List<TrackEntity> entities =
        em.createQuery("SELECT t FROM TrackEntity t WHERE t.status = 'ready'", TrackEntity.class)
            .getResultList();

    return mapTracksWithBatchedCredits(entities).stream()
        .map(t -> new IndexableTrack(t, !hiddenTrackIds.contains(t.getId().value())))
        .toList();
  }

  @Override
  public List<ArtistProfile> allArtistsForIndex() {
    List<ArtistProfileEntity> entities =
        em.createQuery("SELECT a FROM ArtistProfileEntity a", ArtistProfileEntity.class)
            .getResultList();
    if (entities.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> ids = entities.stream().map(e -> e.id).toList();
    // Batch-load shows for these artists.
    List<ArtistShowEntity> allShows =
        em.createQuery(
                "SELECT s FROM ArtistShowEntity s WHERE s.artistId IN :ids ORDER BY s.position",
                ArtistShowEntity.class)
            .setParameter("ids", ids)
            .getResultList();
    Map<String, List<ArtistShowEntity>> showsByArtist =
        allShows.stream().collect(Collectors.groupingBy(s -> s.artistId));
    return entities.stream()
        .map(e -> toDomain(e, showsByArtist.getOrDefault(e.id, Collections.emptyList())))
        .toList();
  }

  @Override
  public List<Album> allAlbumsForIndex() {
    List<AlbumEntity> entities =
        em.createQuery("SELECT a FROM AlbumEntity a", AlbumEntity.class).getResultList();
    return mapAlbumsWithBatchedTrackIds(entities);
  }

  @Override
  public List<Playlist> allPlaylistsForIndex() {
    // Deliberately includes private (is_public = false) playlists: reindex is upsert-only, so the
    // indexer needs them to write visible=false rather than omitting them (Task 4).
    List<PlaylistEntity> entities =
        em.createQuery("SELECT p FROM PlaylistEntity p", PlaylistEntity.class).getResultList();
    if (entities.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> ids = entities.stream().map(e -> e.id).toList();
    List<PlaylistTrackEntity> allTracks =
        em.createQuery(
                "SELECT pt FROM PlaylistTrackEntity pt WHERE pt.playlistId IN :ids ORDER BY pt.position",
                PlaylistTrackEntity.class)
            .setParameter("ids", ids)
            .getResultList();
    Map<String, List<String>> trackIdsByPlaylist =
        allTracks.stream()
            .collect(
                Collectors.groupingBy(
                    pt -> pt.playlistId,
                    Collectors.mapping(pt -> pt.trackId, Collectors.toList())));
    return entities.stream()
        .map(
            e -> new Playlist(
                new PlaylistId(e.id),
                e.title,
                e.description,
                e.creator,
                e.creatorAvatar,
                e.image,
                e.isPublic,
                e.followers,
                trackIdsByPlaylist.getOrDefault(e.id, Collections.emptyList())))
        .toList();
  }

  private Release toReleaseDomain(ReleaseEntity e, List<ReleaseTrackEntity> trackEntities) {
    List<ReleaseTrack> tracks = trackEntities.stream()
        .map(rt -> new ReleaseTrack(rt.trackId, rt.pk.position, rt.priceMinor))
        .toList();
    return Release.reconstitute(
        e.id,
        e.artistId,
        e.title,
        ReleaseType.valueOf(e.type),
        ReleaseStatus.valueOf(e.status),
        Visibility.fromDbValue(e.visibility),
        e.scheduledAt,
        e.wentLiveAt,
        e.listPriceMinor,
        e.createdAt,
        e.updatedAt,
        tracks,
        e.genre,
        e.description);
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
