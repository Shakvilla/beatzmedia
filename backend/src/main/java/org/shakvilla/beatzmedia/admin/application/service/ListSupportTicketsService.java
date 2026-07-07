package org.shakvilla.beatzmedia.admin.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.ListSupportTickets;
import org.shakvilla.beatzmedia.admin.application.port.in.SupportTicketDetailView;
import org.shakvilla.beatzmedia.admin.application.port.in.TicketQuery;
import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader;
import org.shakvilla.beatzmedia.admin.application.port.out.SupportTicketRepository;
import org.shakvilla.beatzmedia.admin.domain.SupportTicket;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Application service for LLFR-ADMIN-08.1 (ticket inbox). Read-only: no audit entry written
 * (audit ADD §9 — reads are not audited). Auth: any admin role may read the support inbox (admin
 * ADD §8 RBAC matrix — support section is {@code RW} for every role).
 */
@ApplicationScoped
public class ListSupportTicketsService implements ListSupportTickets {

  private final SupportTicketRepository tickets;
  private final IdentityReader identityReader;

  @Inject
  public ListSupportTicketsService(SupportTicketRepository tickets, IdentityReader identityReader) {
    this.tickets = tickets;
    this.identityReader = identityReader;
  }

  @Override
  @Transactional
  public Page<SupportTicketDetailView> list(String actorId, TicketQuery query, PageRequest page) {
    TicketQuery effective = query == null ? TicketQuery.all() : query;
    PageRequest effectivePage = page == null ? PageRequest.defaults() : page;

    Page<SupportTicket> raw = tickets.list(effective.status(), effective.q(), effectivePage);
    return new Page<>(
        raw.items().stream().map(t -> SupportTicketMapper.toDetail(t, identityReader)).toList(),
        raw.page(),
        raw.size(),
        raw.total());
  }
}
