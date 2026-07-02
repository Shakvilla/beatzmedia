package org.shakvilla.beatzmedia.payments.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Unit tests for the framework-free ledger domain: {@link Direction} signing, {@link LedgerEntry}
 * validation + signed contribution, {@link LedgerAccount} factories, {@link TxnId}/{@link TipRef}
 * value objects, {@link LedgerType} display mapping. Underpins INV-6 balance checks.
 */
@Tag("unit")
class LedgerDomainTest {

  private static final Instant T = Instant.parse("2026-07-02T00:00:00Z");

  private static Money ghs(long minor) {
    return Money.ofMinor(minor, Currency.GHS);
  }

  private static LedgerEntry entry(Direction dir, long minor) {
    return LedgerEntry.post(
        "e-" + dir + "-" + minor,
        new TxnId("t1"),
        new LedgerAccountId("acc-1"),
        dir,
        ghs(minor),
        "intent",
        "ref-1",
        T,
        T);
  }

  // ---- Direction --------------------------------------------------------

  @Test
  void direction_debit_is_positive_credit_is_negative() {
    assertEquals(700, Direction.DEBIT.signed(700));
    assertEquals(-300, Direction.CREDIT.signed(300));
  }

  // ---- LedgerEntry ------------------------------------------------------

  @Test
  void ledger_entry_signed_matches_direction() {
    assertEquals(1000, entry(Direction.DEBIT, 1000).signedMinor());
    assertEquals(-1000, entry(Direction.CREDIT, 1000).signedMinor());
  }

  @Test
  void balanced_transaction_signed_sum_is_zero() {
    long sum =
        entry(Direction.DEBIT, 1000).signedMinor()
            + entry(Direction.CREDIT, 700).signedMinor()
            + entry(Direction.CREDIT, 300).signedMinor();
    assertEquals(0, sum);
  }

  @Test
  void ledger_entry_rejects_non_positive_amount() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            LedgerEntry.post(
                "e", new TxnId("t"), new LedgerAccountId("a"), Direction.DEBIT, ghs(0),
                "intent", "r", T, T));
  }

  // ---- LedgerAccount ----------------------------------------------------

  @Test
  void owned_account_requires_owner_and_correct_kind() {
    LedgerAccount a =
        LedgerAccount.owned(
            new LedgerAccountId("a1"), LedgerAccountKind.CREATOR_PAYABLE, "creator-1");
    assertEquals("creator-1", a.getOwnerAccountId().orElseThrow());
    assertThrows(
        IllegalArgumentException.class,
        () -> LedgerAccount.owned(new LedgerAccountId("a2"), LedgerAccountKind.PLATFORM_REVENUE, "x"));
    assertThrows(
        IllegalArgumentException.class,
        () -> LedgerAccount.owned(new LedgerAccountId("a3"), LedgerAccountKind.CREATOR_PAYABLE, " "));
  }

  @Test
  void singleton_account_has_no_owner_and_correct_kind() {
    LedgerAccount a =
        LedgerAccount.singleton(new LedgerAccountId("a1"), LedgerAccountKind.PLATFORM_REVENUE);
    assertTrue(a.getOwnerAccountId().isEmpty());
    assertThrows(
        IllegalArgumentException.class,
        () -> LedgerAccount.singleton(new LedgerAccountId("a2"), LedgerAccountKind.CREATOR_PAYABLE));
  }

  @Test
  void ledger_account_kind_wire_roundtrip() {
    assertEquals("creator_payable", LedgerAccountKind.CREATOR_PAYABLE.wire());
    assertEquals(
        LedgerAccountKind.PROVIDER_CLEARING, LedgerAccountKind.fromWire("provider_clearing"));
  }

  // ---- TxnId ------------------------------------------------------------

  @Test
  void txnId_rejects_blank() {
    assertThrows(IllegalArgumentException.class, () -> new TxnId(" "));
    assertEquals("t1", new TxnId("t1").toString());
  }

  // ---- TipRef -----------------------------------------------------------

  @Test
  void tipRef_encodes_and_decodes_creator() {
    OrderRef ref = TipRef.forCreator(new AccountId("creator-9"));
    assertEquals("TIP:creator-9", ref.value());
    assertTrue(TipRef.isTip(ref.value()));
    assertEquals("creator-9", TipRef.creatorOf(ref.value()).value());
  }

  @Test
  void tipRef_recognises_non_tip_refs() {
    assertFalse(TipRef.isTip("BZ-2026-0001"));
    assertFalse(TipRef.isTip("TIP:")); // prefix only, no creator
    assertFalse(TipRef.isTip(null));
    assertThrows(IllegalArgumentException.class, () -> TipRef.creatorOf("BZ-2026-1"));
  }

  // ---- LedgerType -------------------------------------------------------

  @Test
  void ledgerType_display_and_parse() {
    assertEquals("Sale", LedgerType.SALE.display());
    assertEquals("Tip", LedgerType.TIP.display());
    assertEquals(LedgerType.FEE, LedgerType.fromDisplayOrNull("fee"));
    assertEquals(LedgerType.TIP, LedgerType.fromDisplayOrNull("Tip"));
    org.junit.jupiter.api.Assertions.assertNull(LedgerType.fromDisplayOrNull("nope"));
    org.junit.jupiter.api.Assertions.assertNull(LedgerType.fromDisplayOrNull(""));
  }
}
