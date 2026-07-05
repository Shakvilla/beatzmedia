package org.shakvilla.beatzmedia.notifications.application.service;

import java.time.Duration;
import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.notifications.application.port.out.EmailMessage;
import org.shakvilla.beatzmedia.notifications.application.port.out.Mailer;
import org.shakvilla.beatzmedia.notifications.application.port.out.NotificationRepository;
import org.shakvilla.beatzmedia.notifications.application.port.out.SmsMessage;
import org.shakvilla.beatzmedia.notifications.application.port.out.SmsSender;
import org.shakvilla.beatzmedia.notifications.domain.Channel;
import org.shakvilla.beatzmedia.notifications.domain.DeliveryAttempt;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.notifications.domain.PermanentDeliveryException;
import org.shakvilla.beatzmedia.notifications.domain.TransientDeliveryException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;

/**
 * Owns the {@link DeliveryAttempt} state machine — creating a channel's first {@code pending} row,
 * sending it, and applying the outcome transition (sent / failed-with-backoff / dead). Shared by
 * {@link DispatchSubscriber} (first send, post-commit) and the WU-PLT-2 retry sweep job so the
 * state machine and backoff policy live in exactly one place. Notifications ADD §8 / §9.
 *
 * <p><strong>Send-idempotency.</strong> A channel is sent at most once per notification:
 * {@link #createPendingIfAbsent} is a create-if-absent keyed on the persistence layer's unique
 * {@code (notification_id, channel)} index, and {@link DeliveryAttempt#providerIdempotencyKey()}
 * (built from {@code notificationId:channel}) is passed to the provider adapter so a retried send
 * is ALSO deduped by the provider itself where it supports idempotency keys.
 *
 * <p><strong>Transaction shape.</strong> Each method runs in its OWN {@code REQUIRES_NEW}
 * transaction (mirrors payments' {@code TipLedgerPoster} pattern) so a provider outage / send
 * failure never rolls back the caller's transaction (in particular, never the notification-
 * creation transaction that already committed by the time dispatch runs).
 */
@ApplicationScoped
public class DeliveryAttemptService {

  private static final Logger LOG = Logger.getLogger(DeliveryAttemptService.class);

  private final NotificationRepository repository;
  private final Mailer mailer;
  private final SmsSender smsSender;
  private final Clock clock;
  private final PlatformSettingsProvider settingsProvider;
  private final org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator ids;

  @Inject
  public DeliveryAttemptService(
      NotificationRepository repository,
      Mailer mailer,
      SmsSender smsSender,
      Clock clock,
      PlatformSettingsProvider settingsProvider,
      org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator ids) {
    this.repository = repository;
    this.mailer = mailer;
    this.smsSender = smsSender;
    this.clock = clock;
    this.settingsProvider = settingsProvider;
    this.ids = ids;
  }

  /**
   * Creates the {@code pending} attempt row for {@code (notificationId, channel)} if one does not
   * already exist (send-idempotency — a channel is attempted at most once per notification). If a
   * row already exists (e.g. a redelivered {@link NotificationCreated}), returns the EXISTING row
   * unchanged rather than resetting its progress.
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public DeliveryAttempt createPendingIfAbsent(NotificationId notificationId, Channel channel) {
    var existing = repository.findAttempt(notificationId, channel);
    if (existing.isPresent()) {
      return existing.get();
    }
    Instant now = clock.now();
    String providerKey = notificationId.value() + ":" + channel.name();
    DeliveryAttempt pending =
        DeliveryAttempt.createPending(
            new org.shakvilla.beatzmedia.notifications.domain.DeliveryAttemptId(ids.newId()),
            notificationId,
            channel,
            providerKey,
            now);
    return repository.saveAttempt(pending);
  }

  /**
   * Attempts to send {@code attempt} (email or SMS) using {@code title}/{@code body}/{@code to},
   * then persists the resulting state transition. No-op (returns the attempt unchanged) if it is
   * not currently pending/failed (already sent or dead — INV-N5, never resent).
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public DeliveryAttempt attemptSend(DeliveryAttempt attempt, String to, String title, String body) {
    if (!attempt.isPending()) {
      return attempt; // already sent or dead — never re-sent (INV-N5 / send-idempotency)
    }
    Instant now = clock.now();
    PlatformSettings settings = settingsProvider.current();

    try {
      switch (attempt.channel()) {
        case email ->
            mailer.send(new EmailMessage(to, title, body, attempt.providerIdempotencyKey()));
        case sms -> smsSender.send(new SmsMessage(to, body, attempt.providerIdempotencyKey()));
      }
      DeliveryAttempt sent = attempt.markSent(now);
      return repository.saveAttempt(sent);
    } catch (PermanentDeliveryException e) {
      LOG.warnf(
          "delivery_attempt %s channel=%s permanent failure; marking dead (no PII logged)",
          attempt.id(), attempt.channel());
      return repository.saveAttempt(attempt.markPermanentFailure(classify(e), now));
    } catch (TransientDeliveryException e) {
      int maxRetries = settings.notificationMaxRetries();
      Duration backoff = settings.notificationRetryBackoff(attempt.retryCount());
      DeliveryAttempt updated = attempt.markTransientFailure(classify(e), maxRetries, backoff, now);
      LOG.infof(
          "delivery_attempt %s channel=%s transient failure; status=%s retryCount=%d",
          attempt.id(), attempt.channel(), updated.status(), updated.retryCount());
      return repository.saveAttempt(updated);
    }
  }

  /** Short, non-PII error classification string (message only — never recipient contact info). */
  private static String classify(RuntimeException e) {
    String message = e.getMessage();
    return message == null ? e.getClass().getSimpleName() : message;
  }
}
