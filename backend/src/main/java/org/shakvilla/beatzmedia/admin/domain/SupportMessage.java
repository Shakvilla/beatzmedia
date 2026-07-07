package org.shakvilla.beatzmedia.admin.domain;

import java.time.Instant;

/**
 * A single message in a {@link SupportTicket}'s thread. Immutable entity, child of the ticket
 * aggregate (same-module FK). Pure Java, no framework imports. Admin ADD §3.
 *
 * <p>Fields mirror {@code SupportMessage} in {@code Frontend/src/lib/admin-data.ts}: {@code id},
 * {@code from} ({@code user}|{@code agent}), {@code author} (display name), {@code text}, {@code
 * time}.
 */
public final class SupportMessage {

  private final String id;
  private final String ticketId;
  private final MessageFrom from;
  private final String author;
  private final String text;
  private final Instant createdAt;

  public SupportMessage(
      String id, String ticketId, MessageFrom from, String author, String text, Instant createdAt) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("SupportMessage id must not be blank");
    }
    if (ticketId == null || ticketId.isBlank()) {
      throw new IllegalArgumentException("SupportMessage ticketId must not be blank");
    }
    if (from == null) {
      throw new IllegalArgumentException("SupportMessage from must not be null");
    }
    if (author == null || author.isBlank()) {
      throw new IllegalArgumentException("SupportMessage author must not be blank");
    }
    if (text == null || text.isBlank()) {
      throw new BlankReplyException();
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("SupportMessage createdAt must not be null");
    }
    this.id = id;
    this.ticketId = ticketId;
    this.from = from;
    this.author = author;
    this.text = text;
    this.createdAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public String getTicketId() {
    return ticketId;
  }

  public MessageFrom getFrom() {
    return from;
  }

  public String getAuthor() {
    return author;
  }

  public String getText() {
    return text;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
