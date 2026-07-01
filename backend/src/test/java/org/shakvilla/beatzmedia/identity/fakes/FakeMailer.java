package org.shakvilla.beatzmedia.identity.fakes;

import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.identity.application.port.out.Mailer;

/**
 * In-memory fake for {@link Mailer}. Captures sent password-reset messages for unit-test
 * assertions instead of touching SMTP.
 */
public class FakeMailer implements Mailer {

  /** A captured password-reset email. */
  public record SentReset(String email, String resetToken) {}

  private final List<SentReset> sent = new ArrayList<>();

  @Override
  public void sendPasswordReset(String email, String resetToken) {
    sent.add(new SentReset(email, resetToken));
  }

  public List<SentReset> sent() {
    return List.copyOf(sent);
  }
}
