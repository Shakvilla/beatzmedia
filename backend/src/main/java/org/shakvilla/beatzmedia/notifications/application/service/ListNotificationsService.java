package org.shakvilla.beatzmedia.notifications.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.port.in.ListNotifications;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotificationFeed;
import org.shakvilla.beatzmedia.notifications.application.port.out.NotificationRepository;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Application service for {@link ListNotifications} (LLFR-NOTIF-01.1). Notifications ADD §4.1.
 *
 * <p>Scoped strictly to {@code caller}: the repository query is always filtered by the recipient
 * derived from the verified JWT subject (passed in by the resource) — never a client-supplied
 * account id (INV-N1, no IDOR). {@code unread} is the caller's FULL unread total, not the page's.
 */
@ApplicationScoped
@Transactional
public class ListNotificationsService implements ListNotifications {

  private final NotificationRepository repository;
  private final Clock clock;

  @Inject
  public ListNotificationsService(NotificationRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public NotificationFeed list(AccountId caller, PageRequest page) {
    if (caller == null) {
      throw new IllegalArgumentException("caller must not be null");
    }
    var now = clock.now();
    Page<org.shakvilla.beatzmedia.notifications.domain.Notification> domainPage =
        repository.findByRecipient(caller, page);
    Page<org.shakvilla.beatzmedia.notifications.application.port.in.AppNotificationView> views =
        Page.of(
            domainPage.items().stream().map(n -> NotificationMapper.toView(n, now)).toList(),
            domainPage.page(),
            domainPage.size(),
            domainPage.total());
    long unread = repository.countUnread(caller);
    return new NotificationFeed(views, unread);
  }
}
