package org.shakvilla.beatzmedia.payments.fakes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePostingException;
import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository;
import org.shakvilla.beatzmedia.payments.application.port.out.UnbalancedLedgerException;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.CreatorBalance;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccount;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccountId;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccountKind;
import org.shakvilla.beatzmedia.payments.domain.LedgerEntry;
import org.shakvilla.beatzmedia.payments.domain.LedgerType;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * In-memory {@link LedgerRepository} fake for unit tests. Enforces the same INV-6 balance assertion as
 * the real adapter so a posting-logic bug surfaces without a database. Records all entries so tests
 * can inspect the exact rows produced by the split posting.
 */
public class FakeLedgerRepository implements LedgerRepository {

  public final List<LedgerEntry> entries = new ArrayList<>();
  private final Map<String, LedgerAccount> accounts = new HashMap<>();
  private final Set<String> claims = new HashSet<>();
  private final AtomicInteger seq = new AtomicInteger();

  @Override
  public void postBalanced(TxnId txn, List<LedgerEntry> es) {
    long sum = 0;
    for (LedgerEntry e : es) {
      sum += e.signedMinor();
    }
    if (sum != 0) {
      throw new UnbalancedLedgerException("unbalanced: " + sum);
    }
    entries.addAll(es);
  }

  /** Records lockBalance calls so a concurrency test can assert the row lock was taken. */
  public final AtomicInteger lockBalanceCalls = new AtomicInteger();

  /** Monotonic txn id source for reserve/disburse postings. */
  private final AtomicInteger txnSeq = new AtomicInteger();

  @Override
  public void clear(TxnId txn, Instant at) {
    // no-op for the fake
  }

  @Override
  public TxnId postRefundReversal(String paymentIntentId, String refundId, Instant at) {
    // Mirror the real adapter: read the original settlement entries (ref_type intent/tip whose ref_id
    // is the intent id or "<intentId>:<creatorId>"), flip each direction, post under ("refund",
    // refundId). Balanced by construction because the originals summed to zero.
    List<LedgerEntry> originals =
        entries.stream()
            .filter(
                e ->
                    (e.getRefType().equals("intent") || e.getRefType().equals("tip"))
                        && (e.getRefId().equals(paymentIntentId)
                            || e.getRefId().startsWith(paymentIntentId + ":")))
            .toList();
    if (originals.isEmpty()) {
      throw new IllegalStateException(
          "no settlement ledger entries to reverse for intent " + paymentIntentId);
    }
    TxnId txn = new TxnId("refund-" + txnSeq.incrementAndGet());
    claimPosting(txn, "refund", refundId);
    List<LedgerEntry> reversal = new ArrayList<>(originals.size());
    for (LedgerEntry e : originals) {
      org.shakvilla.beatzmedia.payments.domain.Direction mirror =
          e.getDirection() == org.shakvilla.beatzmedia.payments.domain.Direction.DEBIT
              ? org.shakvilla.beatzmedia.payments.domain.Direction.CREDIT
              : org.shakvilla.beatzmedia.payments.domain.Direction.DEBIT;
      reversal.add(
          LedgerEntry.post(
              "e-" + seq.incrementAndGet(), txn, e.getAccountId(), mirror, e.getAmount(),
              "refund", refundId, at, at));
    }
    postBalanced(txn, reversal);
    return txn;
  }

  @Override
  public void lockBalance(AccountId creator) {
    lockBalanceCalls.incrementAndGet();
  }

  @Override
  public TxnId postWithdrawalReserve(
      AccountId creator, org.shakvilla.beatzmedia.platform.domain.Money amount,
      String withdrawalId, Instant at) {
    TxnId txn = new TxnId("reserve-" + txnSeq.incrementAndGet());
    claimPosting(txn, "withdraw", withdrawalId);
    LedgerAccountId creatorPayable =
        idOf(accountFor(LedgerAccountKind.CREATOR_PAYABLE, creator.value()));
    LedgerAccountId payoutClearing = idOf(accountFor(LedgerAccountKind.PAYOUT_CLEARING, null));
    postBalanced(
        txn,
        List.of(
            LedgerEntry.post(
                "e-" + seq.incrementAndGet(), txn, creatorPayable,
                org.shakvilla.beatzmedia.payments.domain.Direction.DEBIT,
                amount, "withdraw", withdrawalId, at, at),
            LedgerEntry.post(
                "e-" + seq.incrementAndGet(), txn, payoutClearing,
                org.shakvilla.beatzmedia.payments.domain.Direction.CREDIT,
                amount, "withdraw", withdrawalId, at, at)));
    return txn;
  }

