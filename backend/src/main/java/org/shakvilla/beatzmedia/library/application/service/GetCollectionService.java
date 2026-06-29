package org.shakvilla.beatzmedia.library.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.in.CollectionView;
import org.shakvilla.beatzmedia.library.application.port.in.GetCollection;
import org.shakvilla.beatzmedia.library.application.port.in.UserPlaylistView;
import org.shakvilla.beatzmedia.library.application.port.out.CollectionRepository;
import org.shakvilla.beatzmedia.library.application.port.out.LibraryOwnershipReader;
import org.shakvilla.beatzmedia.library.domain.LikeSets;
import org.shakvilla.beatzmedia.library.domain.UserPlaylist;

/** Application service for GET /v1/me/collection. Library ADD §4.1 / LLFR-LIBRARY-01.1. */
@ApplicationScoped
public class GetCollectionService implements GetCollection {

  private final CollectionRepository repo;
  private final LibraryOwnershipReader ownershipReader;

  @Inject
  public GetCollectionService(CollectionRepository repo, LibraryOwnershipReader ownershipReader) {
    this.repo = repo;
    this.ownershipReader = ownershipReader;
  }

  @Override
  @Transactional
  public CollectionView get(AccountId account) {
    LikeSets sets = repo.likeSets(account);
    List<UserPlaylist> playlists = repo.playlistsOf(account);
    List<String> owned = ownershipReader.ownedTrackIds(account);

    List<UserPlaylistView> playlistViews = playlists.stream().map(PlaylistMapper::toView).toList();

    return new CollectionView(
        sets.likedTrackIds(),
        sets.followedArtistIds(),
        sets.followedPlaylistIds(),
        sets.followedShowIds(),
        sets.savedAlbumIds(),
        owned,
        playlistViews);
  }
}
