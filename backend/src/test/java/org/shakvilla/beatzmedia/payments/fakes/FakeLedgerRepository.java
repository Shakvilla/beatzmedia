package org.shakvilla.beatzmedia.payments.fakes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

  @Override
  public void clear(TxnId txn, Instant at) {
    // no-op for the fake
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
    long credit = 0;
    for (LedgerEntry e : entries) {
      LedgerAccount a = accountById(e.getAccountId());
      if (a != null
          && a.getKind() == LedgerAccountKind.CREATOR_PAYABLE
          && creator.value().equals(a.getOwnerAccountId().orElse(null))
          && e.getDirection() == org.shakvilla.beatzmedia.payments.domain.Direction.CREDIT) {
        credit += e.getAmount().minor();
      }
    }
    return new CreatorBalance(creator, credit, 0, credit);
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

  private LedgerAccount accountById(LedgerAccountId id) {
    return accounts.values().stream()
        .filter(a -> a.getId().equals(id))
        .findFirst()
        .orElse(null);
  }
}
