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
