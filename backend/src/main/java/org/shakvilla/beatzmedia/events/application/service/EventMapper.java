package org.shakvilla.beatzmedia.events.application.service;

import org.shakvilla.beatzmedia.events.application.port.in.EventView;
import org.shakvilla.beatzmedia.events.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.events.application.port.in.TicketTierView;
import org.shakvilla.beatzmedia.events.domain.Event;
import org.shakvilla.beatzmedia.events.domain.TicketTier;
import org.shakvilla.beatzmedia.platform.domain.Currency;

/**
 * Maps events domain aggregates to their wire read-models. {@code status}/{@code soldOut} are
 * computed HERE from live tier availability — never copied from a stored field (INV-EVT-2). Events
 * ADD §6.
 */
final class EventMapper {

  private EventMapper() {}

  static EventView toView(Event event) {
    return new EventView(
        event.id().value(),
        event.title(),
        event.artistName(),
        event.artistId().orElse(null),
        event.lineup(),
        event.image(),
        event.eventAt() == null ? null : event.eventAt().toString(),
        event.doorsTime().orElse(null),
        event.venue(),
        event.city(),
        event.region().orElse(null),
        event.status().wireValue(),
        event.category().wireValue(),
        event.description().orElse(null),
        event.tiers().stream().map(EventMapper::toView).toList(),
        event.popularity(),
        event.ageRestriction().orElse(null));
  }

  static TicketTierView toView(TicketTier tier) {
    return new TicketTierView(
        tier.name(),
        MoneyView.ofMinor(tier.priceMinor(), Currency.GHS.name()),
        tier.perks(),
        tier.isSoldOut());
  }
}
