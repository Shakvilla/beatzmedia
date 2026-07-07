package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.util.List;

import org.shakvilla.beatzmedia.admin.application.port.in.SupportTicketDetailView;

/**
 * Response DTO matching {@code SupportTicket} in {@code Frontend/src/lib/admin-data.ts}: {@code {
 * id, subject, requester, channel, priority, status, age, messages } }. {@code age} carries the
 * ticket's {@code createdAt} as an ISO-8601 string (never a pre-formatted relative string — the
 * frontend formats display strings, write-rest-resource §2). Served by both the inbox list and the
 * detail endpoint so the SPA can render a selected list item's thread with no extra fetch (matches
 * the existing mock behaviour in {@code admin.support.tsx}).
 */
public record SupportTicketDto(
    String id,
    String subject,
    String requester,
    String channel,
    String priority,
    String status,
    String age,
    List<SupportMessageDto> messages) {

  public static SupportTicketDto from(SupportTicketDetailView view) {
    return new SupportTicketDto(
        view.id(),
        view.subject(),
        view.requester(),
        view.channel(),
        view.priority(),
        view.status(),
        view.createdAt().toString(),
        view.messages().stream().map(SupportMessageDto::from).toList());
  }
}
