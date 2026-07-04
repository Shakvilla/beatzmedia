package org.shakvilla.beatzmedia.notifications.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.adapter.in.events.NotificationEventObservers;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotifyCommand;
import org.shakvilla.beatzmedia.notifications.domain.NotificationType;
import org.shakvilla.beatzmedia.notifications.fakes.FakeNotifyUseCase;
import org.shakvilla.beatzmedia.payments.domain.TipReceived;

/**
 * Unit tests for {@link NotificationEventObservers} — proves the payments {@code TipReceived} event
 * is translated into the right {@link NotifyCommand} (recipient = creator, type = tip, a per-intent
 * dedupe key), closing the in-app half of the WU-POD-2 tip-notification AC. The observer reads ONLY
 * the event payload — no cross-module table access.
 */
@Tag("unit")
class NotificationEventObserversTest {

  private TipReceived settledTip() {
    return new TipReceived(
        "intent-42", "fan-acct", "creator-acct", 1000, 900, 100, "GHS", Instant.parse("2026-07-04T09:00:00Z"));
  }

  @Test
  void onTipReceived_notifiesTheCreator_withTipTypeAndPerIntentDedupeKey() {
    FakeNotifyUseCase notify = new FakeNotifyUseCase();
    new NotificationEventObservers(notify).onTipReceived(settledTip());

    assertEquals(1, notify.commands.size());
    NotifyCommand cmd = notify.commands.get(0);
    assertEquals(new AccountId("creator-acct"), cmd.recipient(), "the creator is the recipient");
    assertEquals(NotificationType.tip, cmd.type());
    assertEquals("tip:intent-42:creator-acct", cmd.dedupeKey(), "per-intent dedupe key for exactly-once");
    assertTrue(cmd.body().contains("9.00"), "body shows the creator's 90% share (₵9.00 of a ₵10 tip)");
  }
}
