package org.shakvilla.beatzmedia.notifications.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.notifications.application.port.out.EmailMessage;
import org.shakvilla.beatzmedia.notifications.application.port.out.NotificationEmailRenderer;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;

@ApplicationScoped
public class QuteNotificationEmailRenderer implements NotificationEmailRenderer {

  private final Template htmlTemplate;
  private final Template textTemplate;
  private final String appBaseUrl;

  @Inject
  public QuteNotificationEmailRenderer(
      @Location("email/notification.html") Template htmlTemplate,
      @Location("email/notification.txt") Template textTemplate,
      @ConfigProperty(name = "beatz.app.base-url", defaultValue = "http://localhost:5173") String appBaseUrl) {
    this.htmlTemplate = htmlTemplate;
    this.textTemplate = textTemplate;
    this.appBaseUrl = appBaseUrl.replaceAll("/+$", "");
  }

  @Override
  public EmailMessage render(String to, String subject, String body, String actionRoute, String idempotencyKey) {
    String actionUrl = safeActionUrl(actionRoute);
    String html = htmlTemplate.data("title", subject).data("body", body)
        .data("actionUrl", actionUrl).render();
    String text = textTemplate.data("title", subject).data("body", body)
        .data("actionUrl", actionUrl).render();
    return new EmailMessage(to, subject, text, html, idempotencyKey);
  }

  /** Notification routes are application-relative; never turn arbitrary text into a clickable URL. */
  private String safeActionUrl(String route) {
    if (route == null || !route.startsWith("/") || route.startsWith("//")) {
      return null;
    }
    return appBaseUrl + route;
  }
}
