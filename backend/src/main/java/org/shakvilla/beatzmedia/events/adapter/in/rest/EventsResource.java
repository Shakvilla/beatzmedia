package org.shakvilla.beatzmedia.events.adapter.in.rest;

import java.util.Optional;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.events.application.port.in.EventFilter;
import org.shakvilla.beatzmedia.events.application.port.in.EventView;
import org.shakvilla.beatzmedia.events.application.port.in.GetEvent;
import org.shakvilla.beatzmedia.events.application.port.in.ListEvents;
import org.shakvilla.beatzmedia.events.domain.EventCategory;
import org.shakvilla.beatzmedia.events.domain.EventId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Thin REST resource for the public events browse/detail endpoints (LLFR-EVENTS-01.1 – 01.2). Maps
 * HTTP to input ports; no business logic — {@code status}/{@code soldOut} derivation lives entirely
 * in {@code EventMapper} (INV-EVT-2). Events ADD §5.1 / API-CONTRACT.md §9.
 *
 * <ul>
 *   <li>GET /v1/events?city=&amp;category=&amp;page=&amp;size= → Page&lt;EventDto&gt; (200)
 *   <li>GET /v1/events/:id → EventDto (200); 404 NOT_FOUND
 * </ul>
 *
 * <p>Both endpoints are public (no auth required); an optional bearer token is honored only to
 * resolve the caller for a possible future per-caller decoration (Events ADD §4.1) — today neither
 * response DTO carries such a field. Tickets are NOT bought here: purchase is a {@code commerce}
 * cart line (`kind: "ticket"`, `refId: "eventId:tier"`) per Events ADD §5.1.
 */
@Path("/v1/events")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class EventsResource {

  private final ListEvents listEvents;
  private final GetEvent getEvent;
  private final JsonWebToken jwt;

  @Inject
  public EventsResource(ListEvents listEvents, GetEvent getEvent, JsonWebToken jwt) {
    this.listEvents = listEvents;
    this.getEvent = getEvent;
    this.jwt = jwt;
  }

  /** GET /v1/events?city=&category=&page=&size= — LLFR-EVENTS-01.1. */
  @GET
  public Page<EventView> list(
      @QueryParam("city") String city,
      @QueryParam("category") String category,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("20") int size) {
    EventFilter filter = new EventFilter(parseCity(city), parseCategory(category));
    return listEvents.list(filter, new PageRequest(page, size), callerId());
  }

  /** GET /v1/events/:id — LLFR-EVENTS-01.2. */
  @GET
  @Path("/{id}")
  public EventView get(@PathParam("id") String id) {
    return getEvent.get(new EventId(id), callerId());
  }

  private static Optional<String> parseCity(String raw) {
    return (raw == null || raw.isBlank()) ? Optional.empty() : Optional.of(raw);
  }

  private static Optional<EventCategory> parseCategory(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(EventCategory.fromWireValue(raw));
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Invalid event category: " + raw, "category");
    }
  }

  /** Extract caller account id from JWT sub, if a valid token is present. */
  private Optional<AccountId> callerId() {
    try {
      String sub = jwt.getSubject();
      return (sub != null && !sub.isBlank()) ? Optional.of(new AccountId(sub)) : Optional.empty();
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
