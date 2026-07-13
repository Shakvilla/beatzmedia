package org.shakvilla.beatzmedia.payments.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Unit tests for the WU-PAY-2 domain types: {@link PaymentEvent} / {@link ReconciliationDiscrepancy}
 * invariants and the {@link PaymentSettled}/{@link PaymentFailed} intent→event factories.
 */
@Tag("unit")
class WebhookDomainTest {

  private static final Instant NOW = Instant.parse("2026-07-02T12:00:00Z");

  @Test
  void payment_event_rejects_blank_provider_event_id() {
    assertThrows(
        IllegalArgumentException.class,
        () -> PaymentEvent.record("id", "intent", "  ", PaymentEventType.SETTLED, "{}", NOW));
  }

  @Test
  void payment_event_rejects_nulls() {
    assertThrows(
        NullPointerException.class,
        () -> PaymentEvent.record(null, "intent", "ev", PaymentEventType.SETTLED, "{}", NOW));
  }

  @Test
  void discrepancy_rejects_null_kind() {
    assertThrows(
        NullPointerException.class,
        () ->
            ReconciliationDiscrepancy.of(
                "id", "intent", "BZ-1", null, 1000, "SETTLED", "timeout", "2026-07-02", NOW));
  }

  @Test
  void discrepancy_exposes_its_fields() {
    ReconciliationDiscrepancy d =
        ReconciliationDiscrepancy.of(
            "d-1",
            "pi-1",
            "BZ-1",
            DiscrepancyKind.PROVIDER_SETTLED_INTENT_NOT,
            1000,
            "SETTLED",
            "timeout",
            "2026-07-02",
            NOW);

    assertEquals("d-1", d.getId());
    assertEquals("pi-1", d.getIntentId());
    assertEquals(DiscrepancyKind.PROVIDER_SETTLED_INTENT_NOT, d.getKind());
    assertEquals(1000, d.getAmountMinor());
    assertEquals("2026-07-02", d.getAsOfDay());
  }

  @Test
  void payment_settled_factory_snapshots_the_intent() {
    PaymentIntent intent = settledIntent();

    PaymentSettled event = PaymentSettled.from(intent, NOW);

    assertEquals("pi-1", event.intentId());
    assertEquals("BZ-2026-1", event.orderRef());
    assertEquals("acct-1", event.accountId());
    assertEquals(1000, event.amountMinor());
    assertEquals("GHS", event.currency());
    assertEquals("mtn", event.provider());
    assertEquals("MTN-1", event.providerRef());
    assertEquals(NOW, event.settledAt());
  }

  @Test
  void payment_failed_factory_carries_the_failure_reason() {
    PaymentIntent intent =
        PaymentIntent.reconstitute(
            "pi-2",
            new AccountId("acct-1"),
            new OrderRef("BZ-2026-1"),
            Money.ofMinor(1000, Currency.GHS),
            Provider.mtn,
            MethodKind.momo,
            "MTN-2",
            PaymentIntentStatus.timeout,
            "timeout",
            null, // checkoutUrl (WU-PAY-6)
            "idem-2",
            "fp-2",
            NOW,
            NOW);

    PaymentFailed event = PaymentFailed.from(intent, NOW);

    assertEquals("timeout", event.reason());
    assertEquals("pi-2", event.intentId());
  }

  private static PaymentIntent settledIntent() {
    return PaymentIntent.reconstitute(
        "pi-1",
        new AccountId("acct-1"),
        new OrderRef("BZ-2026-1"),
        Money.ofMinor(1000, Currency.GHS),
        Provider.mtn,
        MethodKind.momo,
        "MTN-1",
        PaymentIntentStatus.settled,
        null,
        null, // checkoutUrl (WU-PAY-6)
        "idem-1",
        "fp-1",
        NOW,
        NOW);
  }
}
