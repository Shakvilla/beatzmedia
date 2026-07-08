package org.shakvilla.beatzmedia.events.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.events.application.port.in.EventView;
import org.shakvilla.beatzmedia.events.application.service.GetEventService;
import org.shakvilla.beatzmedia.events.domain.Event;
import org.shakvilla.beatzmedia.events.domain.EventCategory;
import org.shakvilla.beatzmedia.events.domain.EventId;
import org.shakvilla.beatzmedia.events.domain.EventNotFoundException;
import org.shakvilla.beatzmedia.events.domain.TicketTier;
import org.shakvilla.beatzmedia.events.domain.TicketTierId;
import org.shakvilla.beatzmedia.events.fakes.FakeEventRepository;

/** Unit tests for {@link GetEventService} — LLFR-EVENTS-01.2 (event detail, 404). */
@Tag("unit")
class GetEventServiceTest {

  @Test
  void get_knownEvent_returnsView() {
    Event event =
        new Event(
            new EventId("iron-boy-live"),
            "Iron Boy Live",
            "Black Sherif",
            "black-sherif",
            List.of("Lasmid", "Camidoh"),
            "img.png",
            Instant.parse("2026-07-09T19:00:00Z"),
            "7:00 PM",
            "Independence Square",
            "Accra",
            "Greater Accra",
            EventCategory.CONCERT,
            "desc",
            "All ages",
            99,
            List.of(
                new TicketTier(
                    new TicketTierId("iron-boy-live-regular"),
                    new EventId("iron-boy-live"),
                    "Regular",
                    15000,
                    List.of("General standing"),
                    500,
                    480)));
    FakeEventRepository repo = new FakeEventRepository().withEvent(event);
    GetEventService service = new GetEventService(repo);

    EventView view = service.get(new EventId("iron-boy-live"), Optional.empty());

    assertEquals("iron-boy-live", view.id());
    assertEquals("Iron Boy Live", view.title());
    assertEquals("Concert", view.category());
    assertEquals(1, view.ticketTiers().size());
    assertEquals("Regular", view.ticketTiers().get(0).name());
    assertEquals(150.00, view.ticketTiers().get(0).price().amount().doubleValue());
    assertEquals("GHS", view.ticketTiers().get(0).price().currency());
    // remaining = 20 <= low-stock threshold -> selling-fast (INV-EVT-2), not a stored string.
    assertEquals("selling-fast", view.status());
    assertTrue(!view.ticketTiers().get(0).soldOut());
  }

  @Test
  void get_unknownEvent_throwsNotFound() {
    FakeEventRepository repo = new FakeEventRepository();
    GetEventService service = new GetEventService(repo);

    assertThrows(
        EventNotFoundException.class,
        () -> service.get(new EventId("does-not-exist"), Optional.empty()));
  }

  @Test
  void get_allTiersSoldOut_statusIsSoldOut() {
    Event event =
        new Event(
            new EventId("afro-nation-gh"),
            "Afro Nation Ghana",
            "Multiple Artists",
            null,
            List.of(),
            "img.png",
            Instant.parse("2026-10-10T15:00:00Z"),
            null,
            "Venue",
            "Aburi",
            null,
            EventCategory.FESTIVAL,
            null,
            "18+",
            91,
            List.of(
                new TicketTier(
                    new TicketTierId("afro-nation-gh-general"),
                    new EventId("afro-nation-gh"),
                    "General",
                    50000,
                    List.of(),
                    5000,
                    5000),
                new TicketTier(
                    new TicketTierId("afro-nation-gh-vip"),
                    new EventId("afro-nation-gh"),
                    "VIP",
                    150000,
                    List.of(),
                    1000,
                    1000)));
    FakeEventRepository repo = new FakeEventRepository().withEvent(event);
    GetEventService service = new GetEventService(repo);

    EventView view = service.get(new EventId("afro-nation-gh"), Optional.empty());

    assertEquals("sold-out", view.status());
    assertTrue(view.ticketTiers().stream().allMatch(t -> t.soldOut()));
  }
}
