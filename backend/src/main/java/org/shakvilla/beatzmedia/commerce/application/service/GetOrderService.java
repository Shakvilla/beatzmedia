package org.shakvilla.beatzmedia.commerce.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.commerce.application.port.in.GetOrder;
import org.shakvilla.beatzmedia.commerce.application.port.in.OrderSnapshot;
import org.shakvilla.beatzmedia.commerce.application.port.out.OrderRepository;
import org.shakvilla.beatzmedia.commerce.domain.Order;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.commerce.domain.OrderNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Application service for {@code GET /v1/me/orders/{orderId}} ({@link GetOrder}, WU-COM-3). Loads
 * by id and asserts caller ownership before returning; both "no such order" and "someone else's
 * order" throw the SAME {@link OrderNotFoundException} (§2.2).
 */
@ApplicationScoped
public class GetOrderService implements GetOrder {

  private final OrderRepository orderRepository;

  @Inject
  public GetOrderService(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  @Override
  @Transactional
  public OrderSnapshot getOrder(AccountId account, OrderId orderId) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId.value()));
    if (!order.getAccountId().equals(account)) {
      throw new OrderNotFoundException(orderId.value());
    }
    return OrderSnapshot.of(order);
  }
}
