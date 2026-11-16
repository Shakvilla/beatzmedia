package org.shakvilla.beatzmedia.notifications.application.port.out;

/** Renders a notification as a branded email while keeping the provider-independent use case. */
public interface NotificationEmailRenderer {

  EmailMessage render(String to, String subject, String body, String actionRoute, String idempotencyKey);
}
