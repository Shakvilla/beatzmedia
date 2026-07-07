package org.shakvilla.beatzmedia.admin.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.admin.application.port.in.ListFeaturedSlots;
import org.shakvilla.beatzmedia.admin.application.port.out.FeaturedSlotRepository;
import org.shakvilla.beatzmedia.admin.domain.FeaturedSlot;

/**
 * Application service for LLFR-ADMIN-06.1 (list featured slots). Read-only; not audited. Admin
 * ADD §4.1.
 */
@ApplicationScoped
public class ListFeaturedSlotsService implements ListFeaturedSlots {

  private final FeaturedSlotRepository featuredSlots;

  @Inject
  public ListFeaturedSlotsService(FeaturedSlotRepository featuredSlots) {
    this.featuredSlots = featuredSlots;
  }

  @Override
  public List<FeaturedSlot> list() {
    return featuredSlots.listOrdered();
  }
}
