package org.shakvilla.beatzmedia.notifications.fakes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.port.out.NotificationRepository;
import org.shakvilla.beatzmedia.notifications.domain.Channel;
import org.shakvilla.beatzmedia.notifications.domain.DeliveryAttempt;
import org.shakvilla.beatzmedia.notifications.domain.DeliveryAttemptId;
import org.shakvilla.beatzmedia.notifications.domain.DeliveryStatus;
import org.shakvilla.beatzmedia.notifications.domain.Notification;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * In-memory {@link NotificationRepository} for unit tests. Models the two idempotency guarantees of
 * the real adapter: {@code findByDedupeKey} short-circuits a sequential replay, and {@code save}
 * rejects a second row with the same {@code dedupeKey} (the unique-index behaviour) so a test can
 * assert exactly-once creation without Postgres.
 */
public class FakeNotificationRepository implements NotificationRepository {

  private final Map<String, Notification> byId = new LinkedHashMap<>();

  @Override
  public Notification save(Notification notification) {
    // Emulate the unique partial index on dedupe_key: a DIFFERENT id trying to claim an existing
    // dedupe key is a constraint violation. Re-saving the SAME id (e.g. markRead) is an update.
    notification
        .dedupeKey()
        .ifPresent(
            key ->
                byId.values().stream()
                    .filter(n -> key.equals(n.dedupeKey().orElse(null)))
                    .filter(n -> !n.id().equals(notification.id()))
                    .findAny()
                    .ifPresent(
                        clash -> {
                          throw new IllegalStateException(
                              "duplicate dedupe_key: " + key); // stands in for SQLState 23505
                        }));
    byId.put(notification.id().value(), notification);
    return notification;
  }

  @Override
  public Optional<Notification> findById(NotificationId id) {
    return Optional.ofNullable(byId.get(id.value()));
  }

  @Override
  public Page<Notification> findByRecipient(AccountId recipient, PageRequest page) {
    List<Notification> owned =
        byId.values().stream()
            .filter(n -> n.recipientId().equals(recipient))
            .sorted(Comparator.comparing(Notification::createdAt).reversed())
            .toList();
    int from = Math.min((page.page() - 1) * page.size(), owned.size());
    int to = Math.min(from + page.size(), owned.size());
    return Page.of(new ArrayList<>(owned.subList(from, to)), page.page(), page.size(), owned.size());
  }

  @Override
  public long countUnread(AccountId recipient) {
    return byId.values().stream()
        .filter(n -> n.recipientId().equals(recipient))
        .filter(n -> !n.read())
        .count();
  }

  @Override
  public int markAllReadForRecipient(AccountId recipient, Instant readAt) {
    int touched = 0;
    for (Notification n : new ArrayList<>(byId.values())) {
      if (n.recipientId().equals(recipient) && !n.read()) {
        byId.put(n.id().value(), n.markRead(readAt));
        touched++;
      }
    }
    return touched;
  }

  @Override
  public boolean existsByDedupeKey(String dedupeKey) {
    return findByDedupeKey(dedupeKey).isPresent();
  }

  @Override
  public Optional<Notification> findByDedupeKey(String dedupeKey) {
    return byId.values().stream().filter(n -> dedupeKey.equals(n.dedupeKey().orElse(null))).findFirst();
  }

  /** Test helper — total rows stored (across all recipients). */
  public int count() {
    return byId.size();
  }

  // -------------------------------------------------------------------------
  // WU-NOT-2: delivery_attempt
  // -------------------------------------------------------------------------

  private final Map<String, DeliveryAttempt> attemptsById = new LinkedHashMap<>();

  @Override
  public DeliveryAttempt saveAttempt(DeliveryAttempt attempt) {
    // Emulate the unique (notification_id, channel) index: a DIFFERENT id trying to claim an
    // existing (notificationId, channel) pair is a constraint violation (send-idempotency guard).
    findAttempt(attempt.notificationId(), attempt.channel())
        .filter(existing -> !existing.id().equals(attempt.id()))
        .ifPresent(clash -> {
          throw new IllegalStateException(
              "duplicate delivery_attempt for (notificationId, channel): "
                  + attempt.notificationId() + "/" + attempt.channel());
        });
    attemptsById.put(attempt.id().value(), attempt);
    return attempt;
  }

  @Override
  public Optional<DeliveryAttempt> findAttemptById(DeliveryAttemptId id) {
    return Optional.ofNullable(attemptsById.get(id.value()));
  }

  @Override
  public Optional<DeliveryAttempt> findAttempt(NotificationId notificationId, Channel channel) {
    return attemptsById.values().stream()
        .filter(a -> a.notificationId().equals(notificationId) && a.channel() == channel)
        .findFirst();
  }

  @Override
  public List<DeliveryAttempt> findAttemptsByNotification(NotificationId notificationId) {
    return attemptsById.values().stream()
        .filter(a -> a.notificationId().equals(notificationId))
        .sorted(Comparator.comparing(DeliveryAttempt::createdAt))
        .toList();
  }

  @Override
  public List<DeliveryAttempt> findDueRetries(Instant now, int limit) {
    return attemptsById.values().stream()
        .filter(a -> a.status() == DeliveryStatus.pending || a.status() == DeliveryStatus.failed)
        .filter(a -> a.nextAttemptAt().isEmpty() || !a.nextAttemptAt().get().isAfter(now))
        .sorted(Comparator.comparing(DeliveryAttempt::createdAt))
        .limit(limit)
        .toList();
  }

  /** Test helper — total delivery_attempt rows stored. */
  public int attemptCount() {
    return attemptsById.size();
  }
}
