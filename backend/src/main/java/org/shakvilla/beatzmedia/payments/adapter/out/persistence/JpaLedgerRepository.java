package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

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
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * JPA implementation of {@link LedgerRepository} (payments ADD §5.2). Reads/writes only the payments
 * module's ledger tables; no cross-module joins. Transaction boundary = the calling application
 * service.
 *
 * <p><strong>INV-6 (balance) — two layers.</strong> {@link #postBalanced} asserts Σ DEBIT = Σ CREDIT
 * in-app before any INSERT (throwing {@link UnbalancedLedgerException}), and the DB deferred
 * constraint trigger {@code assert_txn_balanced} (V703) re-checks at commit. An unbalanced posting
 * can never commit. After persisting, the {@code creator_balance} projection for every credited
 * creator is recomputed from the entries in the same transaction so it never drifts.
 */
@ApplicationScoped
public class JpaLedgerRepository implements LedgerRepository {

  private final EntityManager em;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public JpaLedgerRepository(EntityManager em, IdGenerator ids, Clock clock) {
    this.em = em;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  public void postBalanced(TxnId txn, List<LedgerEntry> entries) {
    if (entries == null || entries.isEmpty()) {
      throw new UnbalancedLedgerException("cannot post an empty ledger transaction");
    }
    // In-app balance assertion (INV-6) — fail loudly before touching the DB.
    long signedSum = 0L;
    for (LedgerEntry e : entries) {
      if (!e.getTxnId().equals(txn)) {
        throw new UnbalancedLedgerException(
            "entry txnId " + e.getTxnId() + " does not match posting txn " + txn);
      }
      signedSum += e.signedMinor();
    }
    if (signedSum != 0L) {
      throw new UnbalancedLedgerException(
          "unbalanced ledger txn " + txn + ": signed sum = " + signedSum + " (INV-6)");
    }

    Set<String> creditedCreators = new LinkedHashSet<>();
    for (LedgerEntry e : entries) {
      LedgerEntryEntity entity = new LedgerEntryEntity();
      entity.id = e.getId();
      entity.txnId = e.getTxnId().value();
      entity.accountId = e.getAccountId().value();
      entity.direction = e.getDirection().name();
      entity.amountMinor = e.getAmount().minor();
      entity.refType = e.getRefType();
      entity.refId = e.getRefId();
      entity.clearedAt = e.getClearedAt();
      entity.postedAt = e.getPostedAt();
      em.persist(entity);

      String creatorId = creditedCreatorOf(e.getAccountId().value());
      if (creatorId != null) {
        creditedCreators.add(creatorId);
      }
    }
    em.flush();

    for (String creatorId : creditedCreators) {
      refreshProjection(creatorId);
    }
  }

  @Override
  public void lockBalance(AccountId creator) {
    // Ensure a balance row exists to lock (INSERT ... ON CONFLICT DO NOTHING is atomic), then take a
    // row lock (SELECT ... FOR UPDATE) so two concurrent withdrawals for the SAME creator serialise:
    // the second blocks until the first's reservation commits and then reads the reduced available
    // balance (INV-8). This closes the read-then-reserve TOCTOU that would otherwise let both spend
    // the same funds or drive the balance negative.
    em.createNativeQuery(
            "INSERT INTO creator_balance (account_id, available_minor, pending_minor, lifetime_minor, updated_at) "
                + "VALUES (:id, 0, 0, 0, :now) ON CONFLICT (account_id) DO NOTHING")
        .setParameter("id", creator.value())
        .setParameter("now", clock.now())
        .executeUpdate();
    em.createNativeQuery("SELECT account_id FROM creator_balance WHERE account_id = :id FOR UPDATE")
        .setParameter("id", creator.value())
        .getSingleResult();
  }

  @Override
  public TxnId postWithdrawalReserve(
      AccountId creator, org.shakvilla.beatzmedia.platform.domain.Money amount,
      String withdrawalId, Instant at) {
    // Reserve funds: DEBIT creator_payable (reduces available NOW) = CREDIT payout_clearing (in-flight).
    // Balanced by construction (equal debit/credit of the same amount). Exactly-once on the withdrawal.
    TxnId txn = new TxnId(ids.newId());
    claimPosting(txn, "withdraw", withdrawalId);

    LedgerAccountId creatorPayable =
        idOf(accountFor(LedgerAccountKind.CREATOR_PAYABLE, creator.value()));
    LedgerAccountId payoutClearing = idOf(accountFor(LedgerAccountKind.PAYOUT_CLEARING, null));

    List<LedgerEntry> entries =
        List.of(
            LedgerEntry.post(
                ids.newId(), txn, creatorPayable,
                org.shakvilla.beatzmedia.payments.domain.Direction.DEBIT,
                amount, "withdraw", withdrawalId, at, at),
            LedgerEntry.post(
                ids.newId(), txn, payoutClearing,
                org.shakvilla.beatzmedia.payments.domain.Direction.CREDIT,
                amount, "withdraw", withdrawalId, at, at));
    postBalanced(txn, entries);
    return txn;
  }

  @Override
  public TxnId postWithdrawalDisburse(
      org.shakvilla.beatzmedia.platform.domain.Money amount, String withdrawalId,
      org.shakvilla.beatzmedia.payments.domain.Provider provider, Instant at) {
    // Disburse: DEBIT payout_clearing (funds leave clearing) = CREDIT provider_clearing (paid via rail).
    // Balanced; does NOT touch creator_payable (available was already reduced at reservation).
    // Exactly-once keyed on ("payout", withdrawalId): a retried run fails on the header PK and rolls
    // back rather than double-debiting (INV-6).
    TxnId txn = new TxnId(ids.newId());
    claimPosting(txn, "payout", withdrawalId);

    LedgerAccountId payoutClearing = idOf(accountFor(LedgerAccountKind.PAYOUT_CLEARING, null));
    LedgerAccountId providerClearing =
        idOf(accountFor(LedgerAccountKind.PROVIDER_CLEARING, provider.name()));

    List<LedgerEntry> entries =
        List.of(
            LedgerEntry.post(
                ids.newId(), txn, payoutClearing,
                org.shakvilla.beatzmedia.payments.domain.Direction.DEBIT,
                amount, "payout", withdrawalId, at, at),
            LedgerEntry.post(
                ids.newId(), txn, providerClearing,
                org.shakvilla.beatzmedia.payments.domain.Direction.CREDIT,
                amount, "payout", withdrawalId, at, at));
    postBalanced(txn, entries);
    return txn;
  }

  @Override
  public TxnId postRefundReversal(
      String paymentIntentId,
      String refundId,
      org.shakvilla.beatzmedia.platform.domain.Money refundAmount,
      Instant at) {
    // Read the ORIGINAL settlement entries for this intent (a sale posts ref_type='intent', a tip
    // 'tip'; a multi-creator order posts several 'intent' sub-postings whose ref_id is
    // "<intentId>:<creatorId>"). We match ref_id = intentId OR ref_id starting with intentId + ":"
    // so both the single-creator and the per-creator multi-posting cases are captured. Joined to the
    // account so we can classify each leg by kind (provider_clearing / creator_payable / platform).
    @SuppressWarnings("unchecked")
    List<Object[]> originals =
        em.createNativeQuery(
                "SELECT e.account_id, e.direction, e.amount_minor, a.kind "
                    + "FROM ledger_entry e JOIN ledger_account a ON a.id = e.account_id "
                    + "WHERE e.ref_type IN ('intent','tip') "
                    + "AND (e.ref_id = :ref OR e.ref_id LIKE :prefix)")
            .setParameter("ref", paymentIntentId)
            .setParameter("prefix", paymentIntentId + ":%")
            .getResultList();

    if (originals.isEmpty()) {
      throw new IllegalStateException(
          "no settlement ledger entries to reverse for intent " + paymentIntentId);
    }

    // Aggregate the original legs: the gross (provider_clearing DEBIT), the platform fee
    // (platform_revenue CREDIT), and the per-creator shares (creator_payable CREDIT, keyed by account
    // in a deterministic order so the rounding remainder is assigned stably).
    long originalGross = 0L;
    long originalPlatformFee = 0L;
    java.util.LinkedHashMap<String, Long> creatorShares = new java.util.LinkedHashMap<>();
    String providerClearingAccount = null;
    String platformRevenueAccount = null;
    for (Object[] row : originals) {
      String accountId = (String) row[0];
      String direction = (String) row[1];
      long amountMinor = ((Number) row[2]).longValue();
      String kind = (String) row[3];
      switch (kind) {
        case "provider_clearing" -> {
          if ("DEBIT".equals(direction)) {
            originalGross += amountMinor;
            providerClearingAccount = accountId;
          }
        }
        case "platform_revenue" -> {
          if ("CREDIT".equals(direction)) {
            originalPlatformFee += amountMinor;
            platformRevenueAccount = accountId;
          }
        }
        case "creator_payable" -> {
          if ("CREDIT".equals(direction)) {
            creatorShares.merge(accountId, amountMinor, Long::sum);
          }
        }
        default -> {
          // ignore any other leg kinds
        }
      }
    }

    long refund = refundAmount.minor();
    if (refund <= 0 || refund > originalGross) {
      throw new IllegalStateException(
          "refund amount " + refund + " out of range (0, gross=" + originalGross + "]");
    }

    // Proportional, rounding-safe reversal (mirrors RevenueSplit): the platform-fee reversal is the
    // half-up proportional share of the refund; the creators absorb the EXACT remainder. So the DEBIT
    // legs sum to exactly `refund` = the buyer CREDIT (Σ DEBIT = Σ CREDIT, INV-6). A FULL refund
    // (refund == originalGross) reverses exactly the original legs (feeReversal == originalPlatformFee,
    // each creatorReversal == its original share) — regression-safe.
    long platformReversal = proportional(refund, originalPlatformFee, originalGross);
    long creatorReversalTotal = refund - platformReversal; // exact remainder for the creator side

    TxnId txn = new TxnId(ids.newId());
    // Exactly-once claim BEFORE any entry: a re-delivered / concurrent refund fails on the
    // ledger_posting PK ("refund", refundId) and DuplicatePostingException rolls this tx back — so the
    // clawback lands EXACTLY ONCE (INV-9). The caller runs this on a REQUIRES_NEW boundary.
    claimPosting(txn, "refund", refundId);

    List<LedgerEntry> reversal = new java.util.ArrayList<>();
    org.shakvilla.beatzmedia.platform.domain.Currency ccy = refundAmount.currency();

    // Buyer/clearing CREDIT = the refunded amount (funds returned to the rail).
    reversal.add(
        entry(txn, providerClearingAccount,
            org.shakvilla.beatzmedia.payments.domain.Direction.CREDIT, refund, refundId, ccy, at));

    // Platform-revenue DEBIT = proportional fee reversal (may be 0 for a tiny partial refund).
    if (platformReversal > 0) {
      reversal.add(
          entry(txn, platformRevenueAccount,
              org.shakvilla.beatzmedia.payments.domain.Direction.DEBIT, platformReversal, refundId,
              ccy, at));
    }

    // Creator DEBIT(s) = the creator-side total, distributed per creator proportionally to their
    // original share, with the LAST creator absorbing the rounding remainder so the creator DEBITs sum
    // to exactly creatorReversalTotal (no pesewa lost/created; no creator over-clawed beyond its share).
    long originalCreatorTotal = 0L;
    for (long s : creatorShares.values()) {
      originalCreatorTotal += s;
    }
    long assigned = 0L;
    int idx = 0;
    int last = creatorShares.size() - 1;
    for (java.util.Map.Entry<String, Long> e : creatorShares.entrySet()) {
      long portion;
      if (idx == last) {
        portion = creatorReversalTotal - assigned; // exact remainder to the last creator
      } else if (originalCreatorTotal > 0) {
        portion = proportional(creatorReversalTotal, e.getValue(), originalCreatorTotal);
        assigned += portion;
      } else {
        portion = 0L;
      }
      if (portion > 0) {
        reversal.add(
            entry(txn, e.getKey(),
                org.shakvilla.beatzmedia.payments.domain.Direction.DEBIT, portion, refundId, ccy, at));
      }
      idx++;
    }

    postBalanced(txn, reversal);
    return txn;
  }

  /** Build a refund-reversal ledger entry (already-cleared) against an account. */
  private LedgerEntry entry(
      TxnId txn,
      String accountId,
      org.shakvilla.beatzmedia.payments.domain.Direction direction,
      long amountMinor,
      String refundId,
      org.shakvilla.beatzmedia.platform.domain.Currency ccy,
      Instant at) {
    return LedgerEntry.post(
        ids.newId(),
        txn,
        new LedgerAccountId(accountId),
        direction,
        org.shakvilla.beatzmedia.platform.domain.Money.ofMinor(amountMinor, ccy),
        "refund",
        refundId,
        at,
        at);
  }

  /**
   * Half-up proportional share: {@code round(amount · numerator / denominator)} in exact integer
   * arithmetic (INV-11, no floating point). {@code denominator > 0} required by the caller.
   */
  private static long proportional(long amount, long numerator, long denominator) {
    if (denominator <= 0 || numerator <= 0) {
      return 0L;
    }
    return java.math.BigDecimal.valueOf(amount)
        .multiply(java.math.BigDecimal.valueOf(numerator))
        .divide(java.math.BigDecimal.valueOf(denominator), 0, java.math.RoundingMode.HALF_UP)
        .longValueExact();
  }

  @Override
  public void clear(TxnId txn, Instant at) {
    em.createNativeQuery(
            "UPDATE ledger_entry SET cleared_at = :at WHERE txn_id = :txn AND cleared_at IS NULL")
        .setParameter("at", at)
        .setParameter("txn", txn.value())
        .executeUpdate();
    // Refresh every creator touched by this txn.
    @SuppressWarnings("unchecked")
    List<String> creators =
        em.createNativeQuery(
                "SELECT DISTINCT a.owner_account_id FROM ledger_entry e "
                    + "JOIN ledger_account a ON a.id = e.account_id "
                    + "WHERE e.txn_id = :txn AND a.kind = 'creator_payable'")
            .setParameter("txn", txn.value())
            .getResultList();
    for (String creatorId : creators) {
      if (creatorId != null) {
        refreshProjection(creatorId);
      }
    }
  }

  @Override
  public LedgerAccount accountFor(LedgerAccountKind kind, String ownerAccountId) {
    // Atomic get-or-create keyed on the V703 partial unique indexes. ON CONFLICT DO NOTHING then
    // re-select, so two concurrent callers converge on one account row.
    String existing = selectAccountId(kind, ownerAccountId);
    if (existing != null) {
      return reconstitute(existing, kind, ownerAccountId);
    }
    String id = ids.newId();
    em.createNativeQuery(
            "INSERT INTO ledger_account (id, kind, owner_account_id, created_at) "
                + "VALUES (:id, :kind, :owner, :now) ON CONFLICT DO NOTHING")
        .setParameter("id", id)
        .setParameter("kind", kind.wire())
        .setParameter("owner", ownerAccountId)
        .setParameter("now", clock.now())
        .executeUpdate();
    String resolved = selectAccountId(kind, ownerAccountId);
    return reconstitute(resolved != null ? resolved : id, kind, ownerAccountId);
  }

  private String selectAccountId(LedgerAccountKind kind, String ownerAccountId) {
    String jpql =
        ownerAccountId == null
            ? "SELECT a.id FROM LedgerAccountEntity a WHERE a.kind = :kind AND a.ownerAccountId IS NULL"
            : "SELECT a.id FROM LedgerAccountEntity a WHERE a.kind = :kind AND a.ownerAccountId = :owner";
    var query = em.createQuery(jpql, String.class).setParameter("kind", kind.wire());
    if (ownerAccountId != null) {
      query.setParameter("owner", ownerAccountId);
    }
    return query.setMaxResults(1).getResultList().stream().findFirst().orElse(null);
  }

  private static LedgerAccount reconstitute(
      String id, LedgerAccountKind kind, String ownerAccountId) {
    return LedgerAccount.reconstitute(new LedgerAccountId(id), kind, ownerAccountId);
  }

  @Override
  public LedgerAccountId idOf(LedgerAccount account) {
    return account.getId();
  }

  @Override
  public boolean existsPostingFor(String refType, String refId) {
    Long count =
        em.createQuery(
                "SELECT COUNT(e) FROM LedgerEntryEntity e WHERE e.refType = :t AND e.refId = :r",
                Long.class)
            .setParameter("t", refType)
            .setParameter("r", refId)
            .getSingleResult();
    return count != null && count > 0;
  }

  @Override
  public void claimPosting(TxnId txn, String refType, String refId) {
    // Exactly-once claim: insert the ledger_posting header (PK (ref_type, ref_id)) and FLUSH so a
    // duplicate surfaces NOW as a constraint violation, not silently at commit. The first concurrent
    // poster inserts; the second fails on the PK and we translate it to DuplicatePostingException so
    // the caller's REQUIRES_NEW txn rolls back (finding F1). ON CONFLICT is deliberately NOT used —
    // we WANT the violation so the loser aborts rather than proceeding to double-post.
    try {
      em.createNativeQuery(
              "INSERT INTO ledger_posting (ref_type, ref_id, txn_id, posted_at) "
                  + "VALUES (:rt, :ri, :txn, :now)")
          .setParameter("rt", refType)
          .setParameter("ri", refId)
          .setParameter("txn", txn.value())
          .setParameter("now", clock.now())
          .executeUpdate();
      em.flush();
    } catch (jakarta.persistence.PersistenceException e) {
      if (isUniqueViolation(e)) {
        throw new org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePostingException(
            refType, refId, e);
      }
      throw e;
    }
  }

  /** True if the throwable chain is a Postgres unique/PK violation (SQLState 23505). */
  private static boolean isUniqueViolation(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) {
        return true;
      }
      if (t instanceof org.hibernate.exception.ConstraintViolationException) {
        return true;
      }
    }
    return false;
  }

  @Override
  public CreatorBalance balanceOf(AccountId creator) {
    CreatorBalanceEntity row = em.find(CreatorBalanceEntity.class, creator.value());
    if (row == null) {
      return CreatorBalance.zero(creator);
    }
    return new CreatorBalance(
        creator, row.availableMinor, row.pendingMinor, row.lifetimeMinor);
  }

  @Override
  public List<CreatorLedgerRow> findForCreator(AccountId creator, int limit) {
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT e.id, e.posted_at, e.ref_type, e.amount_minor, e.cleared_at "
                    + "FROM ledger_entry e "
                    + "JOIN ledger_account a ON a.id = e.account_id "
                    + "WHERE a.kind = 'creator_payable' AND a.owner_account_id = :owner "
                    + "AND e.direction = 'CREDIT' "
                    + "ORDER BY e.posted_at DESC")
            .setParameter("owner", creator.value())
            .setMaxResults(limit)
            .getResultList();

    return rows.stream()
        .map(
            r -> {
              String id = (String) r[0];
              Instant postedAt = toInstant(r[1]);
              String refType = (String) r[2];
              long net = ((Number) r[3]).longValue();
              boolean cleared = r[4] != null;
              LedgerType type = "tip".equals(refType) ? LedgerType.TIP : LedgerType.SALE;
              // Gross is not stored per creator-credit row; the payouts screen shows the creator's net
              // as both figures for now (gross reconstruction awaits the order join in WU-COM-2).
              return new CreatorLedgerRow(id, postedAt, type, net, net, cleared, "GHS");
            })
        .toList();
  }

  @Override
  public Page<LedgerEntryRow> find(LedgerType type, String q, PageRequest page) {
    StringBuilder where = new StringBuilder(" WHERE 1=1 ");
    if (type != null) {
      where.append(" AND e.ref_type = :refType AND a.kind = :accKind ");
    }
    if (q != null && !q.isBlank()) {
      where.append(" AND (e.ref_id ILIKE :q OR a.owner_account_id ILIKE :q) ");
    }

    String base =
        "FROM ledger_entry e JOIN ledger_account a ON a.id = e.account_id" + where;

    var countQuery = em.createNativeQuery("SELECT COUNT(*) " + base);
    var pageQuery =
        em.createNativeQuery(
            "SELECT e.id, e.posted_at, e.ref_type, e.direction, e.amount_minor, "
                + "a.kind, a.owner_account_id, e.ref_id "
                + base
                + " ORDER BY e.posted_at DESC");

    if (type != null) {
      String[] kindRef = refTypeAndKindFor(type);
      countQuery.setParameter("refType", kindRef[0]).setParameter("accKind", kindRef[1]);
      pageQuery.setParameter("refType", kindRef[0]).setParameter("accKind", kindRef[1]);
    }
    if (q != null && !q.isBlank()) {
      String like = "%" + q.trim() + "%";
      countQuery.setParameter("q", like);
      pageQuery.setParameter("q", like);
    }

    long total = ((Number) countQuery.getSingleResult()).longValue();

    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        pageQuery
            .setFirstResult(page.offset())
            .setMaxResults(page.size())
            .getResultList();

    List<LedgerEntryRow> items =
        rows.stream()
            .map(
                r -> {
                  String id = (String) r[0];
                  Instant postedAt = toInstant(r[1]);
                  String refType = (String) r[2];
                  String direction = (String) r[3];
                  long amount = ((Number) r[4]).longValue();
                  String accKind = (String) r[5];
                  String owner = (String) r[6];
                  String refId = (String) r[7];
                  LedgerType ledgerType = classify(refType, accKind);
                  // Admin view sign convention: creator credit positive; a fee/platform credit shows
                  // as positive revenue; a provider-clearing debit is the gross inflow (positive).
                  long signed = amount;
                  String party = partyLabel(ledgerType, accKind, owner);
                  return new LedgerEntryRow(id, postedAt, ledgerType, party, refId, signed, "GHS");
                })
            .toList();

    return new Page<>(items, page.page(), page.size(), total);
  }

  // ---- helpers ----------------------------------------------------------

  /**
   * The owner-account id of a creator_payable account, or {@code null} if the given account is not a
   * creator_payable account (so the projection is only refreshed for real creator credits).
   */
  private String creditedCreatorOf(String accountId) {
    @SuppressWarnings("unchecked")
    List<Object> owners =
        em.createNativeQuery(
                "SELECT owner_account_id FROM ledger_account "
                    + "WHERE id = :id AND kind = 'creator_payable'")
            .setParameter("id", accountId)
            .getResultList();
    return owners.isEmpty() ? null : (String) owners.get(0);
  }

  /**
   * Recompute the creator's balance projection from the ledger entries (INV-6/INV-8). Runs in the
   * same transaction as the posting.
   */
  private void refreshProjection(String creatorId) {
    Object[] agg =
        (Object[])
            em.createNativeQuery(
                    "SELECT "
                        + " COALESCE(SUM(CASE WHEN e.direction='CREDIT' AND e.cleared_at IS NOT NULL THEN e.amount_minor "
                        + "                   WHEN e.direction='DEBIT'  AND e.cleared_at IS NOT NULL THEN -e.amount_minor ELSE 0 END),0) AS available, "
                        + " COALESCE(SUM(CASE WHEN e.direction='CREDIT' AND e.cleared_at IS NULL THEN e.amount_minor ELSE 0 END),0) AS pending, "
                        + " COALESCE(SUM(CASE WHEN e.direction='CREDIT' THEN e.amount_minor ELSE 0 END),0) AS lifetime "
                        + "FROM ledger_entry e JOIN ledger_account a ON a.id = e.account_id "
                        + "WHERE a.kind = 'creator_payable' AND a.owner_account_id = :owner")
                .setParameter("owner", creatorId)
                .getSingleResult();

    long available = ((Number) agg[0]).longValue();
    long pending = ((Number) agg[1]).longValue();
    long lifetime = ((Number) agg[2]).longValue();

    em.createNativeQuery(
            "INSERT INTO creator_balance (account_id, available_minor, pending_minor, lifetime_minor, updated_at) "
                + "VALUES (:id, :av, :pd, :lf, :now) "
                + "ON CONFLICT (account_id) DO UPDATE SET "
                + " available_minor = EXCLUDED.available_minor, "
                + " pending_minor = EXCLUDED.pending_minor, "
                + " lifetime_minor = EXCLUDED.lifetime_minor, "
                + " updated_at = EXCLUDED.updated_at")
        .setParameter("id", creatorId)
        .setParameter("av", available)
        .setParameter("pd", pending)
        .setParameter("lf", lifetime)
        .setParameter("now", clock.now())
        .executeUpdate();
  }

  private static LedgerType classify(String refType, String accKind) {
    if ("platform_revenue".equals(accKind)) {
      return LedgerType.FEE;
    }
    if ("tip".equals(refType)) {
      return LedgerType.TIP;
    }
    if ("refund".equals(refType)) {
      return LedgerType.REFUND;
    }
    if ("payout".equals(refType)) {
      return LedgerType.PAYOUT;
    }
    return LedgerType.SALE;
  }

  /** Map a business ledger type to the (ref_type, account_kind) pair used for filtering. */
  private static String[] refTypeAndKindFor(LedgerType type) {
    return switch (type) {
      case FEE -> new String[] {"intent", "platform_revenue"};
      case TIP -> new String[] {"tip", "creator_payable"};
      case SALE -> new String[] {"intent", "creator_payable"};
      case REFUND -> new String[] {"refund", "creator_payable"};
      case PAYOUT -> new String[] {"payout", "payout_clearing"};
      case ROYALTY -> new String[] {"royalty", "creator_payable"};
    };
  }

  private static String partyLabel(LedgerType type, String accKind, String owner) {
    if ("platform_revenue".equals(accKind)) {
      return "Platform fee";
    }
    if (owner != null && !owner.isBlank()) {
      return owner;
    }
    return type.display();
  }

  private static Instant toInstant(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Instant instant) {
      return instant;
    }
    if (value instanceof java.sql.Timestamp ts) {
      return ts.toInstant();
    }
    if (value instanceof java.time.OffsetDateTime odt) {
      return odt.toInstant();
    }
    return Instant.parse(value.toString());
  }
}
