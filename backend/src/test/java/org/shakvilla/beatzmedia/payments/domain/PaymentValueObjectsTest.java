package org.shakvilla.beatzmedia.payments.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the payments domain value objects and their guard branches (blank/null validation,
 * case-insensitive {@code fromWire} parsing). Framework-free; plain JUnit 5. Keeps the domain-layer
 * branch/line coverage complete.
 */
@Tag("unit")
class PaymentValueObjectsTest {

  // ---- AccountId ----

  @Test
  void accountId_accepts_value_and_toString() {
    AccountId id = new AccountId("acct-1");
    assertEquals("acct-1", id.value());
    assertEquals("acct-1", id.toString());
  }

  @Test
  void accountId_rejects_null_and_blank() {
    assertThrows(IllegalArgumentException.class, () -> new AccountId(null));
    assertThrows(IllegalArgumentException.class, () -> new AccountId(" "));
  }

  // ---- OrderRef ----

  @Test
  void orderRef_accepts_value() {
    assertEquals("BZ-2026-1", new OrderRef("BZ-2026-1").value());
  }

  @Test
  void orderRef_rejects_null_and_blank() {
    assertThrows(IllegalArgumentException.class, () -> new OrderRef(null));
    assertThrows(IllegalArgumentException.class, () -> new OrderRef(""));
  }

  // ---- IdempotencyKey ----

  @Test
  void idempotencyKey_accepts_value() {
    assertEquals("k-1", new IdempotencyKey("k-1").value());
  }

  @Test
  void idempotencyKey_rejects_null_and_blank() {
    assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey(null));
    assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey("  "));
  }

  // ---- PaymentMethodRef ----

  @Test
  void paymentMethodRef_accepts_values() {
    PaymentMethodRef ref = new PaymentMethodRef(Provider.mtn, MethodKind.momo, "tok");
    assertEquals(Provider.mtn, ref.provider());
    assertEquals(MethodKind.momo, ref.kind());
    assertEquals("tok", ref.token());
  }

  @Test
  void paymentMethodRef_rejects_null_provider_kind_and_blank_token() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new PaymentMethodRef(null, MethodKind.momo, "tok"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PaymentMethodRef(Provider.mtn, null, "tok"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PaymentMethodRef(Provider.mtn, MethodKind.momo, " "));
  }

  // ---- Provider.fromWire ----

  @Test
  void provider_fromWire_is_case_insensitive() {
    assertEquals(Provider.mtn, Provider.fromWire("MTN"));
    assertEquals(Provider.telecel, Provider.fromWire(" telecel "));
    assertEquals(Provider.airteltigo, Provider.fromWire("AirtelTigo"));
    assertEquals(Provider.card, Provider.fromWire("card"));
    assertEquals(Provider.bank, Provider.fromWire("bank"));
  }

  @Test
  void provider_fromWire_rejects_null_and_unknown() {
    assertThrows(IllegalArgumentException.class, () -> Provider.fromWire(null));
    assertThrows(IllegalArgumentException.class, () -> Provider.fromWire("paypal"));
  }

  // ---- MethodKind.fromWire ----

  @Test
  void methodKind_fromWire_is_case_insensitive() {
    assertEquals(MethodKind.momo, MethodKind.fromWire("MOMO"));
    assertEquals(MethodKind.bank, MethodKind.fromWire(" bank "));
    assertEquals(MethodKind.card, MethodKind.fromWire("Card"));
  }

  @Test
  void methodKind_fromWire_rejects_null_and_unknown() {
    assertThrows(IllegalArgumentException.class, () -> MethodKind.fromWire(null));
    assertThrows(IllegalArgumentException.class, () -> MethodKind.fromWire("crypto"));
  }

  // ---- PaymentIntentStatus transition rules ----

  @Test
  void status_isTerminal_and_transitions() {
    assertEquals(false, PaymentIntentStatus.pending.isTerminal());
    assertEquals(true, PaymentIntentStatus.settled.isTerminal());
    assertEquals(true, PaymentIntentStatus.failed.isTerminal());
    assertEquals(true, PaymentIntentStatus.timeout.isTerminal());
    assertEquals(true, PaymentIntentStatus.pending.canTransitionTo(PaymentIntentStatus.failed));
    assertEquals(false, PaymentIntentStatus.failed.canTransitionTo(PaymentIntentStatus.settled));
  }

  // ---- domain exceptions carry the right error codes ----

  @Test
  void domain_exceptions_carry_error_codes() {
    assertEquals(
        org.shakvilla.beatzmedia.platform.domain.ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
        new IdempotencyConflictException("x").getErrorCode());
    assertEquals(
        org.shakvilla.beatzmedia.platform.domain.ErrorCode.MISSING_IDEMPOTENCY_KEY,
        new MissingIdempotencyKeyException().getErrorCode());
    assertEquals(
        org.shakvilla.beatzmedia.platform.domain.ErrorCode.PROVIDER_ERROR,
        new ProviderException("x").getErrorCode());
    assertEquals(
        org.shakvilla.beatzmedia.platform.domain.ErrorCode.ILLEGAL_TRANSITION,
        new IllegalTransitionException("x").getErrorCode());
  }
}
