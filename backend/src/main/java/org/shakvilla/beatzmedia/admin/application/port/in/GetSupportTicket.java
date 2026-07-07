package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port: ticket detail with its full message thread. Auth: any admin. Throws {@link
 * org.shakvilla.beatzmedia.admin.domain.TicketNotFoundException} (404) if not found.
 * LLFR-ADMIN-08.1.
 */
public interface GetSupportTicket {

  SupportTicketDetailView get(String actorId, String ticketId);
}
