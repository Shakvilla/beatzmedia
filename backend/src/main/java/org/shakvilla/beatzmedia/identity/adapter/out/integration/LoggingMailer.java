package org.shakvilla.beatzmedia.identity.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.identity.application.port.out.Mailer;

/**
 * Config-driven dev/test capture stub implementation of {@link Mailer} (OQ note, identity ADD
 * §5.2 / WU-IDN-2). Logs the password-reset message instead of sending real SMTP — no
 * {@code quarkus-mailer} dependency required for v1. The reset token itself is never logged (only
 * a redacted marker), so no secret material reaches structured logs (conventions §9).
 *
 * <p><b>Production note:</b> before go-live this adapter must be swapped for a real SMTP/provider
 * adapter (e.g. quarkus-mailer → Mailpit/SES) behind the same {@link Mailer} port — no
 * application/domain code changes required.
 */
@ApplicationScoped
public class LoggingMailer implements Mailer {

  private static final Logger LOG = Logger.getLogger(LoggingMailer.class);

  @Override
  public void sendPasswordReset(String email, String resetToken) {
    // The token is deliberately NOT logged — only its presence is recorded. Test/dev callers that
    // need the plaintext token capture it via a test double, not via log scraping.
    LOG.infof("Password reset requested for %s; reset token generated and dispatched.", email);
  }
}
