package org.shakvilla.beatzmedia.events.adapter.out.persistence;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.events.domain.Event;
import org.shakvilla.beatzmedia.events.domain.EventCategory;
import org.shakvilla.beatzmedia.events.domain.EventId;
import org.shakvilla.beatzmedia.events.domain.OrderId;
import org.shakvilla.beatzmedia.events.domain.Ticket;
import org.shakvilla.beatzmedia.events.domain.TicketId;
import org.shakvilla.beatzmedia.events.domain.TicketTier;
import org.shakvilla.beatzmedia.events.domain.TicketTierId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maps {@code event}/{@code ticket_tier}/{@code ticket} JPA entities to/from domain objects.
 * Domain carries no ORM annotations (ArchUnit-enforced); this is the only place the mapping
 * happens. Events ADD §5.2/§7.
 */
@ApplicationScoped
public class EventEntityMapper {

  private final ObjectMapper objectMapper;

  @Inject
  public EventEntityMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  Event toDomain(EventEntity e, List<TicketTierEntity> tierEntities) {
    List<TicketTier> tiers = tierEntities.stream().map(this::toDomain).toList();
    return new Event(
        new EventId(e.id),
        e.title,
        e.artistName,
        e.artistId,
        readStringList(e.lineupJson),
        e.image,
        e.eventAt,
        e.doorsTime,
        e.venue,
        e.city,
        e.region,
        EventCategory.fromWireValue(e.category),
        e.description,
        e.ageRestriction,
        e.popularity,
        tiers);
  }

  TicketTier toDomain(TicketTierEntity e) {
    return new TicketTier(
        new TicketTierId(e.id),
        new EventId(e.eventId),
        e.name,
        e.priceMinor,
        readStringList(e.perksJson),
        e.capacity,
        e.sold);
  }

  Ticket toDomain(TicketEntity e) {
    return new Ticket(
        new TicketId(e.id),
        new EventId(e.eventId),
        new TicketTierId(e.tierId),
        new OrderId(e.orderId),
        new AccountId(e.holderAccountId),
        e.holderName,
        e.qrRef,
        e.issuedAt);
  }

  EventEntity toEntity(Event event, EventEntity target) {
    EventEntity entity = target != null ? target : new EventEntity();
    entity.id = event.id().value();
    entity.title = event.title();
    entity.artistName = event.artistName();
    entity.artistId = event.artistId().orElse(null);
    entity.lineupJson = writeStringList(event.lineup());
    entity.image = event.image();
    entity.eventAt = event.eventAt();
    entity.doorsTime = event.doorsTime().orElse(null);
    entity.venue = event.venue();
    entity.city = event.city();
    entity.region = event.region().orElse(null);
    entity.category = event.category().wireValue();
    entity.description = event.description().orElse(null);
    entity.ageRestriction = event.ageRestriction().orElse(null);
    entity.popularity = event.popularity();
    return entity;
  }

  TicketTierEntity toEntity(TicketTier tier, TicketTierEntity target) {
    TicketTierEntity entity = target != null ? target : new TicketTierEntity();
    entity.id = tier.id().value();
    entity.eventId = tier.eventId().value();
    entity.name = tier.name();
    entity.priceMinor = tier.priceMinor();
    entity.capacity = tier.capacity();
    entity.sold = tier.sold();
    entity.perksJson = writeStringList(tier.perks());
    return entity;
  }

  TicketEntity toEntity(Ticket ticket) {
    TicketEntity entity = new TicketEntity();
    entity.id = ticket.id().value();
    entity.eventId = ticket.eventId().value();
    entity.tierId = ticket.tierId().value();
    entity.orderId = ticket.orderId().value();
    entity.holderAccountId = ticket.holderAccountId().value();
    entity.holderName = ticket.holderName();
    entity.qrRef = ticket.qrRef();
    entity.issuedAt = ticket.issuedAt();
    return entity;
  }

  private List<String> readStringList(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return List.of();
    }
  }

  private String writeStringList(List<String> values) {
    if (values == null || values.isEmpty()) {
      return "[]";
    }
    try {
      return objectMapper.writeValueAsString(values);
    } catch (Exception e) {
      return "[]";
    }
  }
}
