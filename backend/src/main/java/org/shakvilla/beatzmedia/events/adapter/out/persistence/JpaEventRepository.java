package org.shakvilla.beatzmedia.events.adapter.out.persistence;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;

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
 * JPA implementation of {@link EventRepository}. Reads/writes only {@code event} /
 * {@code ticket_tier} / {@code ticket}; no cross-module joins. Transaction boundary = the
 * application service. Events ADD §5.2.
 */
@ApplicationScoped
public class JpaEventRepository implements EventRepository {

  private final EntityManager em;
  private final EventEntityMapper mapper;

  @Inject
  public JpaEventRepository(EntityManager em, EventEntityMapper mapper) {
    this.em = em;
    this.mapper = mapper;
  }

  @Override
  public Page<Event> find(EventFilter filter, PageRequest page) {
    StringBuilder whereJpql = new StringBuilder("FROM EventEntity e WHERE 1 = 1");
    filter.city().ifPresent(c -> whereJpql.append(" AND e.city = :city"));
    filter.category().ifPresent(c -> whereJpql.append(" AND e.category = :category"));

    TypedQuery<Long> countQuery = em.createQuery("SELECT COUNT(e) " + whereJpql, Long.class);
    TypedQuery<EventEntity> listQuery =
        em.createQuery(
            "SELECT e " + whereJpql + " ORDER BY e.eventAt ASC, e.id ASC", EventEntity.class);
    filter.city().ifPresent(c -> {
      countQuery.setParameter("city", c);
      listQuery.setParameter("city", c);
    });
    filter.category().ifPresent(c -> {
      countQuery.setParameter("category", c.wireValue());
      listQuery.setParameter("category", c.wireValue());
    });

    long total = countQuery.getSingleResult();
    List<EventEntity> entities =
        listQuery.setFirstResult(page.offset()).setMaxResults(page.size()).getResultList();

    List<String> eventIds = entities.stream().map(e -> e.id).toList();
    Map<String, List<TicketTierEntity>> tiersByEvent = loadTiersByEventIds(eventIds);
    List<Event> items =
        entities.stream()
            .map(e -> mapper.toDomain(e, tiersByEvent.getOrDefault(e.id, List.of())))
            .toList();

    return Page.of(items, page.page(), page.size(), total);
  }

  @Override
  public Optional<Event> findById(EventId id) {
    EventEntity e = em.find(EventEntity.class, id.value());
    if (e == null) {
      return Optional.empty();
    }
    List<TicketTierEntity> tiers = loadTiers(id.value());
    return Optional.of(mapper.toDomain(e, tiers));
  }

  @Override
  public Optional<TicketTier> lockTierForUpdate(TicketTierId id) {
    TicketTierEntity e = em.find(TicketTierEntity.class, id.value(), LockModeType.PESSIMISTIC_WRITE);
    return e == null ? Optional.empty() : Optional.of(mapper.toDomain(e));
  }

  @Override
  public void save(Event event) {
    EventEntity existing = em.find(EventEntity.class, event.id().value());
    Instant now = Instant.now();
    EventEntity entity = mapper.toEntity(event, existing);
    entity.updatedAt = now;
    if (existing == null) {
      entity.createdAt = now;
      em.persist(entity);
    }

    Map<String, TicketTierEntity> existingTiers =
        loadTiers(event.id().value()).stream()
            .collect(Collectors.toMap(t -> t.id, t -> t, (a, b) -> a, LinkedHashMap::new));
    for (TicketTier tier : event.tiers()) {
      TicketTierEntity tierEntity = existingTiers.get(tier.id().value());
      TicketTierEntity mapped = mapper.toEntity(tier, tierEntity);
      if (tierEntity == null) {
        em.persist(mapped);
      }
    }
  }

  @Override
  public void saveTicket(Ticket ticket) {
    em.persist(mapper.toEntity(ticket));
  }

  @Override
  public boolean ticketExistsForOrderTier(OrderId orderId, TicketTierId tierId) {
    Long count =
        em.createQuery(
                "SELECT COUNT(t) FROM TicketEntity t WHERE t.orderId = :orderId AND t.tierId = :tierId",
                Long.class)
            .setParameter("orderId", orderId.value())
            .setParameter("tierId", tierId.value())
            .getSingleResult();
    return count != null && count > 0;
  }

  @Override
  public void incrementSold(TicketTierId tierId, int quantity) {
    em.createQuery("UPDATE TicketTierEntity t SET t.sold = t.sold + :qty WHERE t.id = :id")
        .setParameter("qty", quantity)
        .setParameter("id", tierId.value())
        .executeUpdate();
  }

  @Override
  public List<Ticket> ticketsForOrderTier(OrderId orderId, TicketTierId tierId) {
    return em.createQuery(
            "SELECT t FROM TicketEntity t WHERE t.orderId = :orderId AND t.tierId = :tierId"
                + " ORDER BY t.issuedAt ASC",
            TicketEntity.class)
        .setParameter("orderId", orderId.value())
        .setParameter("tierId", tierId.value())
        .getResultList()
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  private List<TicketTierEntity> loadTiers(String eventId) {
    return em.createQuery(
            "SELECT t FROM TicketTierEntity t WHERE t.eventId = :eventId ORDER BY t.id ASC",
            TicketTierEntity.class)
        .setParameter("eventId", eventId)
        .getResultList();
  }

  private Map<String, List<TicketTierEntity>> loadTiersByEventIds(List<String> eventIds) {
    if (eventIds.isEmpty()) {
      return Map.of();
    }
    List<TicketTierEntity> all =
        em.createQuery(
                "SELECT t FROM TicketTierEntity t WHERE t.eventId IN :ids ORDER BY t.id ASC",
                TicketTierEntity.class)
            .setParameter("ids", eventIds)
            .getResultList();
    Map<String, List<TicketTierEntity>> byEvent = new LinkedHashMap<>();
    for (TicketTierEntity t : all) {
      byEvent.computeIfAbsent(t.eventId, k -> new java.util.ArrayList<>()).add(t);
    }
    return byEvent;
  }
}
