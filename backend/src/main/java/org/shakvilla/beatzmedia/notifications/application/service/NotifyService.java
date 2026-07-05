package org.shakvilla.beatzmedia.notifications.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotifyCommand;
import org.shakvilla.beatzmedia.notifications.application.port.in.NotifyUseCase;
import org.shakvilla.beatzmedia.notifications.application.port.out.NotificationRepository;
import org.shakvilla.beatzmedia.notifications.domain.Notification;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for {@link NotifyUseCase} — the internal port invoked ONLY by in-module
 * event observers (`adapter.in.events`), never exposed over REST. Notifications ADD §4.1 / §9.
 *
 * <p><strong>Idempotency (INV-N4).</strong> {@code dedupeKey} (event id + recipient + type) is
 * the natural key: a redelivered event must create no duplicate row. Guarded two ways —
 *
 * <ul>
 *   <li>a cheap fast-path {@code findByDedupeKey} lookup before any insert — if the notification
 *       already exists, its id is returned as a no-op success (short-circuits the common
 *       sequential replay, mirrors the payments {@code TipSettlementSubscriber} pattern);
 *   <li>the persistence adapter's unique partial index on {@code dedupe_key} is the ACTUAL
 *       concurrency guard — a racing duplicate insert violates the constraint; the outbound
 *       adapter ({@code JpaNotificationRepository}) catches that and re-reads the winning row so a
 *       concurrent replay still returns a single, valid id rather than a 500.
 * </ul>
 *
 * <p>In-app persistence always happens independent of any downstream delivery outcome — a
 * notification row is the one durable fact this service produces. On a genuine NEW row (never on
 * a dedupe-guard replay no-op) this service fires {@link NotificationCreated}, observed {@code
 * AFTER_SUCCESS} by {@link DispatchSubscriber} (WU-NOT-2) to fan out to email/SMS post-commit —
 * reusing this same creation path rather than a second, independent observer of the source domain
 * events (hard requirement: no double dispatch logic).
 */
@ApplicationScoped
@Transactional
public class NotifyService implements NotifyUseCase {

  private static final Logger LOG = Logger.getLogger(NotifyService.class);

  private final NotificationRepository repository;
  private final Clock clock;
  private final IdGenerator ids;
  private final Event<NotificationCreated> notificationCreatedEvent;

  @Inject
  public NotifyService(
      NotificationRepository repository,
      Clock clock,
      IdGenerator ids,
      Event<NotificationCreated> notificationCreatedEvent) {
    this.repository = repository;
    this.clock = clock;
    this.ids = ids;
    this.notificationCreatedEvent = notificationCreatedEvent;
  }

  @Override
  public NotificationId notify(NotifyCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command must not be null");
    }

    // Fast-path replay guard (INV-N4): if a notification for this event is already visible, do
    // nothing further. The unique dedupe_key index is the actual exactly-once guard against a
    // concurrent race; this check just avoids the common sequential-replay round-trip.
    var existing = repository.findByDedupeKey(command.dedupeKey());
    if (existing.isPresent()) {
      LOG.debugf(
          "notification for dedupeKey %s already exists; ignoring replay", command.dedupeKey());
      return existing.get().id();
    }

    Notification notification =
        Notification.create(
            new NotificationId(ids.newId()),
            command.recipient(),
            command.type(),
            command.title(),
            command.body(),
            command.to(),
            command.dedupeKey(),
            clock.now());

    Notification saved = repository.save(notification);

    // Fired only for a genuine new row; the observer runs post-commit (AFTER_SUCCESS) so a
    // provider outage during dispatch never rolls back the in-app notification.
    notificationCreatedEvent.fire(
        new NotificationCreated(
            saved.id().value(), saved.recipientId().value(), saved.title(), saved.body()));

    return saved.id();
  }
}
