package org.shakvilla.beatzmedia.notifications.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.port.out.NotificationContactPort;
import org.shakvilla.beatzmedia.notifications.application.port.out.NotificationContactPort.ContactView;
import org.shakvilla.beatzmedia.notifications.domain.Channel;
import org.shakvilla.beatzmedia.notifications.domain.DeliveryAttempt;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;

/**
 * Observes {@link NotificationCreated} {@code AFTER_SUCCESS} of the notification-creation
 * transaction and fans out to the recipient's enabled external channels (WU-NOT-2,
 * LLFR-NOTIF-02.1). This is the ONLY dispatch trigger — it reacts to the in-module
 * notification-created signal, NOT a second, independent observer of the upstream source domain
 * events (hard requirement: no double logic; {@code NotificationEventObservers} in {@code
 * adapter.in.events} handles ONLY in-app creation).
 *
 * <p><strong>INV-N3.</strong> A channel is dispatched only if identity reports opt-in AND a usable
 * contact exists; otherwise no {@link DeliveryAttempt} row is created for that channel at all.
 *
 * <p><strong>Post-commit.</strong> Firing {@code AFTER_SUCCESS} guarantees the in-app notification
 * row is already durably committed before any provider call is attempted — a provider outage can
 * never roll back or hide the in-app feed row. Each per-channel unit of work runs in its own
 * {@code REQUIRES_NEW} transaction (see {@link DeliveryAttemptService}).
 */
@ApplicationScoped
public class DispatchSubscriber {

  private static final Logger LOG = Logger.getLogger(DispatchSubscriber.class);

  private final NotificationContactPort contactPort;
  private final DeliveryAttemptService attemptService;

  @Inject
  public DispatchSubscriber(NotificationContactPort contactPort, DeliveryAttemptService attemptService) {
    this.contactPort = contactPort;
    this.attemptService = attemptService;
  }

  public void onNotificationCreated(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) NotificationCreated event) {
    ContactView contact = contactPort.resolve(new AccountId(event.recipientId()));
    NotificationId notificationId = new NotificationId(event.notificationId());

    if (contact.emailDispatchable()) {
      dispatch(notificationId, Channel.email, contact.email(), event.title(), event.body());
    } else {
      LOG.debugf(
          "notification %s: email channel skipped (opted out or no usable contact) — INV-N3",
          notificationId);
    }

    if (contact.smsDispatchable()) {
      dispatch(notificationId, Channel.sms, contact.phone(), event.title(), event.body());
    } else {
      LOG.debugf(
          "notification %s: sms channel skipped (opted out or no usable contact) — INV-N3",
          notificationId);
    }
  }

  private void dispatch(NotificationId notificationId, Channel channel, String to, String title, String body) {
    DeliveryAttempt attempt = attemptService.createPendingIfAbsent(notificationId, channel);
    attemptService.attemptSend(attempt, to, title, body);
  }
}
