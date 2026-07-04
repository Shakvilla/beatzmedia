package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;

import org.shakvilla.beatzmedia.payments.application.port.out.DisputeRepository;
import org.shakvilla.beatzmedia.payments.application.port.out.DuplicateRefundException;
import org.shakvilla.beatzmedia.payments.domain.Dispute;
import org.shakvilla.beatzmedia.payments.domain.DisputeEvent;
import org.shakvilla.beatzmedia.payments.domain.DisputeId;
import org.shakvilla.beatzmedia.payments.domain.DisputeStatus;
import org.shakvilla.beatzmedia.payments.domain.Refund;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * JPA implementation of {@link DisputeRepository} (payments ADD §5.2, WU-PAY-5). Reads/writes only the
 * payments module's V705 tables ({@code dispute}, {@code dispute_event}, {@code refund}); no
 * cross-module joins. Transaction boundary = the calling application service ({@code @Transactional}).
 *
 * <p><strong>Exactly-once refund (INV-9).</strong> {@link #saveRefund} inserts and flushes so a
 * duplicate for the same dispute ({@code uq_refund_per_dispute}, V705) surfaces NOW as a constraint
 * violation, translated to {@link DuplicateRefundException} — the caller treats it as an already
 * refunded no-op rather than double-clawing back (mirrors {@code JpaPayoutRepository.savePayoutTxn}).
 * {@link #findDisputeForUpdate} takes a pessimistic write lock so two concurrent refunds of one
 * dispute serialise on its row.
 */
@ApplicationScoped
public class JpaDisputeRepository implements DisputeRepository {

  private final EntityManager em;

  @Inject
  public JpaDisputeRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public void lockForIdempotencyKey(
      org.shakvilla.beatzmedia.payments.domain.IdempotencyKey key) {
    // Transaction-scoped advisory lock derived from the refund idempotency key so two same-key admin
    // refunds serialise (INV-1 / §9.2), consistent with the other money POSTs. The durable
    // exactly-once backstop remains uq_refund_per_dispute + the ledger_posting claim.
    em.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
        .setParameter("k", advisoryKey(key.value()))
        .getSingleResult();
  }

  @Override
  public Dispute saveDispute(Dispute dispute) {
    DisputeEntity entity = em.find(DisputeEntity.class, dispute.getId().value());
    boolean isNew = entity == null;
    if (isNew) {
      entity = new DisputeEntity();
      entity.id = dispute.getId().value();
      entity.orderRef = dispute.getOrderRef();
      entity.paymentIntentId = dispute.getPaymentIntentId();
      entity.kind = dispute.getKind();
      entity.amountMinor = dispute.getAmount().minor();
      entity.isChargeback = dispute.isChargeback();
      entity.openedAt = dispute.getOpenedAt();
    }
    entity.subject = dispute.getSubject();
    entity.detail = dispute.getDetail();
    entity.status = dispute.getStatus().wire();
    if (isNew) {
      em.persist(entity);
    } else {
      em.merge(entity);
    }
    // Flush so a concurrent duplicate provider-chargeback insert surfaces its UNIQUE violation now.
    em.flush();
    return dispute;
  }

  @Override
  public Optional<Dispute> findDispute(DisputeId id) {
    DisputeEntity entity = em.find(DisputeEntity.class, id.value());
    return Optional.ofNullable(entity).map(JpaDisputeRepository::toDomain);
  }

  @Override
  public Optional<Dispute> findDisputeForUpdate(DisputeId id) {
    DisputeEntity entity = em.find(DisputeEntity.class, id.value(), LockModeType.PESSIMISTIC_WRITE);
    return Optional.ofNullable(entity).map(JpaDisputeRepository::toDomain);
  }

  @Override
  public Optional<Dispute> findByProviderCase(String providerCaseId) {
    if (providerCaseId == null || providerCaseId.isBlank()) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT d FROM DisputeEntity d WHERE d.providerCaseId = :pc", DisputeEntity.class)
        .setParameter("pc", providerCaseId)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst()
        .map(JpaDisputeRepository::toDomain);
  }

  @Override
  public Optional<Dispute> saveChargebackDispute(Dispute dispute, String providerCaseId) {
    try {
      DisputeEntity entity = new DisputeEntity();
      entity.id = dispute.getId().value();
      entity.orderRef = dispute.getOrderRef();
      entity.paymentIntentId = dispute.getPaymentIntentId();
      entity.kind = dispute.getKind();
      entity.subject = dispute.getSubject();
      entity.detail = dispute.getDetail();
      entity.amountMinor = dispute.getAmount().minor();
      entity.isChargeback = true;
      entity.status = dispute.getStatus().wire();
      entity.providerCaseId = providerCaseId;
      entity.openedAt = dispute.getOpenedAt();
      em.persist(entity);
      em.flush();
      return Optional.of(dispute);
    } catch (PersistenceException e) {
      if (isUniqueViolation(e)) {
        // A concurrent/duplicate chargeback for the same provider case already opened a dispute.
        return Optional.empty();
      }
      throw e;
    }
  }

  @Override
  public List<DisputeEvent> timelineOf(DisputeId id) {
    return em
        .createQuery(
            "SELECT e FROM DisputeEventEntity e WHERE e.disputeId = :id ORDER BY e.at ASC",
            DisputeEventEntity.class)
        .setParameter("id", id.value())
        .getResultList()
        .stream()
        .map(e -> DisputeEvent.of(e.id, new DisputeId(e.disputeId), e.text, e.actor, e.at))
        .toList();
  }

  @Override
  public DisputeEvent saveEvent(DisputeEvent event) {
    DisputeEventEntity entity = new DisputeEventEntity();
    entity.id = event.id();
    entity.disputeId = event.disputeId().value();
    entity.text = event.text();
    entity.actor = event.actor();
    entity.at = event.at();
    em.persist(entity);
    return event;
  }

  @Override
  public Refund saveRefund(Refund refund, String clawbackTxnId) {
    try {
      RefundEntity entity = new RefundEntity();
      entity.id = refund.id();
      entity.disputeId = refund.disputeId().value();
      entity.paymentIntentId = refund.paymentIntentId();
      entity.amountMinor = refund.amount().minor();
      entity.reason = refund.reason();
      entity.clawbackTxnId = clawbackTxnId;
      entity.at = refund.at();
      em.persist(entity);
      // Flush so a duplicate refund for the same dispute (uq_refund_per_dispute) surfaces now.
      em.flush();
      return refund;
    } catch (PersistenceException e) {
      if (isUniqueViolation(e)) {
        throw new DuplicateRefundException(refund.disputeId().value(), e);
      }
      throw e;
    }
  }

  private static Dispute toDomain(DisputeEntity e) {
    return new Dispute(
        new DisputeId(e.id),
        e.orderRef,
        e.paymentIntentId,
        e.kind,
        e.subject,
        e.detail,
        Money.ofMinor(e.amountMinor, Currency.GHS),
        e.isChargeback,
        DisputeStatus.fromWire(e.status),
        e.openedAt);
  }

  /** True if the throwable chain is a Postgres unique/PK violation (SQLState 23505). */
  private static boolean isUniqueViolation(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) {
        return true;
      }
      if (t instanceof org.hibernate.exception.ConstraintViolationException) {
        return true;
      }
    }
    return false;
  }

  /** Derive a stable 64-bit advisory-lock key from the idempotency key (SHA-256, first 8 bytes). */
  private static long advisoryKey(String idempotencyKey) {
    try {
      byte[] digest =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(idempotencyKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      long k = 0L;
      for (int i = 0; i < 8; i++) {
        k = (k << 8) | (digest[i] & 0xFFL);
      }
      return k;
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
