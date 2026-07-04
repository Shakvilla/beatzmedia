package org.shakvilla.beatzmedia.notifications.domain;

import java.time.Instant;
import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Aggregate root for one in-app notification feed row (LLFR-NOTIF-01.*). Notifications ADD §3.
 *
 * <p><strong>INV-N1</strong> — a notification has exactly one {@code recipientId}; ownership is
 * re-checked by the application layer on every read/mutation (never trust a client-supplied
 * account id).
 *
 * <p><strong>INV-N2</strong> — {@link #markRead(Instant)} is idempotent: marking an already-read
 * row returns an equal (unchanged) instance rather than re-stamping {@code readAt}.
 *
 * <p><strong>INV-N4</strong> — {@code dedupeKey}, when present, is the natural key used by the
 * application layer (via {@code NotificationRepository.existsByDedupeKey}) to make event-driven
 * creation idempotent against redelivery. Purely-user-facing notifications (none yet at WU-NOT-1)
 * may have no dedupe key.
 */
public final class Notification {

  private final NotificationId id;
  private final AccountId recipientId;
  private final NotificationType type;
  private final String title;
  private final String body;
  private final String toRoute;
  private final boolean read;
  private final String dedupeKey;
  private final Instant createdAt;
  private final Instant readAt;

  public Notification(
      NotificationId id,
      AccountId recipientId,
      NotificationType type,
      String title,
      String body,
      String toRoute,
      boolean read,
      String dedupeKey,
      Instant createdAt,
      Instant readAt) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    if (body == null || body.isBlank()) {
      throw new IllegalArgumentException("body must not be blank");
    }
    this.id = id;
    this.recipientId = recipientId;
    this.type = type;
    this.title = title;
    this.body = body;
    this.toRoute = toRoute;
    this.read = read;
    this.dedupeKey = dedupeKey;
    this.createdAt = createdAt;
    this.readAt = readAt;
  }

  /** Factory for a brand-new, unread notification (used by {@code NotifyUseCase}). */
  public static Notification create(
      NotificationId id,
      AccountId recipientId,
      NotificationType type,
      String title,
      String body,
      String toRoute,
      String dedupeKey,
      Instant createdAt) {
    return new Notification(
        id, recipientId, type, title, body, toRoute, false, dedupeKey, createdAt, null);
  }

  /**
   * Marks this notification read. Idempotent (INV-N2): if already read, returns {@code this}
   * unchanged (no {@code readAt} re-stamp).
   */
  public Notification markRead(Instant now) {
    if (read) {
      return this;
    }
    return new Notification(id, recipientId, type, title, body, toRoute, true, dedupeKey, createdAt, now);
  }

  /** True iff {@code caller} is the owner of this notification (INV-N1). */
  public boolean isOwnedBy(AccountId caller) {
    return recipientId.equals(caller);
  }

  public NotificationId id() {
    return id;
  }

  public AccountId recipientId() {
    return recipientId;
  }

  public NotificationType type() {
    return type;
  }

  public String title() {
    return title;
  }

  public String body() {
    return body;
  }

  public Optional<String> toRoute() {
    return Optional.ofNullable(toRoute);
  }

  public boolean read() {
    return read;
  }

  public Optional<String> dedupeKey() {
    return Optional.ofNullable(dedupeKey);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Optional<Instant> readAt() {
    return Optional.ofNullable(readAt);
  }
}
