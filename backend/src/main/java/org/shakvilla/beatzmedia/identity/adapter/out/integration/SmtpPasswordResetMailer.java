package org.shakvilla.beatzmedia.identity.adapter.out.integration;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.identity.application.port.out.Mailer;
import org.shakvilla.beatzmedia.notifications.application.port.out.EmailMessage;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;

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

  private static final String SUBJECT = "Reset your BeatzClik password";

  private final org.shakvilla.beatzmedia.notifications.application.port.out.Mailer mailer;
  private final String appBaseUrl;
  private final long ttlSeconds;
  private final Template passwordResetHtml;
  private final Template passwordResetText;

  @Inject
  public SmtpPasswordResetMailer(
      org.shakvilla.beatzmedia.notifications.application.port.out.Mailer mailer,
      @ConfigProperty(name = "beatz.app.base-url", defaultValue = "http://localhost:5173")
          String appBaseUrl,
      @ConfigProperty(name = "beatz.identity.password-reset-ttl-seconds", defaultValue = "1800")
          long ttlSeconds,
      @Location("email/password-reset.html") Template passwordResetHtml,
      @Location("email/password-reset.txt") Template passwordResetText) {
    this.mailer = mailer;
    this.appBaseUrl = appBaseUrl;
    this.ttlSeconds = ttlSeconds;
    this.passwordResetHtml = passwordResetHtml;
    this.passwordResetText = passwordResetText;
  }

  @Override
  public void sendPasswordReset(String email, String resetToken) {
    String link = appBaseUrl.replaceAll("/+$", "")
        + "/reset-password?token="
        + URLEncoder.encode(resetToken, StandardCharsets.UTF_8);

    long minutes = Math.max(1, ttlSeconds / 60);
    String html = passwordResetHtml.data("resetUrl", link).data("expiresMinutes", minutes).render();
    String text = passwordResetText.data("resetUrl", link).data("expiresMinutes", minutes).render();
    mailer.send(new EmailMessage(
        email, SUBJECT, text, html, "password-reset:" + tokenDigest(resetToken)));
  }

  private static String tokenDigest(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

}
