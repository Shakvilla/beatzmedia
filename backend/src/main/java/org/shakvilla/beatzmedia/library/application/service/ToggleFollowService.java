package org.shakvilla.beatzmedia.library.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.in.ToggleFollow;
import org.shakvilla.beatzmedia.library.application.port.out.CatalogReader;
import org.shakvilla.beatzmedia.library.application.port.out.CollectionRepository;
import org.shakvilla.beatzmedia.library.domain.FollowKind;
import org.shakvilla.beatzmedia.library.domain.TargetNotFoundException;

/** Application service for PUT/DELETE /v1/me/follows/{kind}/:id. LLFR-LIBRARY-01.3. */
@ApplicationScoped
public class ToggleFollowService implements ToggleFollow {

  private final CollectionRepository repo;
  private final CatalogReader catalogReader;

  @Inject
  public ToggleFollowService(CollectionRepository repo, CatalogReader catalogReader) {
    this.repo = repo;
    this.catalogReader = catalogReader;
  }

  @Override
  @Transactional
  public void follow(AccountId account, FollowKind kind, String targetId) {
    validateExists(kind, targetId);
    repo.addFollow(account, kind, targetId);
  }

  @Override
  @Transactional
  public void unfollow(AccountId account, FollowKind kind, String targetId) {
    repo.removeFollow(account, kind, targetId);
  }

  private void validateExists(FollowKind kind, String targetId) {
    boolean exists =
        switch (kind) {
          case artist -> catalogReader.artistExists(targetId);
          case playlist -> catalogReader.playlistExists(targetId);
          case show -> catalogReader.showExists(targetId);
        };
    if (!exists) {
      String code =
          switch (kind) {
            case artist -> "ARTIST_NOT_FOUND";
            case playlist -> "PLAYLIST_NOT_FOUND";
            case show -> "SHOW_NOT_FOUND";
          };
      throw new TargetNotFoundException(code, targetId);
    }
  }
}
