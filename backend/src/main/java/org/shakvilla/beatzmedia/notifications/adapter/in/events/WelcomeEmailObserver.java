package org.shakvilla.beatzmedia.notifications.adapter.in.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.identity.domain.AccountRegistered;
import org.shakvilla.beatzmedia.notifications.application.port.out.EmailMessage;
import org.shakvilla.beatzmedia.notifications.application.port.out.Mailer;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;

/** Sends one welcome message after a new self-service or first-time social registration commits. */
@ApplicationScoped
public class WelcomeEmailObserver {

  private static final String SUBJECT = "Welcome to BeatzClik";

  private final Mailer mailer;
  private final Template htmlTemplate;
  private final Template textTemplate;
  private final String appBaseUrl;

  @Inject
  public WelcomeEmailObserver(
      Mailer mailer,
      @Location("email/welcome.html") Template htmlTemplate,
      @Location("email/welcome.txt") Template textTemplate,
      @ConfigProperty(name = "beatz.app.base-url", defaultValue = "http://localhost:5173")
          String appBaseUrl) {
    this.mailer = mailer;
    this.htmlTemplate = htmlTemplate;
    this.textTemplate = textTemplate;
    this.appBaseUrl = appBaseUrl.replaceAll("/+$", "");
  }

  public void onAccountRegistered(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) AccountRegistered event) {
    String exploreUrl = appBaseUrl + "/explore";
    String html = htmlTemplate.data("name", event.name()).data("exploreUrl", exploreUrl).render();
    String text = textTemplate.data("name", event.name()).data("exploreUrl", exploreUrl).render();
    // Stable account-scoped idempotency key prevents duplicate provider accepts on event redelivery.
    mailer.send(new EmailMessage(
        event.email(), SUBJECT, text, html, "welcome:" + event.accountId()));
  }
}
