package org.shakvilla.beatzmedia.admin.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.admin.application.port.in.ListPushItems;
import org.shakvilla.beatzmedia.admin.application.port.out.PushItemRepository;
import org.shakvilla.beatzmedia.admin.domain.PushItem;

/**
 * Application service for LLFR-ADMIN-06.1 (list push schedule). Read-only; not audited. Admin ADD
 * §4.1.
 */
@ApplicationScoped
public class ListPushItemsService implements ListPushItems {

  private final PushItemRepository pushItems;

  @Inject
  public ListPushItemsService(PushItemRepository pushItems) {
    this.pushItems = pushItems;
  }

  @Override
  public List<PushItem> list() {
    return pushItems.list();
  }
}
