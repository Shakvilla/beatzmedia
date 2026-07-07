package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port: mark a ticket resolved. Returns the full ticket (matches {@code POST
 * .../:id/resolve} → {@code SupportTicketDto} in the ADD §5.1). Auth: any admin (support scope).
 * Throws {@link org.shakvilla.beatzmedia.admin.domain.TicketAlreadyResolvedException} (409) if
 * already resolved. Audited (INV-10). LLFR-ADMIN-08.1.
 */
public interface ResolveTicket {

  SupportTicketDetailView resolve(String actorId, String ticketId);
}
