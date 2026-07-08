package org.shakvilla.beatzmedia.events.application.port.out;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.events.application.port.in.EventFilter;
import org.shakvilla.beatzmedia.events.domain.Event;
import org.shakvilla.beatzmedia.events.domain.EventId;
import org.shakvilla.beatzmedia.events.domain.OrderId;
import org.shakvilla.beatzmedia.events.domain.Ticket;
import org.shakvilla.beatzmedia.events.domain.TicketTier;
import org.shakvilla.beatzmedia.events.domain.TicketTierId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Persistence for events, tiers, and tickets. Implemented by the JPA adapter (Postgres). Events
 * ADD §4.2.
 */
public interface EventRepository {

  Page<Event> find(EventFilter filter, PageRequest page);

  Optional<Event> findById(EventId id);

  /** {@code SELECT ... FOR UPDATE} — OQ-11 pessimistic row lock ahead of an issuance check. */
  Optional<TicketTier> lockTierForUpdate(TicketTierId id);

  void save(Event event);

  void saveTicket(Ticket ticket);

  /** Idempotency guard: has a ticket already been minted for this (orderId, tierId) pair? */
  boolean ticketExistsForOrderTier(OrderId orderId, TicketTierId tierId);

  /**
   * Apply {@code UPDATE ticket_tier SET sold = sold + quantity WHERE id = :tierId} (Events ADD §8
   * sequence diagram). Callers MUST hold the row lock from {@link #lockTierForUpdate} first and
   * have already checked {@code sold + quantity <= capacity} — this method performs no guard of
   * its own; the DB {@code CHECK (sold <= capacity)} is the last-line backstop (INV-EVT-1).
   */
  void incrementSold(TicketTierId tierId, int quantity);

  /**
   * Previously-minted tickets for a (orderId, tierId) pair, used to build an idempotent replay
   * response for a re-delivered settlement without double-minting (Events ADD §9 idempotency).
   */
  List<Ticket> ticketsForOrderTier(OrderId orderId, TicketTierId tierId);
}
