package org.shakvilla.beatzmedia.events.fakes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.events.application.port.in.EventFilter;
import org.shakvilla.beatzmedia.events.application.port.out.EventRepository;
import org.shakvilla.beatzmedia.events.domain.Event;
import org.shakvilla.beatzmedia.events.domain.EventId;
import org.shakvilla.beatzmedia.events.domain.OrderId;
import org.shakvilla.beatzmedia.events.domain.Ticket;
import org.shakvilla.beatzmedia.events.domain.TicketTier;
import org.shakvilla.beatzmedia.events.domain.TicketTierId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * In-memory fake for {@link EventRepository} used in unit tests. Events are stored as a static
 * shell (all fields but tiers); {@code ticket_tier} rows are stored separately (keyed by tier id)
 * so {@link #incrementSold} mutates live state independently of the aggregate snapshot passed to
 * {@link #save}, mirroring how the real relational schema behaves.
 */
public class FakeEventRepository implements EventRepository {

  private final Map<String, Event> events = new LinkedHashMap<>();
  private final Map<String, TicketTier> tiers = new LinkedHashMap<>();
  private final List<Ticket> tickets = new ArrayList<>();

  public FakeEventRepository withEvent(Event event) {
    events.put(event.id().value(), event);
    for (TicketTier tier : event.tiers()) {
      tiers.put(tier.id().value(), tier);
    }
    return this;
  }

  /** Seed a standalone tier (no owning event row) — for {@code IssueTicket} unit tests. */
  public FakeEventRepository withTier(TicketTier tier) {
    tiers.put(tier.id().value(), tier);
    return this;
  }

  @Override
  public Page<Event> find(EventFilter filter, PageRequest page) {
    List<Event> filtered =
        events.values().stream()
            .map(this::rebuild)
            .filter(e -> filter.city().isEmpty() || e.city().equalsIgnoreCase(filter.city().get()))
            .filter(e -> filter.category().isEmpty() || e.category() == filter.category().get())
            .toList();
    int from = Math.min(page.offset(), filtered.size());
    int to = Math.min(from + page.size(), filtered.size());
    return Page.of(new ArrayList<>(filtered.subList(from, to)), page.page(), page.size(), filtered.size());
  }

  @Override
  public Optional<Event> findById(EventId id) {
    Event base = events.get(id.value());
    return base == null ? Optional.empty() : Optional.of(rebuild(base));
  }

  @Override
  public Optional<TicketTier> lockTierForUpdate(TicketTierId id) {
    return Optional.ofNullable(tiers.get(id.value()));
  }

  @Override
  public void save(Event event) {
    withEvent(event);
  }

  @Override
  public void saveTicket(Ticket ticket) {
    tickets.add(ticket);
  }

  @Override
  public boolean ticketExistsForOrderTier(OrderId orderId, TicketTierId tierId) {
    return tickets.stream()
        .anyMatch(t -> t.orderId().equals(orderId) && t.tierId().equals(tierId));
  }

  @Override
  public void incrementSold(TicketTierId tierId, int quantity) {
    TicketTier current = tiers.get(tierId.value());
    if (current == null) {
      return;
    }
    tiers.put(
        tierId.value(),
        new TicketTier(
            current.id(),
            current.eventId(),
            current.name(),
            current.priceMinor(),
            current.perks(),
            current.capacity(),
            current.sold() + quantity));
  }

  @Override
  public List<Ticket> ticketsForOrderTier(OrderId orderId, TicketTierId tierId) {
    return tickets.stream()
        .filter(t -> t.orderId().equals(orderId) && t.tierId().equals(tierId))
        .toList();
  }

  /** Rebuilds an event's tier list from the live {@code tiers} map (post-issuance state). */
  private Event rebuild(Event base) {
    List<TicketTier> liveTiers =
        base.tiers().stream().map(t -> tiers.getOrDefault(t.id().value(), t)).toList();
    return new Event(
        base.id(),
        base.title(),
        base.artistName(),
        base.artistId().orElse(null),
        base.lineup(),
        base.image(),
        base.eventAt(),
        base.doorsTime().orElse(null),
        base.venue(),
        base.city(),
        base.region().orElse(null),
        base.category(),
        base.description().orElse(null),
        base.ageRestriction().orElse(null),
        base.popularity(),
        liveTiers);
  }
}
