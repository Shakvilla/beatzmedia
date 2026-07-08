package org.shakvilla.beatzmedia.store.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.store.application.port.in.GetStoreItem;
import org.shakvilla.beatzmedia.store.application.port.in.StoreItemView;
import org.shakvilla.beatzmedia.store.application.port.out.StoreRepository;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;
import org.shakvilla.beatzmedia.store.domain.StoreItemNotFoundException;

/** Application service for LLFR-STORE-01.2 (store product detail). Store ADD §4.1. */
@ApplicationScoped
@Transactional
public class GetStoreItemService implements GetStoreItem {

  private final StoreRepository repository;

  @Inject
  public GetStoreItemService(StoreRepository repository) {
    this.repository = repository;
  }

  @Override
  public StoreItemView get(StoreItemId id) {
    return repository
        .findById(id)
        .map(StoreMapper::toView)
        .orElseThrow(() -> new StoreItemNotFoundException(id.value()));
  }
}
