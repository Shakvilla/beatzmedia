package org.shakvilla.beatzmedia.payments.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.payments.application.port.in.GetPayouts;
import org.shakvilla.beatzmedia.payments.application.port.in.PayoutsView;
import org.shakvilla.beatzmedia.payments.application.port.in.PayoutsView.BySource;
import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository;
import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository.CreatorLedgerRow;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.CreatorBalance;
import org.shakvilla.beatzmedia.payments.domain.LedgerType;

/**
 * Read service for {@link GetPayouts} (LLFR-PAYMENTS-02.2). Projects the creator's ledger balance +
 * recent transactions onto the frontend {@code Payouts} shape. Money is converted to decimal cedis at
 * this read boundary (INV-11).
 *
 * <p><strong>Scope caveats (documented, not stubs).</strong>
 *
 * <ul>
 *   <li>{@code methods} is empty — payout methods are WU-PAY-4 (this read never invents fake methods).
 *   <li>{@code bySource.royalties} is always {@code 0} — OQ-4 resolved to no royalty model (ADR-20).
 *   <li>{@code earnings} chart series is empty until historical aggregation lands; the numeric KPIs
 *       are live from the ledger.
 * </ul>
 */
@ApplicationScoped
public class GetPayoutsService implements GetPayouts {

  private static final int TXN_LIMIT = 50;
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("MMM dd").withZone(ZoneOffset.UTC);

  private final LedgerRepository ledger;

  @Inject
  public GetPayoutsService(LedgerRepository ledger) {
    this.ledger = ledger;
  }

  @Override
  @Transactional
  public PayoutsView get(AccountId creator) {
    CreatorBalance balance = ledger.balanceOf(creator);
    List<CreatorLedgerRow> rows = ledger.findForCreator(creator, TXN_LIMIT);

    long salesNet = 0L;
    long tipsNet = 0L;
    List<PayoutsView.PayoutTxnView> txns = new java.util.ArrayList<>(rows.size());
    for (CreatorLedgerRow row : rows) {
      if (row.type() == LedgerType.SALE) {
        salesNet += row.netMinor();
      } else if (row.type() == LedgerType.TIP) {
        tipsNet += row.netMinor();
      }
      txns.add(
          new PayoutsView.PayoutTxnView(
              row.id(),
              DATE.format(row.postedAt()),
              sourceLabel(row.type()),
              row.type().display(),
              cedis(row.grossMinor()),
              cedis(row.netMinor()),
              row.cleared() ? "cleared" : "pending"));
    }

    return new PayoutsView(
        cedis(balance.availableMinor()),
        cedis(balance.pendingMinor()),
        // thisMonth: not yet aggregated by period at this WU — surface net available as the headline.
        cedis(salesNet + tipsNet),
        0,
        cedis(balance.lifetimeMinor()),
        "",
        List.of(),
        // OQ-4: royalties always 0 (no royalty model — ADR-20).
        new BySource(cedis(salesNet), BigDecimal.ZERO.setScale(2), cedis(tipsNet)),
        List.of(), // payout methods: WU-PAY-4
        txns);
  }

  private static String sourceLabel(LedgerType type) {
    return switch (type) {
      case SALE -> "Track sale";
      case TIP -> "Tip";
      default -> type.display();
    };
  }

  private static BigDecimal cedis(long minor) {
    return BigDecimal.valueOf(minor).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }
}
