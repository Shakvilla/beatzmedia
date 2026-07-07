package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code push_item} table. Domain types carry no ORM annotations; this adapter
 * class is the only place Hibernate annotations appear. Admin ADD §5.2 / §7.
 */
@Entity
@Table(name = "push_item")
public class PushItemEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "day", nullable = false)
  public String day;

  @Column(name = "time_label", nullable = false)
  public String timeLabel;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "audience", nullable = false)
  public String audience;

  @Column(name = "scheduled_at")
  public Instant scheduledAt;
}
