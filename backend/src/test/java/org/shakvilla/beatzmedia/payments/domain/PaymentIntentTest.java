package org.shakvilla.beatzmedia.payments.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Unit tests for the {@link PaymentIntent} aggregate and its state machine (payments ADD §8).
 * Framework-free; plain JUnit 5. Asserts INV-1 (no settlement without an explicit guarded
 * transition) and INV-11 (money in minor units).
 */
@Tag("unit")
class PaymentIntentTest {

  private static final Instant T0 = Instant.parse("2026-06-22T12:00:00Z");
  private static final Instant T1 = Instant.parse("2026-06-22T12:05:00Z");

  private static PaymentIntent newPending() {
    return PaymentIntent.create(
        "pi-1",
        new AccountId("acct-1"),
        new OrderRef("BZ-2026-00001"),
        Money.ofMinor(1000, Currency.GHS),
        new PaymentMethodRef(Provider.mtn, MethodKind.momo, "tok-123"),
        new IdempotencyKey("idem-1"),
        "fp-1",
        T0);
  }

  @Test
  void create_starts_pending_with_no_provider_ref() {
    PaymentIntent intent = newPending();
    assertEquals(PaymentIntentStatus.pending, intent.getStatus());
    assertNull(intent.getProviderRef());
    assertEquals(1000, intent.getAmount().minor());
    assertEquals(Provider.mtn, intent.getProvider());
    assertEquals(MethodKind.momo, intent.getMethodKind());
    assertEquals("acct-1", intent.getAccountId().value());
  }

  @Test
  void create_rejects_null_account() {
    assertThrows(
        NullPointerException.class,
        () ->
            PaymentIntent.create(
                "pi-n",
                null,
                new OrderRef("BZ-2026-00003"),
                Money.ofMinor(500, Currency.GHS),
                new PaymentMethodRef(Provider.mtn, MethodKind.momo, "tok"),
                new IdempotencyKey("idem-n"),
                "fp-n",
                T0));
  }

  @Test
  void create_rejects_negative_amount() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PaymentIntent.create(
                "pi-x",
                new AccountId("acct-1"),
                new OrderRef("BZ-2026-00002"),
                Money.ofMinor(-1, Currency.GHS),
                new PaymentMethodRef(Provider.card, MethodKind.card, "tok"),
                new IdempotencyKey("idem-x"),
                "fp-x",
                T0));
  }

  @Test
  void markInitiated_sets_provider_ref_and_keeps_pending() {
    PaymentIntent intent = newPending();
    intent.markInitiated("MTN-abc", T1);
    assertEquals("MTN-abc", intent.getProviderRef());
    assertEquals(PaymentIntentStatus.pending, intent.getStatus());
    assertEquals(T1, intent.getUpdatedAt());
  }

  @Test
  void pending_transitions_to_settled() {
    PaymentIntent intent = newPending();
    intent.markSettled("MTN-abc", T1);
    assertEquals(PaymentIntentStatus.settled, intent.getStatus());
    assertEquals("MTN-abc", intent.getProviderRef());
  }

  @Test
  void pending_transitions_to_failed_with_reason() {
    PaymentIntent intent = newPending();
    intent.markFailed("insufficient funds", T1);
    assertEquals(PaymentIntentStatus.failed, intent.getStatus());
    assertEquals("insufficient funds", intent.getFailureReason());
  }

  @Test
  void pending_transitions_to_timeout() {
    PaymentIntent intent = newPending();
    intent.markTimedOut(T1);
    assertEquals(PaymentIntentStatus.timeout, intent.getStatus());
  }

  @Test
  void settle_is_idempotent_no_op_when_already_settled() {
    PaymentIntent intent = newPending();
    intent.markSettled("MTN-abc", T1);
    // Duplicate settlement (e.g. duplicate webhook) is a safe no-op, not an error.
    intent.markSettled("MTN-abc", T1);
    assertEquals(PaymentIntentStatus.settled, intent.getStatus());
  }

  @Test
  void cannot_settle_a_failed_intent() {
    PaymentIntent intent = newPending();
    intent.markFailed("declined", T1);
    assertThrows(IllegalTransitionException.class, () -> intent.markSettled("MTN-abc", T1));
  }

  @Test
  void cannot_fail_a_settled_intent() {
    PaymentIntent intent = newPending();
    intent.markSettled("MTN-abc", T1);
    assertThrows(IllegalTransitionException.class, () -> intent.markFailed("late", T1));
  }

  @Test
  void cannot_attach_provider_ref_after_terminal() {
    PaymentIntent intent = newPending();
    intent.markSettled("MTN-abc", T1);
    assertThrows(IllegalTransitionException.class, () -> intent.markInitiated("MTN-xyz", T1));
  }

  @Test
  void status_transition_rules() {
    assertTrue(PaymentIntentStatus.pending.canTransitionTo(PaymentIntentStatus.settled));
    assertTrue(PaymentIntentStatus.pending.canTransitionTo(PaymentIntentStatus.failed));
    assertTrue(PaymentIntentStatus.pending.canTransitionTo(PaymentIntentStatus.timeout));
    assertFalse(PaymentIntentStatus.pending.canTransitionTo(PaymentIntentStatus.pending));
    assertFalse(PaymentIntentStatus.settled.canTransitionTo(PaymentIntentStatus.failed));
    assertTrue(PaymentIntentStatus.settled.isTerminal());
    assertFalse(PaymentIntentStatus.pending.isTerminal());
  }

  @Test
  void fingerprint_matching() {
    PaymentIntent intent = newPending();
    assertTrue(intent.matchesFingerprint("fp-1"));
    assertFalse(intent.matchesFingerprint("fp-2"));
  }
}
