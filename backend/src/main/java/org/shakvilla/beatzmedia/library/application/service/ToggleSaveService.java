package org.shakvilla.beatzmedia.library.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.in.ToggleSave;
import org.shakvilla.beatzmedia.library.application.port.out.CatalogReader;
import org.shakvilla.beatzmedia.library.application.port.out.CollectionRepository;
import org.shakvilla.beatzmedia.library.domain.TargetNotFoundException;

/** Application service for PUT/DELETE /v1/me/saved/albums/:id. LLFR-LIBRARY-01.4. */
@ApplicationScoped
public class ToggleSaveService implements ToggleSave {

  private final CollectionRepository repo;
  private final CatalogReader catalogReader;

  @Inject
  public ToggleSaveService(CollectionRepository repo, CatalogReader catalogReader) {
    this.repo = repo;
    this.catalogReader = catalogReader;
  }

  @Override
  @Transactional
  public void save(AccountId account, String albumId) {
    if (!catalogReader.albumExists(albumId)) {
      throw new TargetNotFoundException("ALBUM_NOT_FOUND", albumId);
    }
    repo.addSave(account, albumId);
  }

  @Override
  @Transactional
  public void unsave(AccountId account, String albumId) {
    repo.removeSave(account, albumId);
  }
}