  @Override
  public TxnId postWithdrawalDisburse(
      org.shakvilla.beatzmedia.platform.domain.Money amount, String withdrawalId,
      org.shakvilla.beatzmedia.payments.domain.Provider provider, Instant at) {
    TxnId txn = new TxnId("disburse-" + txnSeq.incrementAndGet());
    claimPosting(txn, "payout", withdrawalId);
    LedgerAccountId payoutClearing = idOf(accountFor(LedgerAccountKind.PAYOUT_CLEARING, null));
    LedgerAccountId providerClearing =
        idOf(accountFor(LedgerAccountKind.PROVIDER_CLEARING, provider.name()));
    postBalanced(
        txn,
        List.of(
            LedgerEntry.post(
                "e-" + seq.incrementAndGet(), txn, payoutClearing,
                org.shakvilla.beatzmedia.payments.domain.Direction.DEBIT,
                amount, "payout", withdrawalId, at, at),
            LedgerEntry.post(
                "e-" + seq.incrementAndGet(), txn, providerClearing,
                org.shakvilla.beatzmedia.payments.domain.Direction.CREDIT,
                amount, "payout", withdrawalId, at, at)));
    return txn;
  }

  /** Seed a creator's available balance by posting a cleared credit (test helper). */
  public void seedCredit(AccountId creator, long minor) {
    LedgerAccountId providerClearing =
        idOf(accountFor(LedgerAccountKind.PROVIDER_CLEARING, "test"));
    LedgerAccountId creatorPayable =
        idOf(accountFor(LedgerAccountKind.CREATOR_PAYABLE, creator.value()));
    TxnId txn = new TxnId("seed-" + txnSeq.incrementAndGet());
    Instant now = Instant.now();
    org.shakvilla.beatzmedia.platform.domain.Money m =
        org.shakvilla.beatzmedia.platform.domain.Money.ofMinor(
            minor, org.shakvilla.beatzmedia.platform.domain.Currency.GHS);
    postBalanced(
        txn,
        List.of(
            LedgerEntry.post(
                "e-" + seq.incrementAndGet(), txn, providerClearing,
                org.shakvilla.beatzmedia.payments.domain.Direction.DEBIT,
                m, "intent", "seed", now, now),
            LedgerEntry.post(
                "e-" + seq.incrementAndGet(), txn, creatorPayable,
                org.shakvilla.beatzmedia.payments.domain.Direction.CREDIT,
                m, "intent", "seed", now, now)));
  }

  @Override
  public LedgerAccount accountFor(LedgerAccountKind kind, String ownerAccountId) {
    String key = kind + ":" + ownerAccountId;
    return accounts.computeIfAbsent(
        key,
        k -> {
          LedgerAccountId id = new LedgerAccountId("acc-" + seq.incrementAndGet());
          if (ownerAccountId == null) {
            return LedgerAccount.singleton(id, kind);
          }
          return LedgerAccount.owned(id, kind, ownerAccountId);
        });
  }

  @Override
  public CreatorBalance balanceOf(AccountId creator) {
    // Available = Σ creator_payable CREDIT − Σ creator_payable DEBIT (a withdrawal reserve debits
    // creator_payable, so available already nets out prior reservations — mirrors the real projection).
    long available = 0;
    long lifetime = 0;
    for (LedgerEntry e : entries) {
      LedgerAccount a = accountById(e.getAccountId());
      if (a != null
          && a.getKind() == LedgerAccountKind.CREATOR_PAYABLE
          && creator.value().equals(a.getOwnerAccountId().orElse(null))) {
        if (e.getDirection() == org.shakvilla.beatzmedia.payments.domain.Direction.CREDIT) {
          available += e.getAmount().minor();
          lifetime += e.getAmount().minor();
        } else {
          available -= e.getAmount().minor();
        }
      }
    }
    return new CreatorBalance(creator, available, 0, lifetime);
  }

  @Override
  public List<CreatorLedgerRow> findForCreator(AccountId creator, int limit) {
    return List.of();
  }

  @Override
  public Page<LedgerEntryRow> find(LedgerType type, String q, PageRequest page) {
    return Page.empty(page.page(), page.size());
  }

  @Override
  public LedgerAccountId idOf(LedgerAccount account) {
    return account.getId();
  }

  @Override
  public boolean existsPostingFor(String refType, String refId) {
    return entries.stream()
        .anyMatch(e -> e.getRefType().equals(refType) && e.getRefId().equals(refId));
  }

  @Override
  public void claimPosting(TxnId txn, String refType, String refId) {
    // Mirror the DB PK (ref_type, ref_id): first claim wins, a repeat throws — so unit tests can
    // exercise the exactly-once no-op path deterministically (the real race is proven by the IT).
    if (!claims.add(refType + "|" + refId)) {
      throw new DuplicatePostingException(refType, refId, null);
    }
  }

  private LedgerAccount accountById(LedgerAccountId id) {
    return accounts.values().stream()
        .filter(a -> a.getId().equals(id))
        .findFirst()
        .orElse(null);
  }
}
