package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.payments.application.port.out.PaymentRepository;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;

/**
 * JPA implementation of {@link PaymentRepository}. Reads/writes only the payments module's
 * {@code payment_intent} table; no cross-module joins. Transaction boundary = the calling
 * application service ({@code @Transactional}). Payments ADD §5.2.
 */
@ApplicationScoped
public class JpaPaymentRepository implements PaymentRepository {

  private final EntityManager em;

  @Inject
  public JpaPaymentRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public Optional<PaymentIntent> findByIdempotencyKey(IdempotencyKey key) {
    List<PaymentIntentEntity> rows =
        em.createQuery(
                "SELECT p FROM PaymentIntentEntity p WHERE p.idempotencyKey = :k",
                PaymentIntentEntity.class)
            .setParameter("k", key.value())
            .setMaxResults(1)
            .getResultList();
    return rows.stream().findFirst().map(PaymentIntentMapper::toDomain);
  }

  @Override
  public PaymentIntent save(PaymentIntent intent) {
    PaymentIntentEntity existing = em.find(PaymentIntentEntity.class, intent.getId());
    PaymentIntentEntity entity = PaymentIntentMapper.toEntity(intent);
    if (existing == null) {
      em.persist(entity);
    } else {
      em.merge(entity);
    }
    return intent;
  }
}
