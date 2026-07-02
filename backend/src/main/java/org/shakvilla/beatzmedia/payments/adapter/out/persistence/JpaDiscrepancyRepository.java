package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.payments.application.port.out.DiscrepancyRepository;
import org.shakvilla.beatzmedia.payments.domain.ReconciliationDiscrepancy;

/**
 * JPA implementation of {@link DiscrepancyRepository}. Writes only the payments module's
 * {@code reconciliation_discrepancy} table. Payments ADD §5.2 / V702 migration.
 *
 * <p>{@link #record} uses an atomic {@code INSERT … ON CONFLICT (intent_id, kind, as_of_day) DO
 * NOTHING} so re-running the daily reconciliation over the same window records each distinct mismatch
 * at most once (the affected-row count distinguishes a fresh record from a repeat), without a
 * constraint violation poisoning the caller's transaction.
 */
@ApplicationScoped
public class JpaDiscrepancyRepository implements DiscrepancyRepository {

  private final EntityManager em;

  @Inject
  public JpaDiscrepancyRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean record(ReconciliationDiscrepancy d) {
    int rows =
        em.createNativeQuery(
                "INSERT INTO reconciliation_discrepancy "
                    + "(id, intent_id, order_ref, kind, amount_minor, provider_status,"
                    + " intent_status, as_of_day, detected_at) "
                    + "VALUES (:id, :intentId, :orderRef, :kind, :amountMinor, :providerStatus,"
                    + " :intentStatus, :asOfDay, :detectedAt) "
                    + "ON CONFLICT (intent_id, kind, as_of_day) DO NOTHING")
            .setParameter("id", d.getId())
            .setParameter("intentId", d.getIntentId())
            .setParameter("orderRef", d.getOrderRef())
            .setParameter("kind", d.getKind().name())
            .setParameter("amountMinor", d.getAmountMinor())
            .setParameter("providerStatus", d.getProviderStatus())
            .setParameter("intentStatus", d.getIntentStatus())
            .setParameter("asOfDay", d.getAsOfDay())
            .setParameter("detectedAt", d.getDetectedAt())
            .executeUpdate();
    return rows == 1;
  }
}
