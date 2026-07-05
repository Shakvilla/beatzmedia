package org.shakvilla.beatzmedia.notifications.fakes;

import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.notifications.application.port.out.EmailMessage;
import org.shakvilla.beatzmedia.notifications.application.port.out.Mailer;
import org.shakvilla.beatzmedia.notifications.domain.PermanentDeliveryException;
import org.shakvilla.beatzmedia.notifications.domain.TransientDeliveryException;

/**
 * In-memory {@link Mailer} fake. Programmable outcome queue: by default every {@link #send}
 * succeeds and is recorded; tests can enqueue a transient or permanent failure to be thrown on the
 * NEXT call via {@link #failNextWithTransient()} / {@link #failNextWithPermanent()}.
 */
public class FakeMailer implements Mailer {

  private final List<EmailMessage> sent = new ArrayList<>();
  private Outcome nextOutcome = Outcome.SUCCESS;

  @Override
  public void send(EmailMessage message) {
    Outcome outcome = nextOutcome;
    nextOutcome = Outcome.SUCCESS; // one-shot: reverts to success after the programmed failure
    switch (outcome) {
      case TRANSIENT -> throw new TransientDeliveryException("fake transient email failure");
      case PERMANENT -> throw new PermanentDeliveryException("fake permanent email failure");
      case SUCCESS -> sent.add(message);
    }
  }

  public void failNextWithTransient() {
    nextOutcome = Outcome.TRANSIENT;
  }

  public void failNextWithPermanent() {
    nextOutcome = Outcome.PERMANENT;
  }

  public List<EmailMessage> sent() {
    return List.copyOf(sent);
  }

  public int sentCount() {
    return sent.size();
  }

  private enum Outcome {
    SUCCESS,
    TRANSIENT,
    PERMANENT
  }
}
