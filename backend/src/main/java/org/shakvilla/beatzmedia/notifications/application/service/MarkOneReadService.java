package org.shakvilla.beatzmedia.notifications.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.port.in.MarkOneRead;
import org.shakvilla.beatzmedia.notifications.application.port.out.NotificationRepository;
import org.shakvilla.beatzmedia.notifications.domain.Notification;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.notifications.domain.NotificationNotFoundException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Application service for {@link MarkOneRead} (LLFR-NOTIF-01.3). Notifications ADD §4.1.
 *
 * <p><strong>Ownership re-check (INV-N1, no IDOR).</strong> A missing id AND an id owned by a
 * different account both surface as the SAME {@link NotificationNotFoundException} (404) — a
 * non-owner cannot distinguish "does not exist" from "not yours".
 *
 * <p><strong>Idempotent (INV-N2).</strong> Marking an already-read row is a no-op success — no
 * {@code readAt} re-stamp, no error.
 */
@ApplicationScoped
@Transactional
public class MarkOneReadService implements MarkOneRead {

  private final NotificationRepository repository;
  private final Clock clock;

  @Inject
  public MarkOneReadService(NotificationRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public void markOneRead(AccountId caller, NotificationId id) {
    if (caller == null) {
      throw new IllegalArgumentException("caller must not be null");
    }
    Notification existing =
        repository
            .findById(id)
            .filter(n -> n.isOwnedBy(caller))
            .orElseThrow(() -> new NotificationNotFoundException(id.value()));

    if (existing.read()) {
      return; // INV-N2: already-read is a no-op success.
    }
    repository.save(existing.markRead(clock.now()));
  }
}
