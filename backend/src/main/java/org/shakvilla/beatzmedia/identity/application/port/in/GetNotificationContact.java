package org.shakvilla.beatzmedia.identity.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port: resolve an account's notification contact channels + category opt-ins for the
 * {@code notifications} module's email/SMS dispatch (WU-NOT-2). This is the ONLY way another
 * module may learn a recipient's contact/opt-in state — it never reads {@code account} or
 * {@code fan_settings} tables directly (hexagonal dependency rule; identity ADD §2 / notifications
 * ADD §4.2, §5.2).
 *
 * <p>Read-only, side-effect free, safe to call for any existing account id. If the account (or its
 * lazily-created fan settings) does not exist, {@link #resolve} returns a contact view with both
 * opt-ins {@code false} and no usable channel rather than throwing — the caller (notifications)
 * treats "no data" the same as "opted out" (INV-N3: no attempt without opt-in AND a usable
 * contact).
 */
public interface GetNotificationContact {

  /** Resolve the notification contact + opt-in view for {@code accountId}. */
  NotificationContactView resolve(AccountId accountId);

  /**
   * Read-model returned to calling modules.
   *
   * @param email account email (always present for a real account; may be {@code null} if the
   *     account cannot be found)
   * @param phone fan-settings phone number, or {@code null} if unset
   * @param emailOptIn true if the recipient opted into at least one email-eligible category
   *     ({@code newReleases} or {@code playlistUpdates} — the two categories the frontend exposes
   *     as "email-worthy"; {@code dropsOffers} is a promotional opt-in, kept separate)
   * @param smsOptIn true if the recipient opted into SMS-eligible categories. Fan settings today
   *     expose only email-oriented categories (ADD §7); until a dedicated SMS toggle exists, SMS
   *     opt-in mirrors {@code emailOptIn} AND requires a non-blank {@code phone} (a phone with no
   *     opt-in signal is never messaged).
   */
  record NotificationContactView(String email, String phone, boolean emailOptIn, boolean smsOptIn) {}
}
