package org.shakvilla.beatzmedia.commerce.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.commerce.application.port.in.ListOrders;
import org.shakvilla.beatzmedia.commerce.application.port.in.OrderSnapshot;
import org.shakvilla.beatzmedia.commerce.application.port.out.OrderRepository;
import org.shakvilla.beatzmedia.commerce.domain.Order;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Application service for {@code GET /v1/me/orders} ({@link ListOrders}, LLFR-COMMERCE-02.4). Returns
 * the caller's OWN orders only (scoped by account), newest first, projected onto {@link OrderSnapshot}.
 */
@ApplicationScoped
public class ListOrdersService implements ListOrders {

  private final OrderRepository orderRepository;

  @Inject
  public ListOrdersService(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  @Override
  @Transactional
  public Page<OrderSnapshot> listOrders(AccountId account, PageRequest page) {
    Page<Order> orders = orderRepository.findByAccount(account, page);
    return new Page<>(
        orders.items().stream().map(OrderSnapshot::of).toList(),
        orders.page(),
        orders.size(),
        orders.total());
  }
}
