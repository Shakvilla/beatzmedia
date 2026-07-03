package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.domain.Order;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.commerce.domain.OrderLine;
import org.shakvilla.beatzmedia.commerce.domain.OrderStatus;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Maps between the {@link Order} domain aggregate and JPA entities. Domain carries no ORM annotations
 * (ArchUnit-enforced); this is the only place the mapping happens. Commerce ADD §5.2.
 */
@ApplicationScoped
public class OrderEntityMapper {

  Order toDomain(OrderEntity e) {
    Currency currency = Currency.valueOf(e.currency);
    List<OrderLine> lines =
        e.lines.stream()
            .map(
                l ->
                    new OrderLine(
                        l.id,
                        CartItemKind.fromWireValue(l.kind),
                        l.refId,
                        l.title,
                        Money.ofMinor(l.unitPriceMinor, Currency.valueOf(l.currency)),
                        l.qty))
            .toList();
    return new Order(
        new OrderId(e.id),
        new AccountId(e.accountId),
        e.reference,
        OrderStatus.valueOf(e.status),
        Money.ofMinor(e.subtotalMinor, currency),
        Money.ofMinor(e.feeMinor, currency),
        Money.ofMinor(e.totalMinor, currency),
        e.paymentIntentId,
        e.failureReason,
        e.idempotencyKey,
        lines,
        e.createdAt);
  }

  OrderEntity toEntity(Order order, OrderEntity target) {
    OrderEntity entity = target != null ? target : new OrderEntity();
    entity.id = order.getId().value();
    entity.accountId = order.getAccountId().value();
    entity.reference = order.getReference();
    entity.status = order.getStatus().name();
    entity.subtotalMinor = order.getSubtotal().minor();
    entity.feeMinor = order.getFee().minor();
    entity.totalMinor = order.getTotal().minor();
    entity.currency = order.getTotal().currency().name();
    entity.paymentIntentId = order.getPaymentIntentId();
    entity.failureReason = order.getFailureReason();
    entity.idempotencyKey = order.getIdempotencyKey();
    entity.createdAt = order.getCreatedAt();

    Map<String, OrderLineEntity> existingById =
        entity.lines.stream().collect(Collectors.toMap(l -> l.id, l -> l));
    List<OrderLineEntity> reconciled = new ArrayList<>();
    for (OrderLine line : order.getLines()) {
      OrderLineEntity le = existingById.get(line.getId());
      if (le == null) {
        le = new OrderLineEntity();
        le.id = line.getId();
      }
      le.order = entity;
      le.kind = line.getKind().wireValue();
      le.refId = line.getRefId();
      le.title = line.getTitle();
      le.unitPriceMinor = line.getUnitPrice().minor();
      le.currency = line.getUnitPrice().currency().name();
      le.qty = line.getQty();
      reconciled.add(le);
    }
    entity.lines.clear();
    entity.lines.addAll(reconciled);
    return entity;
  }
}
