package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code support_message} table (child of {@code support_ticket}, same-module
 * FK). Admin ADD §5.2 / §7.
 */
@Entity
@Table(name = "support_message")
public class SupportMessageEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "ticket_id", nullable = false, insertable = false, updatable = false)
  public String ticketId;

  @ManyToOne
  @JoinColumn(name = "ticket_id", nullable = false)
  public SupportTicketEntity ticket;

  @Column(name = "from_party", nullable = false)
  public String fromParty;

  @Column(name = "author", nullable = false)
  public String author;

  @Column(name = "body", nullable = false)
  public String body;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
