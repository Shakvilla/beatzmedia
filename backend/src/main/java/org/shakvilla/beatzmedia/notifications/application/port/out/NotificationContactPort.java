package org.shakvilla.beatzmedia.notifications.application.port.out;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Output port through which the {@code notifications} module resolves a recipient's contact
 * channels + category opt-ins, without ever reading identity's {@code account}/{@code
 * fan_settings} tables directly (hexagonal dependency rule — notifications ADD §4.2 / §5.2).
 * Implemented by an outbound integration adapter that calls identity's {@code
 * GetNotificationContact} INPUT port in-process, mirroring the podcasts→payments tip pattern
 * ({@code PaymentsTipAdapter}).
 */
public interface NotificationContactPort {

  /** Resolve the contact + opt-in view for {@code recipient}. Never throws — see impl contract. */
  ContactView resolve(AccountId recipient);

  /**
   * @param email usable email address, or {@code null}/blank if none
   * @param phone usable phone number, or {@code null}/blank if none
   * @param emailOptIn true iff the recipient opted into email notifications
   * @param smsOptIn true iff the recipient opted into SMS notifications
   */
  record ContactView(String email, String phone, boolean emailOptIn, boolean smsOptIn) {

    /** True iff email should be dispatched: opted in AND a usable address exists (INV-N3). */
    public boolean emailDispatchable() {
      return emailOptIn && email != null && !email.isBlank();
    }

    /** True iff SMS should be dispatched: opted in AND a usable phone exists (INV-N3). */
    public boolean smsDispatchable() {
      return smsOptIn && phone != null && !phone.isBlank();
    }
  }
}
