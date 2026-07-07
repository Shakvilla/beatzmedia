package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port: agent reply on a ticket thread. Auth: any admin (support scope). Rejects blank
 * {@code text} with 422 {@code VALIDATION} before any state change. Audited (INV-10).
 * LLFR-ADMIN-08.1.
 */
public interface ReplyToTicket {

  SupportMessageView reply(String actorId, String ticketId, String text);
}
