package org.shakvilla.beatzmedia.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.payments.application.port.in.RequestWithdrawal;
import org.shakvilla.beatzmedia.payments.application.port.in.WithdrawalView;
import org.shakvilla.beatzmedia.payments.application.service.RequestWithdrawalService;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.BelowMinPayoutException;
import org.shakvilla.beatzmedia.payments.domain.Direction;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyConflictException;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.InsufficientBalanceException;
import org.shakvilla.beatzmedia.payments.domain.KycRequiredException;
import org.shakvilla.beatzmedia.payments.domain.LedgerEntry;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;
import org.shakvilla.beatzmedia.payments.fakes.FakeKycProvider;
import org.shakvilla.beatzmedia.payments.fakes.FakeLedgerRepository;
import org.shakvilla.beatzmedia.payments.fakes.FakePayoutRepository;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.platform.fakes.FakePlatformSettingsProvider;

/**
 * Unit tests for {@link RequestWithdrawalService} — LLFR-PAYMENTS-03.2. Proves the money gates:
 * KYC (INV-8), config-driven floor (INV-4/8), balance-sufficiency net of reservations (INV-8),
 * a balanced exactly-once reservation posting (INV-6), idempotent replay, and audit (INV-10).
 */
@Tag("unit")
class RequestWithdrawalServiceTest {

  private static final AccountId CREATOR = new AccountId("creator-1");
  private static final PayoutMethodId METHOD = new PayoutMethodId("pm-1");

  private FakePayoutRepository payouts;
  private FakeLedgerRepository ledger;
  private FakeKycProvider kyc;
  private FakePlatformSettingsProvider settings;
  private FakeAuditWriter audit;
  private RequestWithdrawalService service;

  @BeforeEach
  void setUp() {
    payouts = new FakePayoutRepository();
    ledger = new FakeLedgerRepository();
    kyc = new FakeKycProvider();
    settings = new FakePlatformSettingsProvider();
    audit = new FakeAuditWriter();
    service =
        new RequestWithdrawalService(
            payouts, ledger, kyc, settings, FakeIds.sequential("wd"), FakeClock.fixed(), audit);
    // A momo method owned by the creator.
    payouts.saveMethod(
        PayoutMethod.create(
            METHOD.value(),
            CREATOR,
            "MTN MoMo",
            "024...9210",
            new org.shakvilla.beatzmedia.payments.domain.PayoutDestination.Momo(
                org.shakvilla.beatzmedia.payments.domain.Provider.mtn, "0244009210"),
            true,
            FakeClock.fixed().now()));
  }

  private static Money ghs(long minor) {
    return Money.ofMinor(minor, Currency.GHS);
  }

  private RequestWithdrawal.Command cmd(long minor) {
    return new RequestWithdrawal.Command(ghs(minor), METHOD);
  }

  @Test
  void rejects_when_not_kyc_verified_with_mapped_error_not_500() {
    ledger.seedCredit(CREATOR, 5000);
    // KYC not set → NONE (fail-closed).
    assertThrows(
        KycRequiredException.class,
        () -> service.request(CREATOR, cmd(2000), new IdempotencyKey("k1")));
    // No reservation posted (only the seeded credit exists), no audit.
    assertTrue(
        ledger.entries.stream().noneMatch(e -> "withdraw".equals(e.getRefType())),
        "no withdrawal reservation posted when KYC blocks (INV-1/INV-8)");
    assertEquals(0, audit.size());
  }

  @Test
  void rejects_below_configured_minimum_payout() {
    kyc.verify(CREATOR);
    ledger.seedCredit(CREATOR, 100_000);
    // payoutMinimumMinor default = 1000 (₵10). Ask for ₵5 (500).
    assertThrows(
        BelowMinPayoutException.class,
        () -> service.request(CREATOR, cmd(500), new IdempotencyKey("k1")));
  }

  @Test
  void rejects_when_amount_exceeds_available_balance() {
    kyc.verify(CREATOR);
    ledger.seedCredit(CREATOR, 2000); // ₵20 available
    assertThrows(
        InsufficientBalanceException.class,
        () -> service.request(CREATOR, cmd(3000), new IdempotencyKey("k1"))); // ask ₵30
  }

  @Test
  void reserves_balanced_and_reduces_available_and_audits() {
    kyc.verify(CREATOR);
    ledger.seedCredit(CREATOR, 5000); // ₵50 available
    WithdrawalView view = service.request(CREATOR, cmd(2000), new IdempotencyKey("k1")); // ₵20

    assertEquals("pending", view.status());
    // Reservation posted a balanced txn (the fake asserts Σdebits==Σcredits).
    long debit = 0;
    long credit = 0;
    for (LedgerEntry e : ledger.entries) {
      if ("withdraw".equals(e.getRefType())) {
        if (e.getDirection() == Direction.DEBIT) {
          debit += e.getAmount().minor();
        } else {
          credit += e.getAmount().minor();
        }
      }
    }
    assertEquals(2000, debit, "reserve debits creator_payable the gross");
    assertEquals(2000, credit, "reserve credits payout_clearing the gross (balanced, INV-6)");
    // Available is now reduced by the reservation.
    assertEquals(3000, ledger.balanceOf(CREATOR).availableMinor());
    // Audit recorded (INV-10).
    assertEquals(1, audit.size());
    assertEquals("REQUEST_WITHDRAWAL", audit.all().get(0).getAction());
    assertEquals(CREATOR.value(), audit.all().get(0).getActor());
  }

  @Test
  void two_sequential_withdrawals_cannot_overdraw_the_same_balance() {
    kyc.verify(CREATOR);
    ledger.seedCredit(CREATOR, 3000); // ₵30 available
    service.request(CREATOR, cmd(2000), new IdempotencyKey("k1")); // ₵20 ok → ₵10 left
    // Second ₵20 must fail: available is now ₵10, not the original ₵30.
    assertThrows(
        InsufficientBalanceException.class,
        () -> service.request(CREATOR, cmd(2000), new IdempotencyKey("k2")));
  }

  @Test
  void idempotent_replay_same_key_returns_same_withdrawal_no_second_reservation() {
    kyc.verify(CREATOR);
    ledger.seedCredit(CREATOR, 5000);
    WithdrawalView first = service.request(CREATOR, cmd(2000), new IdempotencyKey("k1"));
    int entriesAfterFirst = ledger.entries.size();

    WithdrawalView replay = service.request(CREATOR, cmd(2000), new IdempotencyKey("k1"));
    assertEquals(first.id(), replay.id(), "same key ⇒ same withdrawal");
    assertEquals(entriesAfterFirst, ledger.entries.size(), "no second reservation on replay");
    assertEquals(1, audit.size(), "one audit for one logical withdrawal");
  }

  @Test
  void same_key_different_amount_conflicts() {
    kyc.verify(CREATOR);
    ledger.seedCredit(CREATOR, 5000);
    service.request(CREATOR, cmd(2000), new IdempotencyKey("k1"));
    assertThrows(
        IdempotencyConflictException.class,
        () -> service.request(CREATOR, cmd(3000), new IdempotencyKey("k1")));
  }

  @Test
  void fee_is_config_driven_from_platform_settings() {
    kyc.verify(CREATOR);
    ledger.seedCredit(CREATOR, 100_000);
    // momo 1% of ₵100 (10000) = ₵1 (100), which is the ₵1 floor.
    WithdrawalView view = service.request(CREATOR, cmd(10_000), new IdempotencyKey("k1"));
    assertEquals(0, view.fee().amount().compareTo(new java.math.BigDecimal("1.00")));
  }
}
