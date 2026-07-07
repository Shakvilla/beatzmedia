package org.shakvilla.beatzmedia.admin.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate root for an inbound support ticket, owning its {@link SupportMessage} thread
 * (same-module FK). Pure Java, no framework imports. Admin ADD §3 / LLFR-ADMIN-08.1.
 *
 * <p>Fields: {@code id}, {@code subject}, {@code requesterRef} (opaque id into {@code identity},
 * resolved via a reader port — never a direct table join), {@code channel}, {@code priority},
 * {@code status}, {@code assigneeId} (nullable, an admin member id), {@code createdAt}. Mutations
 * enforce the module invariants: {@code reply} rejects blank text (422), {@code resolve} rejects
 * an already-resolved ticket (409).
 */
public final class SupportTicket {

  private final String id;
  private final String subject;
  private final String requesterRef;
  private final String channel;
  private TicketPriority priority;
  private TicketStatus status;
  private String assigneeId;
  private final Instant createdAt;
  private final List<SupportMessage> messages;

  /** Reconstitutes a ticket from persistence (no invariant re-derivation). */
  public SupportTicket(
      String id,
      String subject,
      String requesterRef,
      String channel,
      TicketPriority priority,
      TicketStatus status,
      String assigneeId,
      Instant createdAt,
      List<SupportMessage> messages) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("SupportTicket id must not be blank");
    }
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("SupportTicket subject must not be blank");
    }
    if (requesterRef == null || requesterRef.isBlank()) {
      throw new IllegalArgumentException("SupportTicket requesterRef must not be blank");
    }
    if (channel == null || channel.isBlank()) {
      throw new IllegalArgumentException("SupportTicket channel must not be blank");
    }
    if (priority == null) {
      throw new IllegalArgumentException("SupportTicket priority must not be null");
    }
    if (status == null) {
      throw new IllegalArgumentException("SupportTicket status must not be null");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("SupportTicket createdAt must not be null");
    }
    this.id = id;
    this.subject = subject;
    this.requesterRef = requesterRef;
    this.channel = channel;
    this.priority = priority;
    this.status = status;
    this.assigneeId = assigneeId;
    this.createdAt = createdAt;
    this.messages = new ArrayList<>(messages == null ? List.of() : messages);
  }

  /**
   * Appends an agent reply, moving an {@code open} ticket to {@code pending} (awaiting the
   * requester) — {@code resolved} tickets stay {@code resolved} until explicitly reopened by a new
   * inbound message (out of scope for this WU). Rejects blank text (422) before any state change.
   */
  public SupportMessage reply(String messageId, String authorDisplayName, String text, Instant now) {
    if (text == null || text.isBlank()) {
      throw new BlankReplyException();
    }
    SupportMessage message =
        new SupportMessage(messageId, id, MessageFrom.AGENT, authorDisplayName, text, now);
    messages.add(message);
    if (status == TicketStatus.OPEN) {
      status = TicketStatus.PENDING;
    }
    return message;
  }

  /** Assigns the ticket to an admin member. Idempotent (re-assigning to the same id is a no-op). */
  public void assign(String assigneeId) {
    if (assigneeId == null || assigneeId.isBlank()) {
      throw new IllegalArgumentException("assigneeId must not be blank");
    }
    this.assigneeId = assigneeId;
  }

  /** Marks the ticket resolved. Rejects an already-resolved ticket (409 ILLEGAL_TRANSITION). */
  public void resolve() {
    if (status == TicketStatus.RESOLVED) {
      throw new TicketAlreadyResolvedException(id);
    }
    this.status = TicketStatus.RESOLVED;
  }

  public String getId() {
    return id;
  }

  public String getSubject() {
    return subject;
  }

  public String getRequesterRef() {
    return requesterRef;
  }

  public String getChannel() {
    return channel;
  }

  public TicketPriority getPriority() {
    return priority;
  }

  public TicketStatus getStatus() {
    return status;
  }

  public String getAssigneeId() {
    return assigneeId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<SupportMessage> getMessages() {
    return List.copyOf(messages);
  }
}
