package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port: assign a ticket to an admin team member. Returns the full ticket (matches {@code
 * POST .../:id/assign} → {@code SupportTicketDto} in the ADD §5.1). Auth: any admin (support
 * scope). Audited (INV-10). LLFR-ADMIN-08.1.
 */
public interface AssignTicket {

  SupportTicketDetailView assign(String actorId, String ticketId, String assigneeId);
}
