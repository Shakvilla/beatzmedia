package org.shakvilla.beatzmedia.library.adapter.out.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.out.CollectionRepository;
import org.shakvilla.beatzmedia.library.domain.FollowKind;
import org.shakvilla.beatzmedia.library.domain.LikeSets;
import org.shakvilla.beatzmedia.library.domain.PlaylistId;
import org.shakvilla.beatzmedia.library.domain.PlaylistTrack;
import org.shakvilla.beatzmedia.library.domain.UserPlaylist;

/**
 * JPA implementation of {@link CollectionRepository}. Uses {@code INSERT … ON CONFLICT DO NOTHING}
 * (native SQL) for idempotent like/follow/save, and CascadeAll for playlist-track management.
 * Library ADD §5.2.
 */
@ApplicationScoped
public class JpaCollectionRepository implements CollectionRepository {

  private final EntityManager em;

  @Inject
  public JpaCollectionRepository(EntityManager em) {
    this.em = em;
  }

  // -------- Like sets --------

  @Override
  public LikeSets likeSets(AccountId account) {
    UUID aid = uuid(account);

    List<String> likedTracks =
        em.createQuery(
                "SELECT e.id.trackId FROM LikedTrackEntity e WHERE e.id.accountId = :aid"
                    + " ORDER BY e.createdAt DESC",
                String.class)
            .setParameter("aid", aid)
            .getResultList();

    List<String> followedArtists =
        em.createQuery(
                "SELECT e.id.artistId FROM FollowedArtistEntity e WHERE e.id.accountId = :aid"
                    + " ORDER BY e.createdAt DESC",
                String.class)
            .setParameter("aid", aid)
            .getResultList();

    List<String> followedPlaylists =
        em.createQuery(
                "SELECT e.id.playlistId FROM FollowedPlaylistEntity e WHERE e.id.accountId = :aid"
                    + " ORDER BY e.createdAt DESC",
                String.class)
            .setParameter("aid", aid)
            .getResultList();

    List<String> followedShows =
        em.createQuery(
                "SELECT e.id.showId FROM FollowedShowEntity e WHERE e.id.accountId = :aid"
                    + " ORDER BY e.createdAt DESC",
                String.class)
            .setParameter("aid", aid)
            .getResultList();

    List<String> savedAlbums =
        em.createQuery(
                "SELECT e.id.albumId FROM SavedAlbumEntity e WHERE e.id.accountId = :aid"
                    + " ORDER BY e.createdAt DESC",
                String.class)
            .setParameter("aid", aid)
            .getResultList();

    return new LikeSets(likedTracks, followedArtists, followedPlaylists, followedShows, savedAlbums);
  }

  @Override
  public boolean addLike(AccountId account, String trackId) {
    int rows =
        em.createNativeQuery(
                "INSERT INTO liked_track (account_id, track_id, created_at)"
                    + " VALUES (:aid, :tid, now())"
                    + " ON CONFLICT DO NOTHING")
            .setParameter("aid", uuid(account))
            .setParameter("tid", trackId)
            .executeUpdate();
    return rows > 0;
  }

  @Override
  public void removeLike(AccountId account, String trackId) {
    em.createQuery(
            "DELETE FROM LikedTrackEntity e"
                + " WHERE e.id.accountId = :aid AND e.id.trackId = :tid")
        .setParameter("aid", uuid(account))
        .setParameter("tid", trackId)
        .executeUpdate();
  }

  @Override
  public boolean addFollow(AccountId account, FollowKind kind, String targetId) {
    UUID aid = uuid(account);
    int rows =
        switch (kind) {
          case artist ->
              em.createNativeQuery(
                      "INSERT INTO followed_artist (account_id, artist_id, created_at)"
                          + " VALUES (:aid, :tid, now()) ON CONFLICT DO NOTHING")
                  .setParameter("aid", aid)
                  .setParameter("tid", targetId)
                  .executeUpdate();
          case playlist ->
              em.createNativeQuery(
                      "INSERT INTO followed_playlist (account_id, playlist_id, created_at)"
                          + " VALUES (:aid, :tid, now()) ON CONFLICT DO NOTHING")
                  .setParameter("aid", aid)
                  .setParameter("tid", targetId)
                  .executeUpdate();
          case show ->
              em.createNativeQuery(
                      "INSERT INTO followed_show (account_id, show_id, created_at)"
                          + " VALUES (:aid, :tid, now()) ON CONFLICT DO NOTHING")
                  .setParameter("aid", aid)
                  .setParameter("tid", targetId)
                  .executeUpdate();
        };
    return rows > 0;
  }

