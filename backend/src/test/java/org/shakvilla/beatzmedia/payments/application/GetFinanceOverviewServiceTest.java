package org.shakvilla.beatzmedia.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.port.in.FinanceOverviewView;
import org.shakvilla.beatzmedia.payments.application.port.in.ListPendingPayouts;
import org.shakvilla.beatzmedia.payments.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.payments.application.port.in.PendingPayoutView;
import org.shakvilla.beatzmedia.payments.application.service.GetFinanceOverviewService;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.Dispute;
import org.shakvilla.beatzmedia.payments.domain.DisputeId;
import org.shakvilla.beatzmedia.payments.domain.FinanceRange;
import org.shakvilla.beatzmedia.payments.fakes.FakeDisputeRepository;
import org.shakvilla.beatzmedia.payments.fakes.FakeLedgerRepository;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakePlatformSettingsProvider;

/**
 * Unit tests for {@link GetFinanceOverviewService} (LLFR-ADMIN-05.1). Proves the finance-overview
 * aggregation over the payments ledger/payouts/disputes with fakes: the GMV/fee window math, the
 * period-over-period delta, the payout KPIs, the open-dispute mapping, and the honest-empty fields
 * ({@code momoFloat}, {@code providerMix}). Money is bare decimal cedis on this surface.
 */
@Tag("unit")
class GetFinanceOverviewServiceTest {

  private static final AccountId ARTIST_A = new AccountId("artist-a");
  private static final AccountId ARTIST_B = new AccountId("artist-b");

  private FakeLedgerRepository ledger;
  private FakeDisputeRepository disputes;
  private FakePlatformSettingsProvider settings;
  private FakeClock clock;
  private List<PendingPayoutView> payable;
  private GetFinanceOverviewService service;

  @BeforeEach
  void setUp() {
    ledger = new FakeLedgerRepository();
    disputes = new FakeDisputeRepository();
    settings = new FakePlatformSettingsProvider(); // platformFeePct = 30 (defaults)
    // now = 2026-06-22T12:00Z; range 7d → window [06-15T12:00, 06-22T12:00), prior [06-08T12:00, 06-15T12:00)
    clock = FakeClock.at("2026-06-22T12:00:00Z");
    payable = List.of();
    ListPendingPayouts pendingPayouts = () -> payable;
    service =
        new GetFinanceOverviewService(ledger, disputes, pendingPayouts, settings, clock);
  }

  @Test
  void aggregatesGmvFeeAndDeltaOverTheTrailingWindow() {
    // Two sales inside the current 7d window: gross 1000+2000, fee 300+600.
    ledger.seedSale(ARTIST_A, 1000, 300, Instant.parse("2026-06-18T00:00:00Z"));
    ledger.seedSale(ARTIST_B, 2000, 600, Instant.parse("2026-06-20T00:00:00Z"));
    // One sale in the immediately-preceding window: prior GMV = 1500.
    ledger.seedSale(ARTIST_A, 1500, 450, Instant.parse("2026-06-10T00:00:00Z"));
    // One sale before the prior window entirely — must be ignored by both windows.
    ledger.seedSale(ARTIST_A, 9999, 3000, Instant.parse("2026-05-01T00:00:00Z"));

    FinanceOverviewView.Kpis k = service.overview(FinanceRange.SEVEN_DAYS).kpis();

    assertEquals(new BigDecimal("30.00"), k.gmvMtd()); // (1000+2000) minor = ₵30.00
    assertEquals(new BigDecimal("9.00"), k.platformFee()); // (300+600) minor = ₵9.00
    assertEquals(30, k.feeTakePct());
    // delta = round((3000 - 1500) / 1500 * 100) = 100%
    assertEquals(100, k.gmvDelta());
  }

  @Test
  void deltaIsZeroWhenPriorWindowHadNoSales() {
    ledger.seedSale(ARTIST_A, 1000, 300, Instant.parse("2026-06-18T00:00:00Z"));

    FinanceOverviewView.Kpis k = service.overview(FinanceRange.SEVEN_DAYS).kpis();

    assertEquals(new BigDecimal("10.00"), k.gmvMtd());
    assertEquals(0, k.gmvDelta());
  }

  @Test
  void sumsPayoutsDueCountsDistinctArtistsAndMapsBareAmounts() {
    payable =
        List.of(
            new PendingPayoutView("w1", "Artist A", MoneyView.ofMinor(50000), "MoMo · MTN", "ready"),
            new PendingPayoutView("w2", "Artist B", MoneyView.ofMinor(30000), "Bank · GCB", "ready"),
            new PendingPayoutView(
                "w3", "Artist A", MoneyView.ofMinor(20000), "MoMo · MTN", "kyc_pending"));

    FinanceOverviewView view = service.overview(FinanceRange.SEVEN_DAYS);

    assertEquals(new BigDecimal("1000.00"), view.kpis().payoutsDue()); // (50000+30000+20000) minor
    assertEquals(2, view.kpis().payoutsArtists()); // distinct A, B
    assertEquals(3, view.pendingPayouts().size());
    // Amounts are bare cedis (not the envelope) on the overview surface.
    assertEquals(new BigDecimal("500.00"), view.pendingPayouts().get(0).amount());
    assertEquals("kyc_pending", view.pendingPayouts().get(2).status());
  }

  @Test
  void mapsOpenDisputesToBareDecimalSummaries() {
    disputes.open.add(
        Dispute.open(
            new DisputeId("d1"),
            "ord-1",
            "pi-1",
            "Refund request",
            "@ama_b",
            "Album not delivered",
            Money.ofMinor(1899, Currency.GHS),
            false,
            Instant.parse("2026-06-19T00:00:00Z")));

    List<FinanceOverviewView.DisputeSummary> out =
        service.overview(FinanceRange.SEVEN_DAYS).disputes();

    assertEquals(1, out.size());
    FinanceOverviewView.DisputeSummary d = out.get(0);
    assertEquals("d1", d.id());
    assertEquals("Refund request", d.kind());
    assertEquals("@ama_b", d.subject());
    assertEquals(new BigDecimal("18.99"), d.amount());
    assertEquals("2026-06-19T00:00:00Z", d.opened());
  }

  @Test
  void momoFloatAndProviderMixAreHonestEmpty() {
    FinanceOverviewView view = service.overview(FinanceRange.SEVEN_DAYS);

    assertEquals(new BigDecimal("0.00"), view.kpis().momoFloat());
    assertTrue(view.providerMix().isEmpty(), "providerMix has no backing subsystem yet");
  }
}
