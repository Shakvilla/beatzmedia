package org.shakvilla.beatzmedia.notifications.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.domain.SplitInviteIssued;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test for the WU-CAT-9 collaborator split-invite email producer
 * ({@link org.shakvilla.beatzmedia.notifications.adapter.in.events.SplitInviteEmailObserver}).
 * Proves that catalog's {@link SplitInviteIssued}, delivered exactly as catalog fires it —
 * {@code AFTER_SUCCESS} of the release-submit transaction — results in an email to the raw
 * invited address (which may not belong to a BeatzClik account) carrying the accept URL.
 *
 * <p>Mirrors {@link TipNotificationObserverIT}: the event is fired via CDI {@link Event} inside a
 * real, separately-committed transaction ({@link QuarkusTransaction#requiringNew()}) so the
 * observer's {@code @Observes(during = TransactionPhase.AFTER_SUCCESS)} runs in the genuine
 * completing-transaction context production uses (no ambient transaction on the thread), and the
 * observer's own {@code REQUIRES_NEW} boundary is exercised for real.
 *
 * <p>Email delivery is asserted via Quarkus Mailer's {@link MockMailbox} (same convention as
 * {@link DispatchFlowIT}) — {@code %test.quarkus.mailer.mock=true} captures the send in-memory, so
 * no real SMTP/Mailpit connection is required to prove the flow.
 */
@QuarkusTest
@Tag("integration")
class SplitInviteEmailIT {

  @Inject Event<SplitInviteIssued> splitInviteIssuedEvent;
  @Inject MockMailbox mailbox;

  @AfterEach
  void resetCapture() {
    mailbox.clear();
  }

  @Test
  void splitInviteIssued_firedAfterSuccess_emailsRawCollaboratorAddress() {
    String acceptUrl = "https://beatzclik.local/collab/accept?token=it-" + System.nanoTime();
    SplitInviteIssued event =
        new SplitInviteIssued(
            "bob@x.com",
            acceptUrl,
            "Ama Serwaa",
            "Golden Hour",
            List.of(new SplitInviteIssued.TrackShare("Sunset Groove", "producer", 20)));

    // Fire the event inside a COMMITTED transaction, exactly as catalog's release-submit flow
    // does. The AFTER_SUCCESS observer runs synchronously during this transaction's commit.
    QuarkusTransaction.requiringNew().run(() -> splitInviteIssuedEvent.fire(event));

    List<io.quarkus.mailer.Mail> mails = mailbox.getMailsSentTo("bob@x.com");
    assertEquals(1, mails.size(), "AFTER_SUCCESS observer must send exactly one email to the raw collaborator address");
    assertTrue(
        mails.get(0).getText().contains(acceptUrl),
        "email body must contain the accept URL so the collaborator can review/accept the split");
  }
}