  @Override
  public void removeFollow(AccountId account, FollowKind kind, String targetId) {
    UUID aid = uuid(account);
    switch (kind) {
      case artist ->
          em.createQuery(
                  "DELETE FROM FollowedArtistEntity e"
                      + " WHERE e.id.accountId = :aid AND e.id.artistId = :tid")
              .setParameter("aid", aid)
              .setParameter("tid", targetId)
              .executeUpdate();
      case playlist ->
          em.createQuery(
                  "DELETE FROM FollowedPlaylistEntity e"
                      + " WHERE e.id.accountId = :aid AND e.id.playlistId = :tid")
              .setParameter("aid", aid)
              .setParameter("tid", targetId)
              .executeUpdate();
      case show ->
          em.createQuery(
                  "DELETE FROM FollowedShowEntity e"
                      + " WHERE e.id.accountId = :aid AND e.id.showId = :tid")
              .setParameter("aid", aid)
              .setParameter("tid", targetId)
              .executeUpdate();
    }
  }

  @Override
  public boolean addSave(AccountId account, String albumId) {
    int rows =
        em.createNativeQuery(
                "INSERT INTO saved_album (account_id, album_id, created_at)"
                    + " VALUES (:aid, :alid, now())"
                    + " ON CONFLICT DO NOTHING")
            .setParameter("aid", uuid(account))
            .setParameter("alid", albumId)
            .executeUpdate();
    return rows > 0;
  }

  @Override
  public void removeSave(AccountId account, String albumId) {
    em.createQuery(
            "DELETE FROM SavedAlbumEntity e"
                + " WHERE e.id.accountId = :aid AND e.id.albumId = :alid")
        .setParameter("aid", uuid(account))
        .setParameter("alid", albumId)
        .executeUpdate();
  }

  // -------- Playlists --------

  @Override
  public List<UserPlaylist> playlistsOf(AccountId account) {
    List<UserPlaylistEntity> entities =
        em.createQuery(
                "SELECT p FROM UserPlaylistEntity p WHERE p.accountId = :aid"
                    + " ORDER BY p.createdAt DESC",
                UserPlaylistEntity.class)
            .setParameter("aid", uuid(account))
            .getResultList();
    return entities.stream().map(this::toDomain).toList();
  }

  @Override
  public Optional<UserPlaylist> findPlaylist(AccountId account, PlaylistId playlist) {
    List<UserPlaylistEntity> results =
        em.createQuery(
                "SELECT p FROM UserPlaylistEntity p WHERE p.id = :pid AND p.accountId = :aid",
                UserPlaylistEntity.class)
            .setParameter("pid", UUID.fromString(playlist.value()))
            .setParameter("aid", uuid(account))
            .getResultList();
    return results.stream().findFirst().map(this::toDomain);
  }

  @Override
  public UserPlaylist save(UserPlaylist playlist) {
    UUID pid = UUID.fromString(playlist.id());
    UserPlaylistEntity entity = em.find(UserPlaylistEntity.class, pid);
    if (entity == null) {
      entity = new UserPlaylistEntity();
      entity.id = pid;
      entity.accountId = UUID.fromString(playlist.ownerId());
      entity.createdAt = playlist.createdAt();
    }
    entity.title = playlist.title();
    entity.description = playlist.description();

    // Sync tracks: clear and re-add in order
    entity.tracks.clear();
    em.flush(); // flush deletes before re-inserting to avoid position unique constraint violation
    List<PlaylistTrack> domainTracks = playlist.tracks();
    for (PlaylistTrack dt : domainTracks) {
      entity.tracks.add(new UserPlaylistTrackEntity(entity, dt.trackId(), dt.position()));
    }

    if (em.find(UserPlaylistEntity.class, pid) == null) {
      em.persist(entity);
    } else {
      entity = em.merge(entity);
    }
    em.flush();
    return toDomain(entity);
  }

  @Override
  public void deletePlaylist(AccountId account, PlaylistId playlist) {
    em.createQuery(
            "DELETE FROM UserPlaylistEntity p WHERE p.id = :pid AND p.accountId = :aid")
        .setParameter("pid", UUID.fromString(playlist.value()))
        .setParameter("aid", uuid(account))
        .executeUpdate();
  }

  // -------- Helpers --------

  private UUID uuid(AccountId account) {
    return UUID.fromString(account.value());
  }

  private UserPlaylist toDomain(UserPlaylistEntity e) {
    List<PlaylistTrack> tracks =
        e.tracks.stream()
            .sorted(java.util.Comparator.comparingInt(t -> t.position))
            .map(t -> new PlaylistTrack(t.id.trackId, t.position))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    return new UserPlaylist(
        e.id.toString(),
        e.accountId.toString(),
        e.title,
        e.description,
        tracks,
        e.createdAt);
  }
}
