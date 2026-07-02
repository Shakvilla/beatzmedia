package org.shakvilla.beatzmedia.payments.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository;
import org.shakvilla.beatzmedia.payments.application.port.out.UnbalancedLedgerException;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.Direction;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccount;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccountId;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccountKind;
import org.shakvilla.beatzmedia.payments.domain.LedgerEntry;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for the double-entry ledger (LLFR-PAYMENTS-02.*). Testcontainers Postgres via
 * Quarkus Dev Services. Proves INV-6 at both layers:
 *
 * <ul>
 *   <li>the DB {@code assert_txn_balanced} deferred trigger REJECTS an unbalanced txn at commit;
 *   <li>{@link LedgerRepository#postBalanced} rejects an unbalanced posting in-app before any write;
 *   <li>a balanced posting persists and refreshes the {@code creator_balance} projection.
 * </ul>
 */
@QuarkusTest
@Tag("integration")
class LedgerRepositoryIT {

  @Inject LedgerRepository ledger;
  @Inject IdGenerator ids;
  @Inject AgroalDataSource dataSource;

  private static Money ghs(long minor) {
    return Money.ofMinor(minor, Currency.GHS);
  }

  // ---- DB trigger backstop (INV-6) --------------------------------------

  @Test
  void db_trigger_rejects_an_unbalanced_txn_at_commit() throws SQLException {
    // Insert a single unbalanced row directly (bypassing the app assertion) → the deferred trigger
    // must raise at COMMIT. A creator_payable account and a provider_clearing account first.
    String accId = "lg-acc-" + System.nanoTime();
    String txn = "lg-txn-" + System.nanoTime();
    try (Connection c = dataSource.getConnection()) {
      c.setAutoCommit(false);
      try (Statement s = c.createStatement()) {
        s.executeUpdate(
            "INSERT INTO ledger_account (id, kind, owner_account_id, created_at) VALUES ('"
                + accId + "', 'platform_revenue', NULL, now()) ON CONFLICT DO NOTHING");
        // Only a single CREDIT of 500 under this txn — deliberately unbalanced.
        s.executeUpdate(
            "INSERT INTO ledger_entry (id, txn_id, account_id, direction, amount_minor, ref_type, ref_id, posted_at) "
                + "VALUES ('" + accId + "-e', '" + txn + "', "
                + "(SELECT id FROM ledger_account WHERE kind='platform_revenue' LIMIT 1), "
                + "'CREDIT', 500, 'intent', 'r', now())");
      }
      // The deferred constraint trigger fires here.
      assertThrows(SQLException.class, c::commit,
          "an unbalanced txn_id must be rejected by assert_txn_balanced (INV-6)");
      c.rollback();
    }
  }

  // ---- in-app assertion -------------------------------------------------

  @Test
  @Transactional
  void postBalanced_rejects_unbalanced_entries_in_app() {
    LedgerAccount platform =
        ledger.accountFor(LedgerAccountKind.PLATFORM_REVENUE, null);
    TxnId txn = new TxnId(ids.newId());
    Instant now = Instant.now();
    // Single CREDIT — no matching debit.
    List<LedgerEntry> bad =
        List.of(
            LedgerEntry.post(
                ids.newId(), txn, platform.getId(), Direction.CREDIT, ghs(500),
                "intent", "r", now, now));
    assertThrows(UnbalancedLedgerException.class, () -> ledger.postBalanced(txn, bad));
  }

  // ---- happy path + projection ------------------------------------------

  @Test
  @Transactional
  void postBalanced_persists_a_balanced_sale_and_refreshes_creator_balance() {
    String creatorId = "lg-creator-" + System.nanoTime();
    LedgerAccountId providerClearing =
        ledger.accountFor(LedgerAccountKind.PROVIDER_CLEARING, "mtn").getId();
    LedgerAccountId creatorPayable =
        ledger.accountFor(LedgerAccountKind.CREATOR_PAYABLE, creatorId).getId();
    LedgerAccountId platformRevenue =
        ledger.accountFor(LedgerAccountKind.PLATFORM_REVENUE, null).getId();

    TxnId txn = new TxnId(ids.newId());
    Instant now = Instant.now();
    // ₵10 sale: DEBIT clearing 1000, CREDIT creator 700, CREDIT platform 300 (balanced).
    List<LedgerEntry> entries =
        List.of(
            LedgerEntry.post(ids.newId(), txn, providerClearing, Direction.DEBIT, ghs(1000), "intent", "i-1", now, now),
            LedgerEntry.post(ids.newId(), txn, creatorPayable, Direction.CREDIT, ghs(700), "intent", "i-1", now, now),
            LedgerEntry.post(ids.newId(), txn, platformRevenue, Direction.CREDIT, ghs(300), "intent", "i-1", now, now));

    ledger.postBalanced(txn, entries);

    var balance = ledger.balanceOf(new AccountId(creatorId));
    assertEquals(700, balance.availableMinor(), "creator available = 700 (cleared credit)");
    assertEquals(700, balance.lifetimeMinor());
    assertTrue(ledger.existsPostingFor("intent", "i-1"));
  }

  @Test
  @Transactional
  void accountFor_is_idempotent_get_or_create() {
    String creatorId = "lg-idem-" + System.nanoTime();
    LedgerAccountId first =
        ledger.accountFor(LedgerAccountKind.CREATOR_PAYABLE, creatorId).getId();
    LedgerAccountId second =
        ledger.accountFor(LedgerAccountKind.CREATOR_PAYABLE, creatorId).getId();
    assertEquals(first.value(), second.value(), "same creator resolves to the same payable account");
  }
}
