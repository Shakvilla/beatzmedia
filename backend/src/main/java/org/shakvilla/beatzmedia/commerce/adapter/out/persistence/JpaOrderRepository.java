package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.shakvilla.beatzmedia.commerce.application.port.out.OrderRepository;
import org.shakvilla.beatzmedia.commerce.domain.Order;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * JPA implementation of {@link OrderRepository}. Reads/writes commerce order tables only ({@code
 * "order"}, {@code order_line}); no cross-module joins. Transaction boundary = the application service.
 * Commerce ADD §5.2.
 */
@ApplicationScoped
public class JpaOrderRepository implements OrderRepository {

  private final EntityManager em;
  private final OrderEntityMapper mapper;

  @Inject
  public JpaOrderRepository(EntityManager em, OrderEntityMapper mapper) {
    this.em = em;
    this.mapper = mapper;
  }

  @Override
  public Order save(Order order) {
    OrderEntity existing = em.find(OrderEntity.class, order.getId().value());
    if (existing == null) {
      OrderEntity entity = mapper.toEntity(order, null);
      em.persist(entity);
      em.flush();
      return mapper.toDomain(entity);
    }
    OrderEntity updated = mapper.toEntity(order, existing);
    em.flush();
    return mapper.toDomain(updated);
  }

  @Override
  public Optional<Order> findById(OrderId id) {
    OrderEntity e = em.find(OrderEntity.class, id.value());
    return Optional.ofNullable(e).map(mapper::toDomain);
  }

  @Override
  public Optional<Order> findByReference(String reference) {
    return em
        .createQuery("SELECT o FROM OrderEntity o WHERE o.reference = :ref", OrderEntity.class)
        .setParameter("ref", reference)
        .getResultStream()
        .findFirst()
        .map(mapper::toDomain);
  }

  @Override
  public Optional<Order> findByReferenceForUpdate(String reference) {
    return em
        .createQuery("SELECT o FROM OrderEntity o WHERE o.reference = :ref", OrderEntity.class)
        .setParameter("ref", reference)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultStream()
        .findFirst()
        .map(mapper::toDomain);
  }

  @Override
  public Optional<Order> findByAccountAndIdempotencyKey(AccountId account, String idempotencyKey) {
    return em
        .createQuery(
            "SELECT o FROM OrderEntity o WHERE o.accountId = :acc AND o.idempotencyKey = :key",
            OrderEntity.class)
        .setParameter("acc", account.value())
        .setParameter("key", idempotencyKey)
        .getResultStream()
        .findFirst()
        .map(mapper::toDomain);
  }

  @Override
  public Page<Order> findByAccount(AccountId account, PageRequest page) {
    long total =
        em.createQuery(
                "SELECT COUNT(o) FROM OrderEntity o WHERE o.accountId = :acc", Long.class)
            .setParameter("acc", account.value())
            .getSingleResult();
    List<OrderEntity> rows =
        em.createQuery(
                "SELECT o FROM OrderEntity o WHERE o.accountId = :acc ORDER BY o.createdAt DESC",
                OrderEntity.class)
            .setParameter("acc", account.value())
            .setFirstResult(page.offset())
            .setMaxResults(page.size())
            .getResultList();
    return Page.of(rows.stream().map(mapper::toDomain).toList(), page.page(), page.size(), total);
  }
}
