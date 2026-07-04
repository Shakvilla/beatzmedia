package org.shakvilla.beatzmedia.notifications.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for the {@code notification} table. Notifications ADD §7 / V947 migration. */
@Entity
@Table(name = "notification")
public class NotificationEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "recipient_id", nullable = false)
  public String recipientId;

  @Column(name = "type", nullable = false)
  public String type;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "body", nullable = false)
  public String body;

  @Column(name = "to_route")
  public String toRoute;

  @Column(name = "is_read", nullable = false)
  public boolean read;

  @Column(name = "dedupe_key")
  public String dedupeKey;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "read_at")
  public Instant readAt;
}
