package org.shakvilla.beatzmedia.notifications.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.identity.application.port.out.PhoneVerificationSmsSender;
import org.shakvilla.beatzmedia.notifications.application.port.out.SmsMessage;
import org.shakvilla.beatzmedia.notifications.application.port.out.SmsSender;

/** Bridges identity's verification SMS port to notifications' provider-selected SMS transport. */
@ApplicationScoped
public class IdentityPhoneVerificationSmsAdapter implements PhoneVerificationSmsSender {
  private final SmsSender smsSender;

  @Inject
  public IdentityPhoneVerificationSmsAdapter(SmsSender smsSender) {
    this.smsSender = smsSender;
  }

  @Override
  public String selectedProvider() {
    return smsSender.selectedProvider();
  }

  @Override
  public void send(String phone, String message, String messageId, String provider) {
    smsSender.send(new SmsMessage(phone, message, messageId), provider);
  }
}
