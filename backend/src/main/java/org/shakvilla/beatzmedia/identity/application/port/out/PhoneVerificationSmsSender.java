package org.shakvilla.beatzmedia.identity.application.port.out;

/** Output port for delivering a phone verification code through the selected SMS provider. */
public interface PhoneVerificationSmsSender {
  String selectedProvider();

  void send(String phone, String message, String messageId, String provider);
}
