package org.shakvilla.beatzmedia.notifications.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.notifications.application.port.out.SmsMessage;
import org.shakvilla.beatzmedia.notifications.application.port.out.SmsSender;
import org.shakvilla.beatzmedia.notifications.domain.PermanentDeliveryException;
import org.shakvilla.beatzmedia.platform.application.port.in.ManageProviderSelections;

/** Routes SMS sends through the selected transport and honors provider pinning on retries. */
@ApplicationScoped
public class ProviderSelectingSmsSender implements SmsSender {

  private final SmsSender capture;
  private final SmsSender frog;
  private final ManageProviderSelections selections;

  @Inject
  public ProviderSelectingSmsSender(
      @SmsProvider("capture") SmsSender capture,
      @SmsProvider("frog") SmsSender frog,
      ManageProviderSelections selections) {
    this.capture = capture;
    this.frog = frog;
    this.selections = selections;
  }

  @Override
  public String selectedProvider() {
    return selections.list().stream()
        .filter(selection -> selection.capability().equals("sms"))
        .findFirst()
        .map(ManageProviderSelections.ProviderSelectionView::provider)
        .orElse("capture");
  }

  @Override
  public void send(SmsMessage message) {
    send(message, selectedProvider());
  }

  @Override
  public void send(SmsMessage message, String provider) {
    switch (provider) {
      case "capture" -> capture.send(message);
      case "frog" -> frog.send(message);
      default -> throw new PermanentDeliveryException("sms provider is disabled or unavailable");
    }
  }
}
