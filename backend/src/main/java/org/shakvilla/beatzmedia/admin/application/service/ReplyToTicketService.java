package org.shakvilla.beatzmedia.admin.application.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.ReplyToTicket;
import org.shakvilla.beatzmedia.admin.application.port.in.SupportMessageView;
import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader;
import org.shakvilla.beatzmedia.admin.application.port.out.SupportTicketRepository;
import org.shakvilla.beatzmedia.admin.domain.BlankReplyException;
import org.shakvilla.beatzmedia.admin.domain.SupportMessage;
import org.shakvilla.beatzmedia.admin.domain.SupportTicket;
import org.shakvilla.beatzmedia.admin.domain.TicketNotFoundException;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for LLFR-ADMIN-08.1 (agent reply). Auth: any admin (support scope). Rejects
 * blank {@code text} with 422 BEFORE any state change or audit write (mirrors the reason-required
 * pattern, admin ADD §9). Appends exactly one {@link AuditEntry} (INV-10) of {@code type=user}
 * (support has no dedicated {@code AuditType} on the wire — see {@code admin-data.ts}).
 */
@ApplicationScoped
public class ReplyToTicketService implements ReplyToTicket {

  private final SupportTicketRepository tickets;
  private final IdentityReader identityReader;
  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;

  @Inject
  public ReplyToTicketService(
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
  public SupportMessageView reply(String actorId, String ticketId, String text) {
    if (text == null || text.isBlank()) {
      throw new BlankReplyException();
    }

    SupportTicket ticket =
        tickets.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));

    Instant now = clock.now();
    String authorName = identityReader.displayNameOf(actorId).orElse(actorId);
    SupportMessage message = ticket.reply(idGenerator.newId(), authorName, text, now);
    tickets.save(ticket);

    auditWriter.append(new AuditEntry(
        idGenerator.newId(),
        actorId,
        "Replied to ticket",
        "SupportTicket",
        ticketId,
        AuditType.USER,
        null,
        now));

    return SupportTicketMapper.toMessage(message);
  }
}
