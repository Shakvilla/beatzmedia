package org.shakvilla.beatzmedia.notifications.fakes;

import java.util.HashMap;
import java.util.Map;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.port.out.NotificationContactPort;

/**
 * In-memory {@link NotificationContactPort} fake. Tests register a {@link ContactView} per
 * account id; an unregistered account resolves to "no contact, opted out of everything" (mirrors
 * the real identity service's never-throws contract for an unknown account).
 */
public class FakeNotificationContactPort implements NotificationContactPort {

  private static final ContactView NONE = new ContactView(null, null, false, false);

  private final Map<String, ContactView> byAccount = new HashMap<>();

  @Override
  public ContactView resolve(AccountId recipient) {
    return byAccount.getOrDefault(recipient.value(), NONE);
  }

  public void put(AccountId account, ContactView view) {
    byAccount.put(account.value(), view);
  }

  public void putEmailOptedIn(AccountId account, String email) {
    put(account, new ContactView(email, null, true, false));
  }

  public void putSmsOptedIn(AccountId account, String phone) {
    put(account, new ContactView(null, phone, false, true));
  }

  public void putBothOptedIn(AccountId account, String email, String phone) {
    put(account, new ContactView(email, phone, true, true));
  }

  public void putOptedOut(AccountId account) {
    put(account, NONE);
  }
}
