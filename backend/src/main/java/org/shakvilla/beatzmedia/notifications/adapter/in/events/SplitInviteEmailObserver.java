package org.shakvilla.beatzmedia.notifications.adapter.in.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.domain.SplitInviteIssued;
import org.shakvilla.beatzmedia.notifications.application.port.out.EmailMessage;
import org.shakvilla.beatzmedia.notifications.application.port.out.Mailer;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;

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
  private final Template htmlTemplate;
  private final Template textTemplate;

  @Inject
  public SplitInviteEmailObserver(
      Mailer mailer,
      @Location("email/split-invite.html") Template htmlTemplate,
      @Location("email/split-invite.txt") Template textTemplate) {
    this.mailer = mailer;
    this.htmlTemplate = htmlTemplate;
    this.textTemplate = textTemplate;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onSplitInviteIssued(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) SplitInviteIssued event) {
    String html = htmlTemplate.data("artistName", event.artistName())
        .data("releaseTitle", event.releaseTitle()).data("trackShares", event.trackShares())
        .data("acceptUrl", event.acceptUrl()).render();
    String text = textTemplate.data("artistName", event.artistName())
        .data("releaseTitle", event.releaseTitle()).data("trackShares", event.trackShares())
        .data("acceptUrl", event.acceptUrl()).render();
    // idempotencyKey: the accept URL is unique per issued invite (unique token), so it dedupes resends.
    mailer.send(new EmailMessage(
        event.email(), "You've been added to a release on BeatzClik", text, html, event.acceptUrl()));
  }
}
