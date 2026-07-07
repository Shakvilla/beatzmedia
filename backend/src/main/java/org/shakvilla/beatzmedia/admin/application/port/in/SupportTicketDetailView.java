package org.shakvilla.beatzmedia.admin.application.port.in;

import java.time.Instant;
import java.util.List;

/**
 * Read view of a ticket with its full message thread — matches the frontend {@code SupportTicket}
 * shape exactly ({@code id, subject, requester, channel, priority, status, age, messages}). Admin
 * ADD §6 / LLFR-ADMIN-08.1.
 */
public record SupportTicketDetailView(
    String id,
    String subject,
    String requester,
    String channel,
    String priority,
    String status,
    Instant createdAt,
    List<SupportMessageView> messages) {}
