package org.shakvilla.beatzmedia.notifications.domain;

/**
 * State machine for a {@link DeliveryAttempt} (notifications ADD §3 / §8):
 *
 * <pre>
 *   pending --> sent      (provider ok)
 *   pending --> failed    (transient error; retryCount++)
 *   pending --> dead      (permanent error)
 *   failed  --> sent      (retry ok)
 *   failed  --> failed    (transient error again, retryCount < max)
 *   failed  --> dead      (retryCount == max — INV-N5, terminal, never retried again)
 * </pre>
 */
public enum DeliveryStatus {
  pending,
  sent,
  failed,
  dead
}
