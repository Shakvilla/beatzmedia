package org.shakvilla.beatzmedia.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.payments.application.port.in.WebhookResult;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway.ProviderStatus;
import org.shakvilla.beatzmedia.payments.application.service.HandleCashoutWebhookService;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.PayoutDestination;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;
import org.shakvilla.beatzmedia.payments.domain.PayoutTxn;
import org.shakvilla.beatzmedia.payments.domain.PayoutTxnStatus;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalRequest;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalStatus;
import org.shakvilla.beatzmedia.payments.fakes.FakeLedgerRepository;
import org.shakvilla.beatzmedia.payments.fakes.FakePaymentGateway;
import org.shakvilla.beatzmedia.payments.fakes.FakePayoutRepository;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link HandleCashoutWebhookService} (WU-PAY-7) — verify-by-pull-back cashout
 * confirmation. The withdrawal's ledger posting + PAID transition happen only on a terminal PULLED
 * SETTLED, never the callback body; FAILED marks failed and posts nothing; an inconclusive/unreachable
 * pull leaves it SENT for the recon poll. Idempotent across duplicate deliveries (INV-6).
 */
@Tag("unit")
class HandleCashoutWebhookServiceTest {

  private static final AccountId CREATOR = new AccountId("creator-1");
  private static final PayoutMethodId METHOD = new PayoutMethodId("pm-1");
  private static final String CASHOUT_REF = "TX-1001";

  private FakePayoutRepository payouts;
  private FakeLedgerRepository ledger;
  private FakePaymentGateway gateway;
  private HandleCashoutWebhookService service;

  @BeforeEach
  void setUp() {
    payouts = new FakePayoutRepository();
    ledger = new FakeLedgerRepository();
    gateway = new FakePaymentGateway();
    service =
        new HandleCashoutWebhookService(
            gateway, payouts, ledger, new ObjectMapper(), FakeIds.sequential("ce"),
            FakeClock.fixed(), new FakeAuditWriter());
  }

  private static Money ghs(long minor) {
    return Money.ofMinor(minor, Currency.GHS);
  }

  /** Seed a withdrawal already SENT with an in-flight payout txn carrying {@link #CASHOUT_REF}. */
  private WithdrawalId seedSent(String id) {
    payouts.saveMethod(
        PayoutMethod.create(METHOD.value(), CREATOR, "MoMo", "024...",
            new PayoutDestination.Momo(Provider.mtn, "0244009210"), true, FakeClock.fixed().now()));
    WithdrawalRequest w =
        WithdrawalRequest.reconstitute(
            new WithdrawalId(id), CREATOR, ghs(5000), ghs(100), METHOD, WithdrawalStatus.SENT,
            new TxnId("reserve-" + id), new IdempotencyKey("idem-" + id), Instant.EPOCH);
    payouts.saveWithdrawal(w);
    payouts.savePayoutTxn(
        PayoutTxn.sent("pt-" + id, "b1", new WithdrawalId(id), CREATOR, ghs(5000), CASHOUT_REF,
            FakeClock.fixed().now()));
    return new WithdrawalId(id);
  }

  private static byte[] callback(String transactionId) {
    return ("{ \"transactionid\": \"" + transactionId + "\", \"status\": \"PAID\" }").getBytes();
  }

  private long payoutLedgerEntries() {
    return ledger.entries.stream().filter(e -> "payout".equals(e.getRefType())).count();
  }

  @Test
  void settled_pull_back_posts_ledger_and_marks_paid() {
    WithdrawalId wid = seedSent("w1");
    gateway.setStatus(CASHOUT_REF, ProviderStatus.settled());

    WebhookResult result = service.handle(callback(CASHOUT_REF));

    assertEquals(WebhookResult.HANDLED, result);
    assertEquals(WithdrawalStatus.PAID, payouts.findWithdrawal(wid).get().getStatus());
    assertEquals(PayoutTxnStatus.PAID, payouts.txnFor(wid).get().getStatus());
    assertTrue(payoutLedgerEntries() > 0, "the disbursement is posted on confirm");
  }

