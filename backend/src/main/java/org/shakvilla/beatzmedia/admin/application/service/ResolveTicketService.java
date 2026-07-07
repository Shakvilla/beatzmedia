package org.shakvilla.beatzmedia.admin.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.ResolveTicket;
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
 * Application service for LLFR-ADMIN-08.1 (resolve ticket). Auth: any admin (support scope).
 * Throws {@link org.shakvilla.beatzmedia.admin.domain.TicketAlreadyResolvedException} (409) if
 * already resolved — checked BEFORE any state change or audit write. Appends exactly one {@link
 * AuditEntry} (INV-10).
 */
@ApplicationScoped
public class ResolveTicketService implements ResolveTicket {

  private final SupportTicketRepository tickets;
  private final IdentityReader identityReader;
  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;

  @Inject
  public ResolveTicketService(
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
  public SupportTicketDetailView resolve(String actorId, String ticketId) {
    SupportTicket ticket =
        tickets.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));

    ticket.resolve(); // throws TicketAlreadyResolvedException before any persistence/audit write
    tickets.save(ticket);

    auditWriter.append(new AuditEntry(
        idGenerator.newId(),
        actorId,
        "Resolved ticket",
        "SupportTicket",
        ticketId,
        AuditType.USER,
        null,
        clock.now()));

    return SupportTicketMapper.toDetail(ticket, identityReader);
  }
}
