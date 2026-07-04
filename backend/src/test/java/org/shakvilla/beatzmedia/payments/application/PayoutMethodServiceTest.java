package org.shakvilla.beatzmedia.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.payments.application.port.in.AddPayoutMethod;
import org.shakvilla.beatzmedia.payments.application.port.in.PayoutMethodView;
import org.shakvilla.beatzmedia.payments.application.service.PayoutMethodService;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodNotFoundException;
import org.shakvilla.beatzmedia.payments.fakes.FakePayoutRepository;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link PayoutMethodService} — LLFR-PAYMENTS-03.1. Proves: the first method is the
 * default, exactly one default per account, ownership-scoping (a creator cannot touch another's
 * method → 404), a card is rejected, and every mutation is audited (INV-10).
 */
@Tag("unit")
class PayoutMethodServiceTest {

  private static final AccountId A = new AccountId("artist-a");
  private static final AccountId B = new AccountId("artist-b");

  private FakePayoutRepository payouts;
  private FakeAuditWriter audit;
  private PayoutMethodService service;

  @BeforeEach
  void setUp() {
    payouts = new FakePayoutRepository();
    audit = new FakeAuditWriter();
    service = new PayoutMethodService(payouts, FakeIds.sequential("pm"), FakeClock.fixed(), audit);
  }

  private AddPayoutMethod.Command momo(String label) {
    return new AddPayoutMethod.Command(label, "024...", MethodKind.momo);
  }

  @Test
  void first_method_is_default_second_is_not() {
    PayoutMethodView first = service.add(A, momo("MTN"));
    PayoutMethodView second = service.add(A, momo("Voda"));
    assertTrue(first.isDefault());
    assertFalse(second.isDefault());
    assertEquals(2, audit.size());
  }

  @Test
  void set_default_moves_the_default_exactly_one() {
    PayoutMethodView first = service.add(A, momo("MTN"));
    PayoutMethodView second = service.add(A, momo("Voda"));
    service.setDefault(A, new PayoutMethodId(second.id()));

    long defaults = payouts.findMethods(A).stream().filter(m -> m.isDefault()).count();
    assertEquals(1, defaults, "exactly one default per account");
    assertTrue(
        payouts.findMethod(A, new PayoutMethodId(second.id())).get().isDefault());
    assertFalse(
        payouts.findMethod(A, new PayoutMethodId(first.id())).get().isDefault());
  }

  @Test
  void cannot_touch_another_creators_method() {
    PayoutMethodView aMethod = service.add(A, momo("MTN"));
    // B tries to remove A's method → 404 (ownership-scoped).
    assertThrows(
        PayoutMethodNotFoundException.class,
        () -> service.remove(B, new PayoutMethodId(aMethod.id())));
    assertThrows(
        PayoutMethodNotFoundException.class,
        () -> service.setDefault(B, new PayoutMethodId(aMethod.id())));
  }

  @Test
  void card_is_not_a_valid_payout_destination() {
    assertThrows(
        ValidationException.class,
        () -> service.add(A, new AddPayoutMethod.Command("Visa", "****4242", MethodKind.card)));
  }

  @Test
  void remove_deletes_and_audits() {
    PayoutMethodView m = service.add(A, momo("MTN"));
    service.remove(A, new PayoutMethodId(m.id()));
    assertTrue(payouts.findMethods(A).isEmpty());
    assertEquals("REMOVE_PAYOUT_METHOD", audit.all().get(audit.size() - 1).getAction());
  }
}
