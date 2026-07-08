package org.shakvilla.beatzmedia.events.application.service;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.events.application.port.in.IssueTicket;
import org.shakvilla.beatzmedia.events.application.port.in.IssueTicketCommand;
import org.shakvilla.beatzmedia.events.application.port.in.TicketIssued;
import org.shakvilla.beatzmedia.events.application.port.out.EventRepository;
import org.shakvilla.beatzmedia.events.domain.Ticket;
import org.shakvilla.beatzmedia.events.domain.TicketId;
import org.shakvilla.beatzmedia.events.domain.TicketTier;
import org.shakvilla.beatzmedia.events.domain.TicketTierNotFoundException;
import org.shakvilla.beatzmedia.events.domain.TicketTierSoldOut;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for the INTERNAL {@link IssueTicket} port — invoked in-process by {@code
 * commerce} on settlement of a ticket cart line. Mints ticket rows, decrements the tier's {@code
 * sold} counter under a row lock, and never writes an event-level "sold-out" flag (INV-EVT-2:
 * status is derived at read time from the very tier counters this service updates). Events ADD
 * §4.1 / §8 / §9 / OQ-11.
 */
@ApplicationScoped
public class IssueTicketService implements IssueTicket {

  private final EventRepository repository;
  private final IdGenerator ids;
  private final Clock clock;
  private final Event<TicketTierSoldOut> ticketTierSoldOutEvent;

  @Inject
  public IssueTicketService(
      EventRepository repository,
      IdGenerator ids,
      Clock clock,
      Event<TicketTierSoldOut> ticketTierSoldOutEvent) {
    this.repository = repository;
    this.ids = ids;
    this.clock = clock;
    this.ticketTierSoldOutEvent = ticketTierSoldOutEvent;
  }

  @Override
  @Transactional
  public TicketIssued issue(IssueTicketCommand command) {
    // OQ-11: SELECT ... FOR UPDATE — serializes concurrent settlements against this tier for the
    // duration of this transaction.
    TicketTier tier =
        repository
            .lockTierForUpdate(command.tierId())
            .orElseThrow(() -> new TicketTierNotFoundException(command.tierId().value()));

    // Idempotency: a re-delivered settlement for the same (orderId, tierId) mints no duplicate
    // tickets and double-counts no `sold` — replay the previously-minted result instead.
    if (repository.ticketExistsForOrderTier(command.orderId(), command.tierId())) {
      List<Ticket> existing = repository.ticketsForOrderTier(command.orderId(), command.tierId());
      return new TicketIssued(
          existing.stream().map(Ticket::id).toList(),
          existing.stream().map(Ticket::qrRef).toList(),
          tier.isSoldOut());
    }

    // INV-EVT-1: reject an issuance that would push sold > capacity. tier.sell(..) is the same pure
    // domain guard unit-tested in isolation; the DB CHECK(sold <= capacity) backstops it.
    TicketTier sold = tier.sell(command.quantity());

    List<TicketId> ticketIds = new ArrayList<>(command.quantity());
    List<String> qrRefs = new ArrayList<>(command.quantity());
    var now = clock.now();
    for (int i = 0; i < command.quantity(); i++) {
      TicketId ticketId = new TicketId(ids.newId());
      String qrRef = ids.newId();
      repository.saveTicket(
          new Ticket(
              ticketId,
              command.eventId(),
              command.tierId(),
              command.orderId(),
              command.holderAccountId(),
              command.holderName(),
              qrRef,
              now));
      ticketIds.add(ticketId);
      qrRefs.add(qrRef);
    }
    repository.incrementSold(command.tierId(), command.quantity());

    boolean tierNowSoldOut = sold.isSoldOut();
    if (tierNowSoldOut) {
      // Fired within the transaction; delivered to observers after successful commit (Events ADD
      // §2/§9 — mirrors the catalog module's ReleaseWentLive firing convention).
      ticketTierSoldOutEvent.fire(
          new TicketTierSoldOut(command.eventId().value(), command.tierId().value(), now));
    }

    return new TicketIssued(ticketIds, qrRefs, tierNowSoldOut);
  }
}
