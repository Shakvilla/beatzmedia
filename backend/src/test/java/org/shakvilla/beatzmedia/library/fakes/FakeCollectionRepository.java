package org.shakvilla.beatzmedia.library.fakes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.out.CollectionRepository;
import org.shakvilla.beatzmedia.library.domain.FollowKind;
import org.shakvilla.beatzmedia.library.domain.LikeSets;
import org.shakvilla.beatzmedia.library.domain.PlaylistId;
import org.shakvilla.beatzmedia.library.domain.UserPlaylist;

/** In-memory fake for unit tests. */
public class FakeCollectionRepository implements CollectionRepository {

  private final Map<String, Set<String>> likedTracks = new HashMap<>();
  private final Map<String, Set<String>> followedArtists = new HashMap<>();
  private final Map<String, Set<String>> followedPlaylists = new HashMap<>();
  private final Map<String, Set<String>> followedShows = new HashMap<>();
  private final Map<String, Set<String>> savedAlbums = new HashMap<>();
  private final Map<String, List<UserPlaylist>> playlists = new HashMap<>();

  @Override
  public LikeSets likeSets(AccountId account) {
    return new LikeSets(
        new ArrayList<>(likedTracks.getOrDefault(account.value(), new LinkedHashSet<>())),
        new ArrayList<>(followedArtists.getOrDefault(account.value(), new LinkedHashSet<>())),
        new ArrayList<>(followedPlaylists.getOrDefault(account.value(), new LinkedHashSet<>())),
        new ArrayList<>(followedShows.getOrDefault(account.value(), new LinkedHashSet<>())),
        new ArrayList<>(savedAlbums.getOrDefault(account.value(), new LinkedHashSet<>())));
  }

  @Override
  public boolean addLike(AccountId account, String trackId) {
    return likedTracks.computeIfAbsent(account.value(), k -> new LinkedHashSet<>()).add(trackId);
  }

  @Override
  public void removeLike(AccountId account, String trackId) {
    likedTracks.getOrDefault(account.value(), new LinkedHashSet<>()).remove(trackId);
  }

  @Override
  public boolean addFollow(AccountId account, FollowKind kind, String targetId) {
    return setForKind(account.value(), kind).add(targetId);
  }

  @Override
  public void removeFollow(AccountId account, FollowKind kind, String targetId) {
    setForKind(account.value(), kind).remove(targetId);
  }

  @Override
  public boolean addSave(AccountId account, String albumId) {
    return savedAlbums.computeIfAbsent(account.value(), k -> new LinkedHashSet<>()).add(albumId);
  }

  @Override
  public void removeSave(AccountId account, String albumId) {
    savedAlbums.getOrDefault(account.value(), new LinkedHashSet<>()).remove(albumId);
  }

  @Override
  public List<UserPlaylist> playlistsOf(AccountId account) {
    return new ArrayList<>(playlists.getOrDefault(account.value(), List.of()));
  }

  @Override
  public Optional<UserPlaylist> findPlaylist(AccountId account, PlaylistId playlist) {
    return playlistsOf(account).stream()
        .filter(p -> p.id().equals(playlist.value()))
        .findFirst();
  }

  @Override
  public UserPlaylist save(UserPlaylist playlist) {
    List<UserPlaylist> list =
        playlists.computeIfAbsent(playlist.ownerId(), k -> new ArrayList<>());
    list.removeIf(p -> p.id().equals(playlist.id()));
    list.add(playlist);
    return playlist;
  }

  @Override
  public void deletePlaylist(AccountId account, PlaylistId playlist) {
    List<UserPlaylist> list = playlists.getOrDefault(account.value(), new ArrayList<>());
    list.removeIf(p -> p.id().equals(playlist.value()));
  }

  private Set<String> setForKind(String accountId, FollowKind kind) {
    return switch (kind) {
      case artist -> followedArtists.computeIfAbsent(accountId, k -> new LinkedHashSet<>());
      case playlist -> followedPlaylists.computeIfAbsent(accountId, k -> new LinkedHashSet<>());
      case show -> followedShows.computeIfAbsent(accountId, k -> new LinkedHashSet<>());
    };
  }
}
