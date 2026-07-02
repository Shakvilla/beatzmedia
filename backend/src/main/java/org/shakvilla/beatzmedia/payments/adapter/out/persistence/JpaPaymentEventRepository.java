package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.payments.application.port.out.PaymentEventRepository;
import org.shakvilla.beatzmedia.payments.domain.PaymentEvent;

/**
 * JPA implementation of {@link PaymentEventRepository}. Writes only the payments module's
 * {@code payment_event} table. Payments ADD §5.2 / V702 migration.
 *
 * <p>{@link #recordEvent} uses an atomic {@code INSERT … ON CONFLICT (provider_event_id) DO NOTHING}:
 * the affected-row count tells us whether this was the first delivery (1) or a duplicate/replay (0),
 * <em>without</em> raising a constraint violation that would poison the caller's transaction. This is
 * the durable idempotency backstop for {@code HandleProviderWebhook} — concurrent duplicate webhooks
 * race on the UNIQUE constraint and exactly one wins the insert (LLFR-PAYMENTS-01.2 AC).
 */
@ApplicationScoped
public class JpaPaymentEventRepository implements PaymentEventRepository {

  private final EntityManager em;

  @Inject
  public JpaPaymentEventRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean recordEvent(PaymentEvent event) {
    int rows =
        em.createNativeQuery(
                "INSERT INTO payment_event "
                    + "(id, intent_id, provider_event_id, type, payload, received_at) "
                    + "VALUES (:id, :intentId, :providerEventId, :type, CAST(:payload AS jsonb),"
                    + " :receivedAt) "
                    + "ON CONFLICT (provider_event_id) DO NOTHING")
            .setParameter("id", event.getId())
            .setParameter("intentId", event.getIntentId())
            .setParameter("providerEventId", event.getProviderEventId())
            .setParameter("type", event.getType().name())
            .setParameter("payload", event.getPayload())
            .setParameter("receivedAt", event.getReceivedAt())
            .executeUpdate();
    return rows == 1;
  }
}
