package org.shakvilla.beatzmedia.admin.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.GetSupportTicket;
import org.shakvilla.beatzmedia.admin.application.port.in.SupportTicketDetailView;
import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader;
import org.shakvilla.beatzmedia.admin.application.port.out.SupportTicketRepository;
import org.shakvilla.beatzmedia.admin.domain.SupportTicket;
import org.shakvilla.beatzmedia.admin.domain.TicketNotFoundException;

/**
 * Application service for LLFR-ADMIN-08.1 (ticket detail + thread). Read-only: no audit entry.
 * Auth: any admin role.
 */
@ApplicationScoped
public class GetSupportTicketService implements GetSupportTicket {

  private final SupportTicketRepository tickets;
  private final IdentityReader identityReader;

  @Inject
  public GetSupportTicketService(SupportTicketRepository tickets, IdentityReader identityReader) {
    this.tickets = tickets;
    this.identityReader = identityReader;
  }

  @Override
  @Transactional
  public SupportTicketDetailView get(String actorId, String ticketId) {
    SupportTicket ticket =
        tickets.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
    return SupportTicketMapper.toDetail(ticket, identityReader);
  }
}
