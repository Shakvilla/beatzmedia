package org.shakvilla.beatzmedia.commerce.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.commerce.domain.Order;

/**
 * Read-model / DTO for an order (receipt + history). Mirrors the frontend {@code OrderSnapshot} shape
 * (Commerce ADD §6 / API-CONTRACT.md §6):
 * {@code { items, subtotal, fee, total, reference, orderId, status, createdAt }}. Money is the wire
 * {@code { amount, currency }} form (INV-11); timestamps are ISO-8601.
 */
public record OrderSnapshot(
    List<OrderLineView> items,
    MoneyView subtotal,
    MoneyView fee,
    MoneyView total,
    String reference,
    String orderId,
    String status,
    String createdAt) {

  /** Project a domain aggregate onto the wire shape. */
  public static OrderSnapshot of(Order order) {
    String currency = order.getTotal().currency().name();
    List<OrderLineView> lines =
        order.getLines().stream()
            .map(
                l ->
                    new OrderLineView(
                        l.getId(),
                        l.getKind().wireValue(),
                        l.getRefId(),
                        l.getTitle(),
                        l.getSubtitle(),
                        l.getImage(),
                        MoneyView.ofMinor(l.getUnitPrice().minor(), currency),
                        l.getQty()))
            .toList();
    return new OrderSnapshot(
        lines,
        MoneyView.ofMinor(order.getSubtotal().minor(), currency),
        MoneyView.ofMinor(order.getFee().minor(), currency),
        MoneyView.ofMinor(order.getTotal().minor(), currency),
        order.getReference(),
        order.getId().value(),
        order.getStatus().wireValue(),
        order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
  }
}
