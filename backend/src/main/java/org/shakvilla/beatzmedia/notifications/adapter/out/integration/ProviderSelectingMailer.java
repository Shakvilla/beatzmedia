package org.shakvilla.beatzmedia.notifications.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.notifications.application.port.out.EmailMessage;
import org.shakvilla.beatzmedia.notifications.application.port.out.Mailer;
import org.shakvilla.beatzmedia.notifications.domain.TransientDeliveryException;
import org.shakvilla.beatzmedia.platform.application.port.in.ManageProviderSelections;

/** Selects the transport for a new email and preserves that choice for retries. */
@ApplicationScoped
public class ProviderSelectingMailer implements Mailer {

  private final Mailer smtp;
  private final Mailer resend;
  private final ManageProviderSelections selections;

  @Inject
  public ProviderSelectingMailer(
      @EmailProvider("smtp") Mailer smtp,
      @EmailProvider("resend") Mailer resend,
      ManageProviderSelections selections) {
    this.smtp = smtp;
    this.resend = resend;
    this.selections = selections;
  }

  @Override
  public String selectedProvider() {
    return selections.list().stream()
        .filter(selection -> selection.capability().equals("email"))
        .findFirst()
        .map(ManageProviderSelections.ProviderSelectionView::provider)
        .orElse("smtp");
  }

  @Override
  public void send(EmailMessage message) {
    send(message, selectedProvider());
  }

  @Override
  public void send(EmailMessage message, String provider) {
    sendTracked(message, provider);
  }

  @Override
  public String sendTracked(EmailMessage message, String provider) {
    switch (provider) {
      case "smtp" -> { smtp.send(message); return null; }
      case "resend" -> { return resend.sendTracked(message, provider); }
      default -> throw new TransientDeliveryException("email provider is disabled or unavailable");
    }
  }
}
