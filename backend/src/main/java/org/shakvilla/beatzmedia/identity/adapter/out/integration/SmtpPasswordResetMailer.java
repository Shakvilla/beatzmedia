package org.shakvilla.beatzmedia.identity.adapter.out.integration;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.identity.application.port.out.Mailer;

import io.quarkus.mailer.Mail;

/**
 * Sends identity emails over SMTP.
 *
 * <p><strong>Why this replaced {@code LoggingMailer}.</strong> The previous adapter logged
 * "reset token generated and dispatched" and returned. It was {@code @ApplicationScoped} with no
 * profile guard, so it was the production implementation too — meaning no user of any kind could
 * ever recover a forgotten password, while operations saw a success line in the logs. Its own
 * javadoc said it must be swapped before go-live; nothing failed a build while it wasn't.
 *
 * <p>Dev and test point {@code quarkus.mailer.*} at Mailpit ({@code mail:1025}) and
 * {@code %test.quarkus.mailer.mock=true} mocks the transport outright, so no real mail leaves a
 * developer machine or a CI run — the same arrangement {@code notifications.SmtpMailer} already
 * relies on.
 *
 * <p><strong>No secrets in logs.</strong> Neither the token nor the recipient address is logged;
 * only that a send was attempted. The reset link necessarily carries the plaintext token, which is
 * why it exists solely inside the message body.
 */
@ApplicationScoped
public class SmtpPasswordResetMailer implements Mailer {

  private static final Logger LOG = Logger.getLogger(SmtpPasswordResetMailer.class);

  private static final String SUBJECT = "Reset your BeatzClik password";

  private final io.quarkus.mailer.Mailer mailer;
  private final String appBaseUrl;
  private final long ttlSeconds;

  @Inject
  public SmtpPasswordResetMailer(
      io.quarkus.mailer.Mailer mailer,
      @ConfigProperty(name = "beatz.app.base-url", defaultValue = "http://localhost:5173")
          String appBaseUrl,
      @ConfigProperty(name = "beatz.identity.password-reset-ttl-seconds", defaultValue = "1800")
          long ttlSeconds) {
    this.mailer = mailer;
    this.appBaseUrl = appBaseUrl;
    this.ttlSeconds = ttlSeconds;
  }

  @Override
  public void sendPasswordReset(String email, String resetToken) {
    String link = appBaseUrl.replaceAll("/+$", "")
        + "/reset-password?token="
        + URLEncoder.encode(resetToken, StandardCharsets.UTF_8);

    mailer.send(Mail.withText(email, SUBJECT, body(link)));
    LOG.info("password reset email sent");
  }

  private String body(String link) {
    long minutes = Math.max(1, ttlSeconds / 60);
    return """
        Someone asked to reset the password on your BeatzClik account.

        Open this link to choose a new one:
        %s

        The link works once and expires in %d minutes.

        If this wasn't you, no action is needed — your password has not changed.
        """
        .formatted(link, minutes);
  }
}
