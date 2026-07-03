package org.shakvilla.beatzmedia.payments.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.PaymentSettled;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.RevenueSplit;
import org.shakvilla.beatzmedia.payments.domain.TipReceived;
import org.shakvilla.beatzmedia.payments.domain.TipRef;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Posts a settled tip's 90/10 split and emits {@link TipReceived}, in its <strong>own</strong>
 * transaction ({@code REQUIRES_NEW}). Split out from {@link TipSettlementSubscriber} so the
 * exactly-once claim ({@link LedgerPostingService} → {@code ledger_posting} UNIQUE header) can fail
 * <em>and roll back this transaction in isolation</em> without poisoning the observer's own
 * transaction (finding F1). The subscriber invokes this via a CDI proxy (separate bean) so the
 * {@code REQUIRES_NEW} boundary actually takes effect, then treats a
 * {@link org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePostingException} as a benign
 * idempotent no-op.
 *
 * <p>The whole unit — claim + balanced entries + projection refresh + {@code TipReceived} — commits
 * atomically, or rolls back atomically on the duplicate claim, so a same-intent double-delivery yields
 * exactly one credit (INV-1/INV-6). {@code tipFeePct} comes from {@code PlatformSettings} (INV-4).
 */
@ApplicationScoped
public class TipLedgerPoster {

  private final LedgerPostingService posting;
  private final PlatformSettingsProvider settings;
  private final Clock clock;
  private final Event<TipReceived> tipReceivedEvent;

  @Inject
  public TipLedgerPoster(
      LedgerPostingService posting,
      PlatformSettingsProvider settings,
      Clock clock,
      Event<TipReceived> tipReceivedEvent) {
    this.posting = posting;
    this.settings = settings;
    this.clock = clock;
    this.tipReceivedEvent = tipReceivedEvent;
  }

  /**
   * Post the tip split for a settled tip intent. Throws {@code DuplicatePostingException} (rolling
   * back this new transaction) if a posting for the intent already exists — the caller swallows it.
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void postTip(PaymentSettled settled) {
    AccountId creator = TipRef.creatorOf(settled.orderRef());
    Provider provider = Provider.fromWire(settled.provider());
    Money gross = Money.ofMinor(settled.amountMinor(), Currency.valueOf(settled.currency()));
    int tipFeePct = settings.current().tipFeePct();

    RevenueSplit split =
        posting.postTipSplit(provider, creator, gross, tipFeePct, settled.intentId(), clock.now());

    tipReceivedEvent.fire(
        new TipReceived(
            settled.intentId(),
            settled.accountId(),
            creator.value(),
            gross.minor(),
            split.creatorShare().minor(),
            split.platformFee().minor(),
            gross.currency().name(),
            settled.settledAt()));
  }
}
