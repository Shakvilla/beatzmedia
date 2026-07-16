package org.shakvilla.beatzmedia.notifications.adapter.in.events;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotifyCommand;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotifyUseCase;
import org.shakvilla.beatzmedia.notifications.domain.NotificationType;
import org.shakvilla.beatzmedia.payments.domain.TipReceived;

/**
 * CDI event observers that turn canonical domain events from OTHER modules into in-app
 * notifications, via {@link NotifyUseCase} (LLFR-NOTIF-02.1's in-app half; email/SMS dispatch is
 * WU-NOT-2). Notifications ADD §4.1 / §9.
 *
 * <p><strong>No cross-module table reads.</strong> This class reacts ONLY to the event payload
 * (ids + a minimal money snapshot) — it never queries a payments/podcasts/commerce table. The
 * event is the sole channel of information from the source module (hexagonal dependency rule).
 *
 * <p><strong>Timing — {@code AFTER_SUCCESS}.</strong> The observer fires only once the source
 * transaction (e.g. the tip settlement + ledger posting) has durably committed, so an in-app
 * notification is never created for money that did not actually move.
 *
 * <p><strong>Idempotency (INV-N4).</strong> {@link NotifyUseCase#notify} is keyed by a
 * {@code dedupeKey} built from the event's own natural key (here, the settled tip's
 * {@code intentId}) plus the recipient and notification type — a redelivered {@link TipReceived}
 * (e.g. two webhook deliveries settling the same intent) creates no duplicate row.
 *
 * <p><strong>WU-POD-2 tip-notification AC.</strong> This observer is the producer wired for the
 * podcasts tipping flow's deferred in-app notification: {@code TipReceived} is emitted by
 * payments' {@code TipSettlementSubscriber} on settlement of a podcast/creator tip (WU-PAY-3 /
 * WU-POD-2); {@code creatorAccountId} on the event is the tip recipient. Other canonical producers
 * listed in the ADD (sale, follower, payout, release, episode, system) are future producers —
 * their source events do not yet exist in the codebase, so their observers are not wired here.
 */
@ApplicationScoped
public class NotificationEventObservers {

  private final NotifyUseCase notifyUseCase;

  @Inject
  public NotificationEventObservers(NotifyUseCase notifyUseCase) {
    this.notifyUseCase = notifyUseCase;
  }

  /**
   * On a settled tip: notify the creator in-app ("you received a tip"). {@code type=tip},
   * recipient = {@code creatorAccountId}. Dedupe key = {@code tip:<intentId>:<creatorAccountId>}.
   *
   * <p><strong>{@code REQUIRES_NEW} (not merely the delegate's {@code REQUIRED}).</strong> As an
   * {@code AFTER_SUCCESS} observer this runs while the source transaction is already completing, so
   * no transaction is active on the thread. A plain {@code REQUIRED} boundary (as on
   * {@link NotifyUseCase#notify}) would <em>join</em> that completing context rather than start a
   * live one, and the first persistence op fails with {@code TransactionRequiredException}. Owning a
   * fresh {@code REQUIRES_NEW} transaction here suspends the stale context and gives {@code notify}
   * an active transaction — matching every other AFTER_SUCCESS persisting observer in the codebase
   * (e.g. analytics {@code TipReceivedObserver}, store {@code PurchaseConfirmedSubscriber}).
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onTipReceived(@Observes(during = TransactionPhase.AFTER_SUCCESS) TipReceived event) {
    String dedupeKey = "tip:" + event.intentId() + ":" + event.creatorAccountId();
    String body = "You received a tip of " + displayAmount(event.creatorShareMinor()) + " — nice!";

    notifyUseCase.notify(
        new NotifyCommand(
            dedupeKey,
            new AccountId(event.creatorAccountId()),
            NotificationType.tip,
            "You got a tip",
            body,
            "/studio/payouts"));
  }

  /** Renders {@code minor} pesewas as a display string, e.g. {@code "₵5.00"}. Only GHS today. */
  private static String displayAmount(long minor) {
    BigDecimal cedis = BigDecimal.valueOf(minor).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    return "₵" + cedis.toPlainString();
  }
}
