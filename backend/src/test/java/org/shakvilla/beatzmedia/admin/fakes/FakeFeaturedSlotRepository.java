package org.shakvilla.beatzmedia.admin.fakes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.shakvilla.beatzmedia.admin.application.port.out.FeaturedSlotRepository;
import org.shakvilla.beatzmedia.admin.domain.FeaturedSlot;

/**
 * In-memory fake for {@link FeaturedSlotRepository}. Testing-strategy §2.
 */
public class FakeFeaturedSlotRepository implements FeaturedSlotRepository {

  private final List<FeaturedSlot> slots = new ArrayList<>();

  public void seed(FeaturedSlot slot) {
    slots.add(slot);
  }

  @Override
  public List<FeaturedSlot> listOrdered() {
    return slots.stream().sorted(Comparator.comparingInt(FeaturedSlot::getPosition)).toList();
  }

  @Override
  public List<FeaturedSlot> replaceAll(List<FeaturedSlot> ordered) {
    slots.clear();
    slots.addAll(ordered);
    return listOrdered();
  }
}
