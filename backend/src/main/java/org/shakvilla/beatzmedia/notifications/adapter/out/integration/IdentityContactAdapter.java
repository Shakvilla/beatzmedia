package org.shakvilla.beatzmedia.notifications.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.identity.application.port.in.GetNotificationContact;
import org.shakvilla.beatzmedia.identity.application.port.in.GetNotificationContact.NotificationContactView;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.port.out.NotificationContactPort;

/**
 * Implements notifications' {@link NotificationContactPort} output port by calling identity's
 * {@link GetNotificationContact} INPUT port in-process — notifications NEVER reads or writes
 * identity's {@code account}/{@code fan_settings} tables. Mirrors the podcasts→payments tip
 * pattern ({@code PaymentsTipAdapter}). Notifications ADD §4.2 / §5.2.
 */
@ApplicationScoped
public class IdentityContactAdapter implements NotificationContactPort {

  private final GetNotificationContact getNotificationContact;

  @Inject
  public IdentityContactAdapter(GetNotificationContact getNotificationContact) {
    this.getNotificationContact = getNotificationContact;
  }

  @Override
  public ContactView resolve(AccountId recipient) {
    NotificationContactView view = getNotificationContact.resolve(recipient);
    return new ContactView(view.email(), view.phone(), view.emailOptIn(), view.smsOptIn());
  }
}
