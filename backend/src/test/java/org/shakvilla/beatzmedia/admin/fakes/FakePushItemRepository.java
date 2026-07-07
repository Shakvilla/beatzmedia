package org.shakvilla.beatzmedia.admin.fakes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.shakvilla.beatzmedia.admin.application.port.out.PushItemRepository;
import org.shakvilla.beatzmedia.admin.domain.PushItem;

/**
 * In-memory fake for {@link PushItemRepository}. Testing-strategy §2.
 */
public class FakePushItemRepository implements PushItemRepository {

  private final List<PushItem> items = new ArrayList<>();

  @Override
  public List<PushItem> list() {
    return items.stream()
        .sorted(Comparator.comparing(
            PushItem::getScheduledAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }

  @Override
  public PushItem save(PushItem item) {
    items.add(item);
    return item;
  }

  public int size() {
    return items.size();
  }
}
