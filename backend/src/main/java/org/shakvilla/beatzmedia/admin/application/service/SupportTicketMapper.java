package org.shakvilla.beatzmedia.admin.application.service;

import java.util.Locale;

import org.shakvilla.beatzmedia.admin.application.port.in.SupportMessageView;
import org.shakvilla.beatzmedia.admin.application.port.in.SupportTicketDetailView;
import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader;
import org.shakvilla.beatzmedia.admin.domain.SupportMessage;
import org.shakvilla.beatzmedia.admin.domain.SupportTicket;

/**
 * Maps domain {@link SupportTicket} / {@link SupportMessage} to application-layer read views.
 * {@code requesterRef} is resolved to a display name via {@link IdentityReader} — never a direct
 * table join (admin ADD §4.3). Shared by all support use-case services.
 */
final class SupportTicketMapper {

  private SupportTicketMapper() {}

  static SupportTicketDetailView toDetail(SupportTicket ticket, IdentityReader identityReader) {
    String requester = identityReader.displayNameOf(ticket.getRequesterRef())
        .orElse(ticket.getRequesterRef());
    return new SupportTicketDetailView(
        ticket.getId(),
        ticket.getSubject(),
        requester,
        ticket.getChannel(),
        ticket.getPriority().wireValue(),
        ticket.getStatus().wireValue(),
        ticket.getCreatedAt(),
        ticket.getMessages().stream().map(SupportTicketMapper::toMessage).toList());
  }

  static SupportMessageView toMessage(SupportMessage message) {
    return new SupportMessageView(
        message.getId(),
        message.getFrom().name().toLowerCase(Locale.ROOT),
        message.getAuthor(),
        message.getText(),
        message.getCreatedAt());
  }
}
