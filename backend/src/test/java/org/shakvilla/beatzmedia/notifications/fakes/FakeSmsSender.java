package org.shakvilla.beatzmedia.notifications.fakes;

import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.notifications.application.port.out.SmsMessage;
import org.shakvilla.beatzmedia.notifications.application.port.out.SmsSender;
import org.shakvilla.beatzmedia.notifications.domain.PermanentDeliveryException;
import org.shakvilla.beatzmedia.notifications.domain.TransientDeliveryException;

/**
 * In-memory {@link SmsSender} fake. Mirrors {@link FakeMailer}'s programmable-outcome-queue
 * shape.
 */
public class FakeSmsSender implements SmsSender {

  private final List<SmsMessage> sent = new ArrayList<>();
  private Outcome nextOutcome = Outcome.SUCCESS;

  @Override
  public void send(SmsMessage message) {
    Outcome outcome = nextOutcome;
    nextOutcome = Outcome.SUCCESS;
    switch (outcome) {
      case TRANSIENT -> throw new TransientDeliveryException("fake transient sms failure");
      case PERMANENT -> throw new PermanentDeliveryException("fake permanent sms failure");
      case SUCCESS -> sent.add(message);
    }
  }

  public void failNextWithTransient() {
    nextOutcome = Outcome.TRANSIENT;
  }

  public void failNextWithPermanent() {
    nextOutcome = Outcome.PERMANENT;
  }

  public List<SmsMessage> sent() {
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
