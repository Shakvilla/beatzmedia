package org.shakvilla.beatzmedia.payments.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.payments.application.port.in.PaymentIntentView;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Contract test: verifies {@link PaymentIntentView} matches the payments ADD §6 shape and the
 * frontend {@code PaymentIntent} type:
 *
 * <pre>
 * PaymentIntent { id, orderRef, amount: Money, provider, providerRef, status, createdAt }
 * Money { amount: number (decimal cedis), currency: "GHS" }
 * status: 'pending' | 'settled' | 'failed' | 'timeout'
 * </pre>
 *
 * Money is on the wire as decimal cedis + currency (INV-11); ids/enums are raw strings, never
 * display labels; timestamps are ISO-8601. DoD §11 contract-test requirement.
 */
@Tag("unit")
class PaymentIntentContractTest {

  private static final Set<String> VALID_STATUSES =
      Set.of("pending", "settled", "failed", "timeout");

  private static PaymentIntent intent() {
    return PaymentIntent.create(
        "pi-1",
        new AccountId("acct-1"),
        new OrderRef("BZ-2026-00001"),
        Money.ofMinor(1050, Currency.GHS),
        new PaymentMethodRef(Provider.mtn, MethodKind.momo, "tok"),
        new IdempotencyKey("idem-1"),
        "fp",
        Instant.parse("2026-06-22T12:00:00Z"));
  }

  @Test
  void view_has_all_contract_fields() {
    var names = new HashSet<String>();
    for (var c : PaymentIntentView.class.getRecordComponents()) {
      names.add(c.getName());
    }
    assertTrue(names.contains("id"), "must have 'id'");
    assertTrue(names.contains("orderRef"), "must have 'orderRef'");
    assertTrue(names.contains("amount"), "must have 'amount'");
    assertTrue(names.contains("provider"), "must have 'provider'");
    assertTrue(names.contains("providerRef"), "must have 'providerRef'");
    assertTrue(names.contains("status"), "must have 'status'");
    assertTrue(names.contains("createdAt"), "must have 'createdAt'");
  }

  @Test
  void money_is_wire_shape_decimal_cedis_and_currency() {
    var names = new HashSet<String>();
    for (var c : MoneyView.class.getRecordComponents()) {
      names.add(c.getName());
    }
    assertTrue(names.contains("amount"), "Money must have 'amount'");
    assertTrue(names.contains("currency"), "Money must have 'currency'");

    PaymentIntentView view = PaymentIntentView.of(intent());
    // 1050 pesewas -> 10.50 cedis at the boundary (INV-11).
    assertEquals(0, new BigDecimal("10.50").compareTo(view.amount().amount()));
    assertEquals("GHS", view.amount().currency());
  }

  @Test
  void status_is_a_raw_enum_value_not_a_display_label() {
    PaymentIntentView view = PaymentIntentView.of(intent());
    assertTrue(
        VALID_STATUSES.contains(view.status()),
        "status must be a raw wire value, got: " + view.status());
  }

  @Test
  void ids_and_enums_are_raw_strings_and_timestamp_is_iso8601() {
    PaymentIntentView view = PaymentIntentView.of(intent());
    assertEquals("pi-1", view.id());
    assertEquals("BZ-2026-00001", view.orderRef());
    assertEquals("mtn", view.provider());
    assertNotNull(view.createdAt());
    assertEquals("2026-06-22T12:00:00Z", view.createdAt());
  }
}
