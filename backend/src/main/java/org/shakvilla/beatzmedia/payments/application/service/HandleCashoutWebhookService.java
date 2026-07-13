package org.shakvilla.beatzmedia.payments.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.payments.application.port.in.HandleCashoutWebhook;
import org.shakvilla.beatzmedia.payments.application.port.in.WebhookResult;
import org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePostingException;
import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway.ProviderStatus;
import org.shakvilla.beatzmedia.payments.application.port.out.PayoutRepository;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.PaymentEventType;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.ProviderException;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalId;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalRequest;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Confirms Redde cashout disbursements (WU-PAY-7, {@link HandleCashoutWebhook}) — the payout analog
 * of {@link HandleReddeReceiptService}, sharing its verify-by-pull-back trust (ADR-28). Serves BOTH
 * the cashout webhook and the {@code PayoutReconJob} fallback poll via {@link #confirmSent}.
 *
 * <p>Confirmation is the point where a SENT cashout becomes real money movement: only on a terminal
 * <em>pulled</em> {@code SETTLED} does it post the balanced disbursement ledger txn and mark the
 * withdrawal + payout txn {@code paid}; on {@code FAILED} it marks them failed and posts nothing (the
 * reservation reversal is a documented non-goal, ADR-28). Guarded four ways against double-confirm:
 * {@code findWithdrawalForUpdate} SKIP-LOCKED, the {@code isSent} state check, the {@code payout_event}
 * UNIQUE idempotency insert, and the exactly-once {@code postWithdrawalDisburse} ledger header.
 */
@ApplicationScoped
public class HandleCashoutWebhookService implements HandleCashoutWebhook {

  private static final Logger LOG = Logger.getLogger(HandleCashoutWebhookService.class);

  private final PaymentGateway gateway;
  private final PayoutRepository payouts;
  private final LedgerRepository ledger;
  private final ObjectMapper objectMapper;
  private final IdGenerator ids;
  private final Clock clock;
  private final AuditWriter auditWriter;

  @Inject
  public HandleCashoutWebhookService(
      PaymentGateway gateway,
      PayoutRepository payouts,
      LedgerRepository ledger,
      ObjectMapper objectMapper,
      IdGenerator ids,
      Clock clock,
      AuditWriter auditWriter) {
    this.gateway = gateway;
    this.payouts = payouts;
    this.ledger = ledger;
    this.objectMapper = objectMapper;
    this.ids = ids;
    this.clock = clock;
    this.auditWriter = auditWriter;
  }

  /**
   * Handle a raw cashout callback in one transaction (no self-invocation — this IS the proxy entry).
   * Resolves the in-flight withdrawal by {@code provider_ref == transactionid}; unknown → 202.
   */
  @Override
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public WebhookResult handle(byte[] rawBody) {
    String cashoutRef = parseTransactionId(rawBody);
    WithdrawalId wid = payouts.findSentWithdrawalIdByCashoutRef(cashoutRef).orElse(null);
    if (wid == null) {
      return WebhookResult.IGNORED_UNKNOWN;
    }
    confirmLocked(wid, cashoutRef);
    return WebhookResult.HANDLED;
  }

  /**
   * The ids of withdrawals still SENT whose cashout was sent before {@code sentBefore} — the recon
   * candidates. A short read tx; the per-withdrawal confirm runs in its own {@link #confirmSent}
   * REQUIRES_NEW boundary so one stuck withdrawal can't poison the sweep.
   */
  @Transactional
  public java.util.List<WithdrawalId> readSentCandidates(java.time.Instant sentBefore, int limit) {
    return payouts.findSentWithdrawalIds(sentBefore, limit);
  }

  /**
   * Reconciliation entry point (called per candidate by {@code PayoutReconJob}, across the bean
   * boundary so this {@code REQUIRES_NEW} boundary actually takes effect). Resolves the cashout ref
   * from the withdrawal's payout txn and confirms it the same way the webhook does.
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void confirmSent(WithdrawalId wid) {
    String cashoutRef = payouts.findCashoutRef(wid).orElse(null);
    if (cashoutRef == null) {
      return;
    }
    confirmLocked(wid, cashoutRef);
  }

  /**
   * The shared confirm core (runs inside the caller's REQUIRES_NEW tx). Claims the withdrawal FOR
   * UPDATE SKIP LOCKED, pulls the authenticated status, and on a terminal outcome idempotently posts
   * the ledger + marks paid, or marks failed. A no-op if the withdrawal is gone, already settled, or
   * the pull is inconclusive/unreachable (left SENT for the next tick).
   */
  private void confirmLocked(WithdrawalId wid, String cashoutRef) {
    WithdrawalRequest w = payouts.findWithdrawalForUpdate(wid).orElse(null);
    if (w == null || !w.getStatus().isSent()) {
      return; // gone, already confirmed, or locked by a concurrent confirm (SKIP LOCKED)
    }
    Provider rail = railFor(w);

    ProviderStatus pulled;
    try {
      pulled = gateway.queryStatus(rail, cashoutRef);
    } catch (ProviderException e) {
      // Rail unreachable — do not confirm off an unverified body; leave SENT for the recon poll.
      LOG.warnf(
          "Cashout pull-back failed for withdrawal %s (ref %s): %s — leaving to recon",
          wid, cashoutRef, e.getMessage());
      return;
    }

    PaymentEventType outcome = pulled.outcome();
    if (outcome == PaymentEventType.PENDING) {
      return; // still in flight
    }

    // Idempotency backstop: a duplicate cashout callback confirms at most once.
    if (!payouts.recordCashoutEventIfNew(
        ids.newId(),
        wid,
        "redde-cashout:" + cashoutRef,
        outcome,
        pulled.reason(),
        clock.now())) {
      return;
    }

    switch (outcome) {
      case SETTLED -> {
        try {
          TxnId disburseTxn =
              ledger.postWithdrawalDisburse(w.getAmount(), w.getId().value(), rail, clock.now());
          payouts.markPayoutTxnPaid(wid, disburseTxn, clock.now());
        } catch (DuplicatePostingException e) {
          // Disbursement already posted (exactly-once ledger header) — the mark below is idempotent.
        }
        w.markPaid();
        payouts.saveWithdrawal(w);
        audit(w, "CONFIRM_PAYOUT");
      }
      case FAILED -> {
        payouts.markPayoutTxnFailed(wid, clock.now());
        w.markFailed();
        payouts.saveWithdrawal(w);
        audit(w, "FAIL_PAYOUT");
      }
      case PENDING -> {
        // unreachable (guarded above)
      }
    }
  }

  private void audit(WithdrawalRequest w, String action) {
    auditWriter.append(
        new AuditEntry(
            ids.newId(),
            "system",
            action,
            "WithdrawalRequest",
            w.getId().value(),
            AuditType.FINANCE,
            null,
            clock.now()));
  }

  /** Rail used for the status pull-back, derived from the withdrawal's payout method. */
  private Provider railFor(WithdrawalRequest w) {
    PayoutMethod method = payouts.findMethod(w.getAccountId(), w.getMethodId()).orElse(null);
    if (method == null) {
      return Provider.mtn;
    }
    if (method.getNetwork() != null) {
      return method.getNetwork();
    }
    return method.getKind() == MethodKind.bank ? Provider.bank : Provider.mtn;
  }

  private String parseTransactionId(byte[] rawBody) {
    if (rawBody == null) {
      throw new ValidationException("empty Redde cashout callback body");
    }
    String transactionId;
    try {
      JsonNode node = objectMapper.readTree(rawBody);
      JsonNode txid = node.get("transactionid");
      transactionId = txid == null || txid.isNull() ? null : txid.asText();
    } catch (Exception e) {
      throw new ValidationException("malformed Redde cashout callback payload");
    }
    if (transactionId == null || transactionId.isBlank()) {
      throw new ValidationException("Redde cashout callback missing transactionid", "transactionid");
    }
    return transactionId;
  }
}
