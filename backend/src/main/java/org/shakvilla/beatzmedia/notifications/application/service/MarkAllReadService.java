package org.shakvilla.beatzmedia.notifications.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.port.in.MarkAllRead;
import org.shakvilla.beatzmedia.notifications.application.port.out.NotificationRepository;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Application service for {@link MarkAllRead} (LLFR-NOTIF-01.2). Notifications ADD §4.1.
 *
 * <p>Idempotent (INV-N2): a bulk {@code UPDATE ... WHERE is_read = false} naturally touches zero
 * rows on a re-issue when everything is already read — still a success, never an error.
 */
@ApplicationScoped
@Transactional
public class MarkAllReadService implements MarkAllRead {

  private final NotificationRepository repository;
  private final Clock clock;

  @Inject
  public MarkAllReadService(NotificationRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public void markAllRead(AccountId caller) {
    if (caller == null) {
      throw new IllegalArgumentException("caller must not be null");
    }
    repository.markAllReadForRecipient(caller, clock.now());
  }
}
