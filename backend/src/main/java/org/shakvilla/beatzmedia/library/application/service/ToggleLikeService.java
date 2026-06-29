package org.shakvilla.beatzmedia.library.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.in.ToggleLike;
import org.shakvilla.beatzmedia.library.application.port.out.CatalogReader;
import org.shakvilla.beatzmedia.library.application.port.out.CollectionRepository;
import org.shakvilla.beatzmedia.library.domain.TargetNotFoundException;

/** Application service for PUT/DELETE /v1/me/likes/tracks/:id. LLFR-LIBRARY-01.2. */
@ApplicationScoped
public class ToggleLikeService implements ToggleLike {

  private final CollectionRepository repo;
  private final CatalogReader catalogReader;

  @Inject
  public ToggleLikeService(CollectionRepository repo, CatalogReader catalogReader) {
    this.repo = repo;
    this.catalogReader = catalogReader;
  }

  @Override
  @Transactional
  public void like(AccountId account, String trackId) {
    if (!catalogReader.trackExists(trackId)) {
      throw new TargetNotFoundException("TRACK_NOT_FOUND", trackId);
    }
    repo.addLike(account, trackId);
  }

  @Override
  @Transactional
  public void unlike(AccountId account, String trackId) {
    repo.removeLike(account, trackId);
  }
}
