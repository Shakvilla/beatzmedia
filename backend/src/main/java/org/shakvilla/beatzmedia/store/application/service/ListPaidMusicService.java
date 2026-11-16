package org.shakvilla.beatzmedia.store.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.store.application.port.in.ListPaidMusic;
import org.shakvilla.beatzmedia.store.application.port.in.PaidMusicView;
import org.shakvilla.beatzmedia.store.application.port.out.PaidMusicRepository;
import org.shakvilla.beatzmedia.store.domain.StoreSort;

@ApplicationScoped
@Transactional
public class ListPaidMusicService implements ListPaidMusic {
  private final PaidMusicRepository repository;

  @Inject
  public ListPaidMusicService(PaidMusicRepository repository) {
    this.repository = repository;
  }

  @Override
  public Page<PaidMusicView> list(StoreSort sort, PageRequest page) {
    return repository.find(sort, page);
  }
}
