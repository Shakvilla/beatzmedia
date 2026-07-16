package org.shakvilla.beatzmedia.notifications.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.domain.TipReceived;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test for the WU-POD-2 tip-notification producer
 * ({@link org.shakvilla.beatzmedia.notifications.adapter.in.events.NotificationEventObservers}) over a
 * real Testcontainers Postgres. Proves that a settled {@link TipReceived} — delivered exactly as
 * payments fires it, {@code AFTER_SUCCESS} of the ledger-posting transaction — results in a durable
 * in-app notification row for the creator.
 *
 * <p><strong>Why fire through CDI inside a committed transaction (not a direct observer call).</strong>
 * The bug this guards against ({@code TransactionRequiredException} — "Transaction is not active") only
 * manifests in the genuine {@code AFTER_SUCCESS} phase, where the producing transaction has already
 * committed and is merely completing. A direct call to the observer with no ambient transaction would
 * let its {@code @Transactional(REQUIRED)} delegate start a fresh transaction and hide the defect. So
 * this test fires {@code TipReceived} via {@link Event} inside a real committed transaction: the
 * transactional observer then runs in the same completing context production uses. Without the fix the
 * observer throws (ARC logs it, swallows it) and NO row is written; with the observer on its own
 * {@code REQUIRES_NEW} boundary, the notification is persisted.
 */
@QuarkusTest
@Tag("integration")
class TipNotificationObserverIT {

  @Inject Event<TipReceived> tipReceivedEvent;
  @Inject EntityManager em;

  @Test
  void settledTip_firedAfterSuccess_persistsCreatorNotification() {
    long n = System.nanoTime();
    String intentId = "it-tip-intent-" + n;
    String creatorId = "it-creator-" + n;
    String dedupeKey = "tip:" + intentId + ":" + creatorId;

    TipReceived tip =
        new TipReceived(intentId, "it-fan-" + n, creatorId, 1000, 900, 100, "GHS",
            Instant.parse("2026-07-16T09:00:00Z"));

    // Fire the event inside a COMMITTED transaction, exactly as payments' TipLedgerPoster does. The
    // AFTER_SUCCESS observers run synchronously during this transaction's commit.
    QuarkusTransaction.requiringNew().run(() -> tipReceivedEvent.fire(tip));

    // Read in a FRESH transaction, as a subsequent HTTP request would — the notification the observer
    // wrote in its own REQUIRES_NEW transaction must be durably visible.
    long rows =
        QuarkusTransaction.requiringNew().call(() -> notificationCount(dedupeKey, creatorId));

    assertEquals(
        1L,
        rows,
        "AFTER_SUCCESS tip observer must persist exactly one in-app notification for the creator");
  }

  private long notificationCount(String dedupeKey, String recipientId) {
    Object v =
        em.createNativeQuery(
                "SELECT count(*) FROM notification"
                    + " WHERE dedupe_key = :key AND recipient_id = :rid AND type = 'tip'")
            .setParameter("key", dedupeKey)
            .setParameter("rid", recipientId)
            .getSingleResult();
    return ((Number) v).longValue();
  }
}
