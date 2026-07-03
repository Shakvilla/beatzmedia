package org.shakvilla.beatzmedia.commerce.fakes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.commerce.application.port.out.OrderRepository;
import org.shakvilla.beatzmedia.commerce.domain.Order;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/** In-memory fake {@link OrderRepository} for unit tests. */
public class FakeOrderRepository implements OrderRepository {

  private final Map<String, Order> byId = new LinkedHashMap<>();

  @Override
  public Order save(Order order) {
    byId.put(order.getId().value(), order);
    return order;
  }

  @Override
  public Optional<Order> findById(OrderId id) {
    return Optional.ofNullable(byId.get(id.value()));
  }

  @Override
  public Optional<Order> findByReference(String reference) {
    return byId.values().stream().filter(o -> o.getReference().equals(reference)).findFirst();
  }

  @Override
  public Optional<Order> findByReferenceForUpdate(String reference) {
    return findByReference(reference);
  }

  @Override
  public Optional<Order> findByAccountAndIdempotencyKey(AccountId account, String idempotencyKey) {
    return byId.values().stream()
        .filter(
            o ->
                o.getAccountId().value().equals(account.value())
                    && idempotencyKey.equals(o.getIdempotencyKey()))
        .findFirst();
  }

  @Override
  public Page<Order> findByAccount(AccountId account, PageRequest page) {
    List<Order> all =
        byId.values().stream()
            .filter(o -> o.getAccountId().value().equals(account.value()))
            .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
            .toList();
    int from = Math.min(page.offset(), all.size());
    int to = Math.min(from + page.size(), all.size());
    return Page.of(new ArrayList<>(all.subList(from, to)), page.page(), page.size(), all.size());
  }

  /** Test helper: every stored order. */
  public List<Order> all() {
    return new ArrayList<>(byId.values());
  }
}
