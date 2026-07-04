package org.shakvilla.beatzmedia.payments.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Unit tests for the {@link Dispute} state machine (payments ADD §8, LLFR-PAYMENTS-04.*). Proves the
 * guarded transitions {@code open → refunded | rejected | escalated}, their idempotency, and that a
 * terminal/escalated dispute cannot be refunded (INV-9 — no clawback off a non-open dispute).
 */
@Tag("unit")
class DisputeTest {

  private static Dispute open() {
    return Dispute.open(
        new DisputeId("d-1"),
        "BZ-2026-0001",
        "intent-1",
        "Refund request",
        "@ama_b",
        "album not delivered",
        Money.ofMinor(1899, Currency.GHS),
        false,
        Instant.now());
  }

  @Test
  void open_to_refunded_transitions_once_then_is_idempotent() {
    Dispute d = open();
    assertTrue(d.markRefunded(), "first refund performs the transition");
    assertEquals(DisputeStatus.refunded, d.getStatus());
    assertFalse(d.markRefunded(), "a second refund is a no-op (idempotent)");
  }

  @Test
  void open_to_rejected_transitions_once_then_is_idempotent() {
    Dispute d = open();
    assertTrue(d.markRejected());
    assertEquals(DisputeStatus.rejected, d.getStatus());
    assertFalse(d.markRejected());
  }

  @Test
  void open_to_escalated_transitions_once_then_is_idempotent() {
    Dispute d = open();
    assertTrue(d.markEscalated());
    assertEquals(DisputeStatus.escalated, d.getStatus());
    assertFalse(d.markEscalated());
  }

  @Test
  void a_rejected_dispute_cannot_be_refunded() {
    Dispute d = open();
    d.markRejected();
    assertThrows(IllegalTransitionException.class, d::markRefunded);
  }

  @Test
  void an_escalated_dispute_cannot_be_refunded_until_reopened() {
    Dispute d = open();
    d.markEscalated();
    assertThrows(IllegalTransitionException.class, d::markRefunded);
  }

  @Test
  void a_refunded_dispute_cannot_be_rejected_or_escalated() {
    Dispute d = open();
    d.markRefunded();
    assertThrows(IllegalTransitionException.class, d::markRejected);
    assertThrows(IllegalTransitionException.class, d::markEscalated);
  }

  @Test
  void a_lost_chargeback_forces_refund_from_open() {
    Dispute d = open();
    assertTrue(d.forceRefundedFromChargeback());
    assertEquals(DisputeStatus.refunded, d.getStatus());
  }

  @Test
  void a_lost_chargeback_forces_refund_even_from_escalated_F2() {
    // F2: a LOST chargeback OVERRIDES an admin escalation — escalated → refunded is allowed via the
    // chargeback force-transition (a normal markRefunded from escalated would throw).
    Dispute d = open();
    d.markEscalated();
    assertThrows(IllegalTransitionException.class, d::markRefunded);
    assertTrue(d.forceRefundedFromChargeback(), "lost chargeback forces escalated → refunded");
    assertEquals(DisputeStatus.refunded, d.getStatus());
  }

  @Test
  void a_lost_chargeback_force_refund_is_idempotent_and_blocked_from_rejected() {
    Dispute refunded = open();
    refunded.forceRefundedFromChargeback();
    assertFalse(refunded.forceRefundedFromChargeback(), "re-delivered lost chargeback is a no-op");

    Dispute rejected = open();
    rejected.markRejected();
    assertThrows(IllegalTransitionException.class, rejected::forceRefundedFromChargeback);
  }

  @Test
  void a_chargeback_dispute_carries_its_flag() {
    Dispute d =
        Dispute.open(
            new DisputeId("d-2"),
            "BZ-2026-0481",
            "intent-2",
            "Chargeback",
            "card ···4421",
            "provider chargeback",
            Money.ofMinor(18000, Currency.GHS),
            true,
            Instant.now());
    assertTrue(d.isChargeback());
    assertEquals("intent-2", d.getPaymentIntentId());
  }
}
