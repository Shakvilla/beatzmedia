package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code support_ticket} table. Domain types carry no ORM annotations; this
 * adapter class is the only place Hibernate annotations appear. Admin ADD §5.2 / §7.
 */
@Entity
@Table(name = "support_ticket")
public class SupportTicketEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "subject", nullable = false)
  public String subject;

  @Column(name = "requester_ref", nullable = false)
  public String requesterRef;

  @Column(name = "channel", nullable = false)
  public String channel;

  @Column(name = "priority", nullable = false)
  public String priority;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "assignee_id")
  public String assigneeId;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("createdAt ASC")
  public List<SupportMessageEntity> messages = new ArrayList<>();
}
