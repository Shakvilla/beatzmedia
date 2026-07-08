package org.shakvilla.beatzmedia.store.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.store.application.port.in.ListStore;
import org.shakvilla.beatzmedia.store.application.port.in.StoreItemView;
import org.shakvilla.beatzmedia.store.application.port.out.StoreRepository;
import org.shakvilla.beatzmedia.store.domain.StoreItem;

/** Application service for LLFR-STORE-01.1 (browse the store catalog feed). Store ADD §4.1. */
@ApplicationScoped
@Transactional
public class ListStoreService implements ListStore {

  private final StoreRepository repository;

  @Inject
  public ListStoreService(StoreRepository repository) {
    this.repository = repository;
  }

  @Override
  public Page<StoreItemView> list(StoreQuery query, PageRequest page) {
    Page<StoreItem> items = repository.find(query, query.sort(), page);
    return new Page<>(
        items.items().stream().map(StoreMapper::toView).toList(),
        items.page(),
        items.size(),
        items.total());
  }
}
