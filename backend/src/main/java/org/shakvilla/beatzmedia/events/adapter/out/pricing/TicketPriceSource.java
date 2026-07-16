package org.shakvilla.beatzmedia.events.adapter.out.pricing;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.application.port.out.ModulePriceSource;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricedItem;
import org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException;
import org.shakvilla.beatzmedia.events.application.port.out.EventRepository;
import org.shakvilla.beatzmedia.events.domain.Event;
import org.shakvilla.beatzmedia.events.domain.TicketRef;
import org.shakvilla.beatzmedia.events.domain.TicketTier;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Contributes authoritative {@code ticket} pricing to commerce via the {@link ModulePriceSource} SPI
 * (WU-COM-4). The {@code refId} is {@code "eventId:tierName"} ({@link TicketRef}); the price is the
 * matched tier's {@code priceMinor}. A malformed ref, an unknown event/tier, or a SOLD-OUT tier is
 * rejected 404 rather than sold. Events owns the data; commerce never reads the event tables.
 */
@ApplicationScoped
public class TicketPriceSource implements ModulePriceSource {

  private final EventRepository repository;

  @Inject
  public TicketPriceSource(EventRepository repository) {
    this.repository = repository;
  }

  @Override
  public String entityType() {
    return "ticket";
  }

  @Override
  public PricedItem price(String refId, Map<String, Object> metadata) {
    TicketRef ref;
    try {
      ref = TicketRef.parse(refId);
    } catch (IllegalArgumentException e) {
      throw new PriceUnavailableException("ticket", refId);
    }
    Event event =
        repository
            .findById(ref.eventId())
            .orElseThrow(() -> new PriceUnavailableException("ticket", refId));
    TicketTier tier =
        event.tiers().stream()
            .filter(t -> t.name().equals(ref.tierName()))
            .findFirst()
            .orElseThrow(() -> new PriceUnavailableException("ticket", refId));
    if (tier.isSoldOut()) {
      throw new PriceUnavailableException("ticket", refId);
    }
    return new PricedItem(
        event.title(), tier.name(), event.image(),
        Money.ofMinor(tier.priceMinor(), Currency.GHS));
  }
}
