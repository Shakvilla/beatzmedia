package org.shakvilla.beatzmedia.notifications.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "publication_notification_fanout")
public class PublicationFanoutEntity {

  @Id public String id;

  @Column(name = "publication_kind", nullable = false)
  public String publicationKind;

  @Column(name = "content_id", nullable = false)
  public String contentId;

  @Column(name = "target_kind", nullable = false)
  public String targetKind;

  @Column(name = "target_id", nullable = false)
  public String targetId;

  @Column(nullable = false)
  public String title;

  @Column(nullable = false)
  public String body;

  @Column(nullable = false)
  public String route;

  public String cursor;

  @Column(nullable = false)
  public String status;

  @Column(name = "retry_count", nullable = false)
  public int retryCount;

  @Column(name = "next_attempt_at", nullable = false)
  public Instant nextAttemptAt;

  @Column(name = "last_error")
  public String lastError;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;

  @Column(name = "completed_at")
  public Instant completedAt;
}
