package org.shakvilla.beatzmedia.admin.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.AssignTicket;
import org.shakvilla.beatzmedia.admin.application.port.in.SupportTicketDetailView;
import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader;
import org.shakvilla.beatzmedia.admin.application.port.out.SupportTicketRepository;
import org.shakvilla.beatzmedia.admin.domain.SupportTicket;
import org.shakvilla.beatzmedia.admin.domain.TicketNotFoundException;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for LLFR-ADMIN-08.1 (assign ticket to an admin member). Auth: any admin
 * (support scope). Appends exactly one {@link AuditEntry} (INV-10).
 */
@ApplicationScoped
public class AssignTicketService implements AssignTicket {

  private final SupportTicketRepository tickets;
  private final IdentityReader identityReader;
  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;

  @Inject
  public AssignTicketService(
      SupportTicketRepository tickets,
      IdentityReader identityReader,
      AuditWriter auditWriter,
      IdGenerator idGenerator,
      Clock clock) {
    this.tickets = tickets;
    this.identityReader = identityReader;
    this.auditWriter = auditWriter;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public SupportTicketDetailView assign(String actorId, String ticketId, String assigneeId) {
    SupportTicket ticket =
        tickets.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));

    ticket.assign(assigneeId);
    tickets.save(ticket);

    auditWriter.append(new AuditEntry(
        idGenerator.newId(),
        actorId,
        "Assigned ticket to " + assigneeId,
        "SupportTicket",
        ticketId,
        AuditType.USER,
        null,
        clock.now()));

    return SupportTicketMapper.toDetail(ticket, identityReader);
  }
}