  @Test
  void failed_pull_back_marks_failed_and_posts_no_ledger() {
    WithdrawalId wid = seedSent("w2");
    gateway.setStatus(CASHOUT_REF, ProviderStatus.failed("insufficient float"));

    WebhookResult result = service.handle(callback(CASHOUT_REF));

    assertEquals(WebhookResult.HANDLED, result);
    assertEquals(WithdrawalStatus.FAILED, payouts.findWithdrawal(wid).get().getStatus());
    assertEquals(PayoutTxnStatus.FAILED, payouts.txnFor(wid).get().getStatus());
    assertEquals(0, payoutLedgerEntries(), "a failed cashout posts nothing (INV-6)");
  }

  @Test
  void pending_pull_back_leaves_it_sent() {
    WithdrawalId wid = seedSent("w3");
    // default gateway status is PENDING

    WebhookResult result = service.handle(callback(CASHOUT_REF));

    assertEquals(WebhookResult.HANDLED, result);
    assertEquals(WithdrawalStatus.SENT, payouts.findWithdrawal(wid).get().getStatus());
    assertEquals(0, payoutLedgerEntries());
  }

  @Test
  void unreachable_pull_back_leaves_it_sent_for_recon() {
    WithdrawalId wid = seedSent("w4");
    gateway.failQueryStatus();

    WebhookResult result = service.handle(callback(CASHOUT_REF));

    assertEquals(WebhookResult.HANDLED, result);
    assertEquals(WithdrawalStatus.SENT, payouts.findWithdrawal(wid).get().getStatus());
  }

  @Test
  void duplicate_delivery_confirms_exactly_once() {
    WithdrawalId wid = seedSent("w5");
    gateway.setStatus(CASHOUT_REF, ProviderStatus.settled());

    service.handle(callback(CASHOUT_REF));
    long entriesAfterFirst = payoutLedgerEntries();
    WebhookResult second = service.handle(callback(CASHOUT_REF));

    // After the first confirm the txn is no longer SENT, so the second delivery finds no in-flight
    // cashout and is a 202 no-op — the withdrawal is PAID exactly once, ledger posted once.
    assertEquals(WebhookResult.IGNORED_UNKNOWN, second);
    assertEquals(WithdrawalStatus.PAID, payouts.findWithdrawal(wid).get().getStatus());
    assertEquals(entriesAfterFirst, payoutLedgerEntries(), "no second disbursement posting");
  }

  @Test
  void unknown_transaction_id_is_ignored() {
    seedSent("w6");
    assertEquals(WebhookResult.IGNORED_UNKNOWN, service.handle(callback("NO-SUCH-REF")));
  }

  @Test
  void recon_reads_sent_candidates_and_confirms_each() {
    WithdrawalId wid = seedSent("w7");
    gateway.setStatus(CASHOUT_REF, ProviderStatus.settled());

    // The recon job path: read the SENT candidates, then confirm each in its own boundary.
    var candidates = service.readSentCandidates(FakeClock.fixed().now().plusSeconds(60), 10);
    assertTrue(candidates.contains(wid), "the SENT withdrawal is a recon candidate");
    service.confirmSent(wid);

    assertEquals(WithdrawalStatus.PAID, payouts.findWithdrawal(wid).get().getStatus());
    assertTrue(payoutLedgerEntries() > 0);
  }

  @Test
  void confirmSent_is_a_no_op_when_no_cashout_ref() {
    // A withdrawal with no payout txn (no cashout ref) is skipped cleanly.
    service.confirmSent(new WithdrawalId("no-txn"));
    assertEquals(0, payoutLedgerEntries());
  }

  @Test
  void malformed_body_is_rejected() {
    assertThrows(ValidationException.class, () -> service.handle("not json".getBytes()));
  }
}
