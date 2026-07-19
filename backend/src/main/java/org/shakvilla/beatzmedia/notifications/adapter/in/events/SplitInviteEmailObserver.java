package org.shakvilla.beatzmedia.notifications.adapter.in.events;

import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.domain.SplitInviteIssued;
import org.shakvilla.beatzmedia.notifications.application.port.out.EmailMessage;
import org.shakvilla.beatzmedia.notifications.application.port.out.Mailer;

/**
 * WU-CAT-9: emails a collaborator their split invite when catalog fires {@link SplitInviteIssued}
 * on release submit. Sends to the raw invited address (a collaborator may not be a BeatzClik user),
 * so it bypasses the AccountId-gated in-app notification path and calls {@link Mailer} directly.
 * Reacts only to the event payload — no catalog table reads (hexagonal rule). AFTER_SUCCESS so the
 * email is sent only once the submit transaction has durably committed; REQUIRES_NEW because no tx
 * is active on the thread during AFTER_SUCCESS.
 */
@ApplicationScoped
public class SplitInviteEmailObserver {

  private final Mailer mailer;

  @Inject
  public SplitInviteEmailObserver(Mailer mailer) {
    this.mailer = mailer;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onSplitInviteIssued(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) SplitInviteIssued event) {
    String shares = event.trackShares().stream()
        .map(s -> "  • " + s.trackTitle() + " — " + s.role() + " — " + s.percent() + "%")
        .collect(Collectors.joining("\n"));
    String body = event.artistName() + " wants to split earnings with you on \""
        + event.releaseTitle() + "\":\n\n" + shares
        + "\n\nReview and accept or decline your split:\n" + event.acceptUrl()
        + "\n\nIf you don't recognise this, you can ignore this email.";
    // idempotencyKey: the accept URL is unique per issued invite (unique token), so it dedupes resends.
    mailer.send(new EmailMessage(
        event.email(), "You've been added to a release on BeatzClik", body, event.acceptUrl()));
  }
}
