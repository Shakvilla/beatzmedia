package org.shakvilla.beatzmedia.library.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.in.ManageUserPlaylist;
import org.shakvilla.beatzmedia.library.application.port.in.UserPlaylistView;
import org.shakvilla.beatzmedia.library.application.port.out.CatalogReader;
import org.shakvilla.beatzmedia.library.application.port.out.CollectionRepository;
import org.shakvilla.beatzmedia.library.domain.PlaylistId;
import org.shakvilla.beatzmedia.library.domain.PlaylistNotFoundException;
import org.shakvilla.beatzmedia.library.domain.TargetNotFoundException;
import org.shakvilla.beatzmedia.library.domain.UserPlaylist;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for user playlist CRUD. Clock and IdGenerator are injected via platform
 * output ports so domain stays pure (ArchUnit: no Instant.now() / UUID.randomUUID() in core).
 * Library ADD §4.1 / LLFR-LIBRARY-01.5.
 */
@ApplicationScoped
public class ManageUserPlaylistService implements ManageUserPlaylist {

  private final CollectionRepository repo;
  private final CatalogReader catalogReader;
  private final Clock clock;
  private final IdGenerator idGenerator;

  @Inject
  public ManageUserPlaylistService(
      CollectionRepository repo,
      CatalogReader catalogReader,
      Clock clock,
      IdGenerator idGenerator) {
    this.repo = repo;
    this.catalogReader = catalogReader;
    this.clock = clock;
    this.idGenerator = idGenerator;
  }

  @Override
  @Transactional
  public List<UserPlaylistView> listPlaylists(AccountId account) {
    return repo.playlistsOf(account).stream().map(PlaylistMapper::toView).toList();
  }

  @Override
  @Transactional
  public UserPlaylistView createPlaylist(AccountId account, String title) {
    String id = idGenerator.newId();
    // Let UserPlaylist.create validate the title via setTitle (throws InvalidTitleException)
    UserPlaylist playlist = UserPlaylist.create(id, account.value(), title, clock.now());
    UserPlaylist saved = repo.save(playlist);
    return PlaylistMapper.toView(saved);
  }

  @Override
  @Transactional
  public UserPlaylistView getPlaylist(AccountId account, PlaylistId playlistId) {
    UserPlaylist p = requireOwned(account, playlistId);
    return PlaylistMapper.toView(p);
  }

  @Override
  @Transactional
  public UserPlaylistView renamePlaylist(AccountId account, PlaylistId playlistId, String title) {
    UserPlaylist p = requireOwned(account, playlistId);
    p.rename(title); // throws InvalidTitleException if invalid
    UserPlaylist saved = repo.save(p);
    return PlaylistMapper.toView(saved);
  }

  @Override
  @Transactional
  public void deletePlaylist(AccountId account, PlaylistId playlistId) {
    repo.deletePlaylist(account, playlistId);
  }

  @Override
  @Transactional
  public UserPlaylistView addTrack(AccountId account, PlaylistId playlistId, String trackId) {
    UserPlaylist p = requireOwned(account, playlistId);
    if (!catalogReader.trackExists(trackId)) {
      throw new TargetNotFoundException("TRACK_NOT_FOUND", trackId);
    }
    p.addTrack(trackId);
    UserPlaylist saved = repo.save(p);
    return PlaylistMapper.toView(saved);
  }

  @Override
  @Transactional
  public UserPlaylistView removeTrack(AccountId account, PlaylistId playlistId, String trackId) {
    UserPlaylist p = requireOwned(account, playlistId);
    p.removeTrack(trackId);
    UserPlaylist saved = repo.save(p);
    return PlaylistMapper.toView(saved);
  }

  private UserPlaylist requireOwned(AccountId account, PlaylistId playlistId) {
    return repo.findPlaylist(account, playlistId)
        .orElseThrow(() -> new PlaylistNotFoundException(playlistId.value()));
  }
}
