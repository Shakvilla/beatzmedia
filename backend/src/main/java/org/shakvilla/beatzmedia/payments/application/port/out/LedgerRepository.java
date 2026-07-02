package org.shakvilla.beatzmedia.payments.application.port.out;

import java.time.Instant;
import java.util.List;

import org.shakvilla.beatzmedia.payments.domain.CreatorBalance;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccount;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccountId;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccountKind;
import org.shakvilla.beatzmedia.payments.domain.LedgerEntry;
import org.shakvilla.beatzmedia.payments.domain.LedgerType;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Output port for the double-entry ledger (payments ADD §4.2). Reads/writes only the payments
 * module's ledger tables; no cross-module joins. The transaction boundary is the calling application
 * service ({@code @Transactional}).
 *
 * <p><strong>INV-6 (balance).</strong> {@link #postBalanced(TxnId, List)} asserts Σ DEBIT = Σ CREDIT
 * in-app before flush AND relies on the DB deferred constraint trigger ({@code assert_txn_balanced},
 * V703) as the durable backstop — an unbalanced posting can never commit. The {@code creator_balance}
 * projection is refreshed inside the same transaction on every posting so it never drifts.
 */
public interface LedgerRepository {

  /**
   * Post a balanced set of {@link LedgerEntry} rows under one {@link TxnId}. Throws {@link
   * UnbalancedLedgerException} if Σ DEBIT != Σ CREDIT (INV-6) before touching the DB. All rows share
   * the given {@code txn}. After persisting, the {@code creator_balance} projection for every credited
   * creator is refreshed in the same transaction.
   *
   * @throws UnbalancedLedgerException if the entries are not balanced
   */
  void postBalanced(TxnId txn, List<LedgerEntry> entries);

  /**
   * Mark every uncleared entry of a transaction as cleared at {@code at} (funds available). Used by
   * later payout WUs; a settled sale/tip is posted already-cleared. Refreshes affected projections.
   */
  void clear(TxnId txn, Instant at);

  /**
   * Get-or-create the {@link LedgerAccount} for the given kind + owner. Idempotent under concurrency
   * (unique partial index in V703, ON CONFLICT). {@code ownerAccountId} is the creator/provider for
   * owned kinds and {@code null} for the singletons ({@code PLATFORM_REVENUE}/{@code PAYOUT_CLEARING}).
   */
  LedgerAccount accountFor(LedgerAccountKind kind, String ownerAccountId);

  /** The derived balance for a creator (projection); zeroed if the creator has no ledger activity. */
  CreatorBalance balanceOf(AccountId creator);

  /**
   * Page over ledger entries for the admin finance ledger read (LLFR-PAYMENTS-02.3), newest first,
   * optionally filtered by business {@link LedgerType} and a free-text query over party/ref.
   */
  Page<LedgerEntryRow> find(LedgerType type, String q, PageRequest page);

  /** The id of a ledger account (helper for entry construction). */
  LedgerAccountId idOf(LedgerAccount account);

  /**
   * True if any ledger entry already exists for the given source reference ({@code refType}/{@code
   * refId}). A defensive idempotency guard so a re-delivered settlement (webhook replay + poll race)
   * never double-posts a split for the same intent — the settlement state machine already fires once,
   * this is belt-and-braces for INV-6.
   */
  boolean existsPostingFor(String refType, String refId);

  /**
   * A denormalised ledger row for the admin read, already resolved to its business {@link LedgerType},
   * counterparty {@code party}, source {@code ref}, and signed cedis-minor {@code amountMinor} (credit
   * to a creator is positive; a fee/payout/refund debit is negative in the admin view). The adapter
   * builds these from the joined entry/account rows so the application never re-derives them.
   */
  record LedgerEntryRow(
      String id,
      Instant postedAt,
      LedgerType type,
      String party,
      String ref,
      long amountMinor,
      String currency) {}
}
