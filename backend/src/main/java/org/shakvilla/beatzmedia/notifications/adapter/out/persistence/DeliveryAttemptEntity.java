package org.shakvilla.beatzmedia.notifications.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for the {@code delivery_attempt} table. Notifications ADD §7 / V948 migration. */
@Entity
@Table(name = "delivery_attempt")
public class DeliveryAttemptEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "notification_id", nullable = false)
  public String notificationId;

  @Column(name = "channel", nullable = false)
  public String channel;

  @Column(name = "provider_idempotency_key", nullable = false)
  public String providerIdempotencyKey;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "retry_count", nullable = false)
  public int retryCount;

  @Column(name = "last_error")
  public String lastError;

  @Column(name = "next_attempt_at")
  public Instant nextAttemptAt;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
