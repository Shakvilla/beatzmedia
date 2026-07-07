package org.shakvilla.beatzmedia.admin.application.port.out;

import java.util.Optional;

import org.shakvilla.beatzmedia.admin.domain.SupportTicket;
import org.shakvilla.beatzmedia.admin.domain.TicketStatus;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Output port for {@link SupportTicket} persistence (owns {@code support_ticket} +
 * {@code support_message}; this module's tables only). Implemented by a JPA adapter in {@code
 * adapter.out.persistence}. Admin ADD §4.2 / §7.
 */
public interface SupportTicketRepository {

  /** Paged, filtered list of tickets ordered newest first. */
  Page<SupportTicket> list(TicketStatus status, String q, PageRequest page);

  /** Loads a single ticket with its full message thread, or empty if not found. */
  Optional<SupportTicket> findById(String ticketId);

  /**
   * Persists the ticket's current state (status/priority/assignee) and any newly appended
   * messages. Upsert semantics: existing rows are updated, new messages inserted.
   */
  void save(SupportTicket ticket);
}
