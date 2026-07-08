package org.shakvilla.beatzmedia.events.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.events.application.port.in.EventFilter;
import org.shakvilla.beatzmedia.events.application.port.in.EventView;
import org.shakvilla.beatzmedia.events.application.service.ListEventsService;
import org.shakvilla.beatzmedia.events.domain.Event;
import org.shakvilla.beatzmedia.events.domain.EventCategory;
import org.shakvilla.beatzmedia.events.domain.EventId;
import org.shakvilla.beatzmedia.events.domain.TicketTier;
import org.shakvilla.beatzmedia.events.domain.TicketTierId;
import org.shakvilla.beatzmedia.events.fakes.FakeEventRepository;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/** Unit tests for {@link ListEventsService} — LLFR-EVENTS-01.1 (browse, filter, paginate). */
@Tag("unit")
class ListEventsServiceTest {

  private static Event event(String id, String city, EventCategory category, int capacity, int sold) {
    return new Event(
        new EventId(id),
        "Title " + id,
        "Artist",
        null,
        List.of(),
        "img.png",
        Instant.parse("2026-07-09T19:00:00Z"),
        null,
        "Venue",
        city,
        null,
        category,
        null,
        null,
        50,
        List.of(
            new TicketTier(
                new TicketTierId(id + "-regular"),
                new EventId(id),
                "Regular",
                15000,
                List.of(),
                capacity,
                sold)));
  }

  @Test
  void list_noFilter_returnsAllEvents() {
    FakeEventRepository repo =
        new FakeEventRepository()
            .withEvent(event("e1", "Accra", EventCategory.CONCERT, 100, 10))
            .withEvent(event("e2", "Kumasi", EventCategory.FESTIVAL, 100, 10));
    ListEventsService service = new ListEventsService(repo);

    Page<EventView> page = service.list(EventFilter.NONE, PageRequest.defaults(), Optional.empty());

    assertEquals(2, page.total());
    assertEquals(2, page.items().size());
  }

  @Test
  void list_cityFilter_returnsOnlyMatchingEvents() {
    FakeEventRepository repo =
        new FakeEventRepository()
            .withEvent(event("e1", "Accra", EventCategory.CONCERT, 100, 10))
            .withEvent(event("e2", "Kumasi", EventCategory.FESTIVAL, 100, 10));
    ListEventsService service = new ListEventsService(repo);

    Page<EventView> page =
        service.list(
            new EventFilter(Optional.of("Accra"), Optional.empty()),
            PageRequest.defaults(),
            Optional.empty());

    assertEquals(1, page.total());
    assertEquals("e1", page.items().get(0).id());
  }

  @Test
  void list_categoryFilter_returnsOnlyMatchingEvents() {
    FakeEventRepository repo =
        new FakeEventRepository()
            .withEvent(event("e1", "Accra", EventCategory.CONCERT, 100, 10))
            .withEvent(event("e2", "Accra", EventCategory.FESTIVAL, 100, 10));
    ListEventsService service = new ListEventsService(repo);

    Page<EventView> page =
        service.list(
            new EventFilter(Optional.empty(), Optional.of(EventCategory.FESTIVAL)),
            PageRequest.defaults(),
            Optional.empty());

    assertEquals(1, page.total());
    assertEquals("e2", page.items().get(0).id());
  }

  @Test
  void list_pagination_slicesResults() {
    FakeEventRepository repo = new FakeEventRepository();
    for (int i = 0; i < 5; i++) {
      repo.withEvent(event("e" + i, "Accra", EventCategory.CONCERT, 100, 10));
    }
    ListEventsService service = new ListEventsService(repo);

    Page<EventView> page = service.list(EventFilter.NONE, new PageRequest(2, 2), Optional.empty());

    assertEquals(5, page.total());
    assertEquals(2, page.items().size());
    assertEquals(2, page.page());
    assertEquals(2, page.size());
  }

  @Test
  void list_eventStatus_isDerivedNotStored() {
    // sold == capacity -> derived status must be sold-out, never a stored field.
    FakeEventRepository repo =
        new FakeEventRepository().withEvent(event("e1", "Accra", EventCategory.CONCERT, 10, 10));
    ListEventsService service = new ListEventsService(repo);

    Page<EventView> page = service.list(EventFilter.NONE, PageRequest.defaults(), Optional.empty());

    assertTrue(page.items().get(0).status().equals("sold-out"));
    assertTrue(page.items().get(0).ticketTiers().get(0).soldOut());
  }
}
