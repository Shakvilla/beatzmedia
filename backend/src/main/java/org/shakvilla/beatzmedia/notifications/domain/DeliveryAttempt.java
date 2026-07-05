package org.shakvilla.beatzmedia.notifications.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Child entity of {@link Notification} — one row per (notification, channel) outbound send
 * attempt, driving the retry/backoff state machine (LLFR-NOTIF-02.1). Notifications ADD §3 / §8.
 *
 * <p><strong>INV-N5</strong> — {@code retryCount} only increases; once an attempt reaches {@code
 * maxRetries} it transitions to {@link DeliveryStatus#dead} and is never retried again. A
 * permanent-failure classification (the provider rejects the message outright, e.g. invalid
 * address) also transitions directly to {@code dead}, bypassing the retry loop entirely.
 *
 * <p><strong>Idempotency</strong> — one row per {@code (notificationId, channel)} (unique index at
 * the persistence layer); a channel is dispatched at most once per notification (never sent twice
 * on the same channel), and provider idempotency is additionally carried by {@link
 * #providerIdempotencyKey()} so a retried send can be deduped by the provider itself.
 */
public final class DeliveryAttempt {

  private final DeliveryAttemptId id;
  private final NotificationId notificationId;
  private final Channel channel;
  private final String providerIdempotencyKey;
  private final DeliveryStatus status;
  private final int retryCount;
  private final String lastError;
  private final Instant nextAttemptAt;
  private final Instant createdAt;
  private final Instant updatedAt;

  public DeliveryAttempt(
      DeliveryAttemptId id,
      NotificationId notificationId,
      Channel channel,
      String providerIdempotencyKey,
      DeliveryStatus status,
      int retryCount,
      String lastError,
      Instant nextAttemptAt,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.notificationId = notificationId;
    this.channel = channel;
    this.providerIdempotencyKey = providerIdempotencyKey;
    this.status = status;
    this.retryCount = retryCount;
    this.lastError = lastError;
    this.nextAttemptAt = nextAttemptAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /** Factory for a brand-new, unsent attempt (status=pending, retryCount=0). */
  public static DeliveryAttempt createPending(
      DeliveryAttemptId id,
      NotificationId notificationId,
      Channel channel,
      String providerIdempotencyKey,
      Instant now) {
    return new DeliveryAttempt(
        id, notificationId, channel, providerIdempotencyKey, DeliveryStatus.pending, 0, null,
        now, now, now);
  }

  /** Transition: provider accepted the send. Terminal success — never revisited by the sweep. */
  public DeliveryAttempt markSent(Instant now) {
    return new DeliveryAttempt(
        id, notificationId, channel, providerIdempotencyKey, DeliveryStatus.sent, retryCount,
        null, null, createdAt, now);
  }

  /**
   * Transition: a transient send failure. If {@code retryCount + 1} has reached {@code
   * maxRetries}, terminates at {@link DeliveryStatus#dead} (INV-N5) with no further {@code
   * nextAttemptAt}; otherwise schedules the next attempt at {@code now + backoff} and stays
   * {@link DeliveryStatus#failed} (eligible for the next sweep).
   *
   * @param error a short, non-PII error classification (never includes recipient contact info)
   * @param backoff the computed backoff duration until the next attempt (caller supplies —
   *     exponential-with-cap policy lives in the application service, not the domain, so it can
   *     read {@code PlatformSettings} without the domain depending on it)
   */
  public DeliveryAttempt markTransientFailure(String error, int maxRetries, java.time.Duration backoff, Instant now) {
    int newRetryCount = retryCount + 1;
    if (newRetryCount >= maxRetries) {
      return new DeliveryAttempt(
          id, notificationId, channel, providerIdempotencyKey, DeliveryStatus.dead, newRetryCount,
          error, null, createdAt, now);
    }
    return new DeliveryAttempt(
        id, notificationId, channel, providerIdempotencyKey, DeliveryStatus.failed, newRetryCount,
        error, now.plus(backoff), createdAt, now);
  }

  /** Transition: a permanent (non-retryable) send failure — terminal {@code dead} immediately. */
  public DeliveryAttempt markPermanentFailure(String error, Instant now) {
    return new DeliveryAttempt(
        id, notificationId, channel, providerIdempotencyKey, DeliveryStatus.dead, retryCount,
        error, null, createdAt, now);
  }

  /** True iff this attempt is still eligible for a (re)send — i.e. not sent, not dead. */
  public boolean isPending() {
    return status == DeliveryStatus.pending || status == DeliveryStatus.failed;
  }

  public DeliveryAttemptId id() {
    return id;
  }

  public NotificationId notificationId() {
    return notificationId;
  }

  public Channel channel() {
    return channel;
  }

  public String providerIdempotencyKey() {
    return providerIdempotencyKey;
  }

  public DeliveryStatus status() {
    return status;
  }

  public int retryCount() {
    return retryCount;
  }

  public Optional<String> lastError() {
    return Optional.ofNullable(lastError);
  }

  public Optional<Instant> nextAttemptAt() {
    return Optional.ofNullable(nextAttemptAt);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
