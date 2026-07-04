package org.shakvilla.beatzmedia.payments.application.service;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.payments.application.port.in.RequestWithdrawal;
import org.shakvilla.beatzmedia.payments.application.port.in.WithdrawalView;
import org.shakvilla.beatzmedia.payments.application.port.out.KycProvider;
import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository;
import org.shakvilla.beatzmedia.payments.application.port.out.PayoutRepository;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.BelowMinPayoutException;
import org.shakvilla.beatzmedia.payments.domain.CreatorBalance;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyConflictException;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.InsufficientBalanceException;
import org.shakvilla.beatzmedia.payments.domain.KycRequiredException;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodNotFoundException;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalRequest;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Application service for {@link RequestWithdrawal} (LLFR-PAYMENTS-03.2). The money-safety heart of
 * WU-PAY-4. Every gate below fails with a MAPPED domain error, never a 500.
 *
 * <p><strong>Order of guards (all before any money moves).</strong>
 *
 * <ol>
 *   <li><strong>Idempotency (INV).</strong> An advisory lock on the key serialises same-key requests;
 *       a prior withdrawal for the key is returned unchanged (one reservation per key).
 *   <li><strong>KYC gate (INV-8).</strong> {@link KycProvider#statusOf} must be {@code verified};
 *       otherwise {@link KycRequiredException} (403). Fail-closed: no record ⇒ not verified.
 *   <li><strong>Method ownership.</strong> The method must belong to the creator, else 404.
 *   <li><strong>Floor gate (INV-8).</strong> {@code amount ≥ PlatformSettings.payoutMinimumMinor}
 *       (₵10 default, config-driven — never hard-coded), else {@link BelowMinPayoutException} (422).
 *   <li><strong>Balance-backed (INV-8).</strong> Under a per-creator {@code creator_balance} row lock
 *       ({@link LedgerRepository#lockBalance}), the requested amount must be ≤ the CURRENT available
 *       balance. Because the reservation debits {@code creator_payable}, available already nets out
 *       every prior reservation; the row lock makes the read-then-reserve atomic, so two concurrent
 *       withdrawals cannot both pass this check and overdraw the balance ({@link
 *       InsufficientBalanceException}, 409).
 * </ol>
 *
 * <p>The reservation ({@link LedgerRepository#postWithdrawalReserve}) posts a balanced, cleared txn
 * (DEBIT creator_payable / CREDIT payout_clearing) keyed exactly-once on the withdrawal id, then the
 * withdrawal row is saved {@code pending}. An {@link AuditEntry} records the mutation (INV-10).
 */
@ApplicationScoped
public class RequestWithdrawalService implements RequestWithdrawal {

  private final PayoutRepository payouts;
  private final LedgerRepository ledger;
  private final KycProvider kyc;
  private final PlatformSettingsProvider settings;
  private final IdGenerator ids;
  private final Clock clock;
  private final AuditWriter auditWriter;

  @Inject
  public RequestWithdrawalService(
      PayoutRepository payouts,
      LedgerRepository ledger,
      KycProvider kyc,
      PlatformSettingsProvider settings,
      IdGenerator ids,
      Clock clock,
      AuditWriter auditWriter) {
    this.payouts = payouts;
    this.ledger = ledger;
    this.kyc = kyc;
    this.settings = settings;
    this.ids = ids;
    this.clock = clock;
    this.auditWriter = auditWriter;
  }

  @Override
  @Transactional
  public WithdrawalView request(AccountId creator, Command cmd, IdempotencyKey key) {
    if (cmd == null || cmd.amount() == null || cmd.methodId() == null) {
      throw new ValidationException("amount and methodId are required");
    }
    Money amount = cmd.amount();
    if (!amount.isPositive()) {
      throw new ValidationException("withdrawal amount must be positive", "amount");
    }

    // 1) Idempotency: serialise same-key requests, then replay a prior withdrawal unchanged.
    payouts.lockForIdempotencyKey(key);
    Optional<WithdrawalRequest> prior = payouts.findWithdrawalByIdempotencyKey(key);
    if (prior.isPresent()) {
      WithdrawalRequest existing = prior.get();
      if (existing.getAmount().minor() != amount.minor()
          || !existing.getMethodId().equals(cmd.methodId())) {
        throw new IdempotencyConflictException(
            "Idempotency-Key already used with a different withdrawal request");
      }
      return WithdrawalView.of(existing);
    }

    // 2) KYC gate (INV-8) — fail-closed, mapped 403.
    if (!kyc.statusOf(creator).isVerified()) {
      throw new KycRequiredException(creator);
    }

    // 3) Method ownership — mapped 404.
    PayoutMethod method =
        payouts
            .findMethod(creator, cmd.methodId())
            .orElseThrow(() -> new PayoutMethodNotFoundException(cmd.methodId()));

    // 4) Floor gate (INV-8) — config-driven minimum, mapped 422.
    PlatformSettings ps = settings.current();
    long minMinor = ps.payoutMinimumMinor();
    if (amount.minor() < minMinor) {
      throw new BelowMinPayoutException(amount.minor(), minMinor);
    }

    // 5) Balance-backed under a row lock (INV-8). The lock makes read-then-reserve atomic per creator,
    //    so two concurrent withdrawals cannot both see the same available and overdraw.
    ledger.lockBalance(creator);
    CreatorBalance balance = ledger.balanceOf(creator);
    if (amount.minor() > balance.availableMinor()) {
      throw new InsufficientBalanceException(amount.minor(), balance.availableMinor());
    }

    // Reserve: balanced, cleared txn keyed exactly-once on the withdrawal id. Reduces available NOW.
    String withdrawalId = ids.newId();
    TxnId reserveTxn =
        ledger.postWithdrawalReserve(creator, amount, withdrawalId, clock.now());

    long feeMinor = ps.withdrawalFeeMinor(method.getKind().name(), amount.minor());
    Money fee = Money.ofMinor(feeMinor, amount.currency() != null ? amount.currency() : Currency.GHS);

    WithdrawalRequest withdrawal =
        WithdrawalRequest.reserved(
            withdrawalId, creator, amount, fee, method.getId(), reserveTxn, key, clock.now());
    payouts.saveWithdrawal(withdrawal);

    // INV-10: a creator-initiated money mutation — record WHO acted.
    auditWriter.append(
        new AuditEntry(
            ids.newId(),
            creator.value(),
            "REQUEST_WITHDRAWAL",
            "WithdrawalRequest",
            withdrawalId,
            AuditType.FINANCE,
            null,
            clock.now()));

    return WithdrawalView.of(withdrawal);
  }
}
