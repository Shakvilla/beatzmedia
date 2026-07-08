package org.shakvilla.beatzmedia.events.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Contract conformance test: validates {@code EventDto} / {@code TicketTierDto} / the uniform
 * error envelope against {@code API-CONTRACT.md} §9 and {@code Frontend/src/types/index.ts}
 * ({@code Event}, {@code TicketTier}). Events ADD §6 / §11.
 *
 * <ul>
 *   <li>{@code Event}: {@code id, title, artistName, artistId, lineup, image, date, doorsTime,
 *       venue, city, region, status, category, description, ticketTiers, popularity,
 *       ageRestriction}.
 *   <li>{@code TicketTier}: {@code name}, money {@code price: { amount, currency }}, {@code
 *       perks}, {@code soldOut} — {@code capacity}/{@code sold} are NEVER serialized.
 *   <li>Unknown resource → the uniform error envelope {@code { error: { code, message } } }.
 * </ul>
 */
@QuarkusTest
@Tag("integration")
class EventsContractTest {

  @Inject EntityManager em;

  private String eventId;
  private String tierId;

  @BeforeEach
  @Transactional
  void seed() {
    long n = System.nanoTime();
    eventId = "evt-c-" + n;
    tierId = eventId + "-regular";

    em.createNativeQuery(
            "INSERT INTO event (id, title, artist_name, artist_id, lineup, image, event_at,"
                + " doors_time, venue, city, region, category, description, age_restriction,"
                + " popularity) VALUES (:id, 'Contract Event', 'Contract Artist', 'artist-c',"
                + " '[\"Support Act\"]'::jsonb, 'img.png', now() + interval '10 days', '7:00 PM',"
                + " 'Contract Venue', 'Accra', 'Greater Accra', 'Concert', 'desc', 'All ages', 77)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", eventId)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO ticket_tier (id, event_id, name, price_minor, capacity, sold, perks)"
                + " VALUES (:id, :eventId, 'Regular', 15000, 100, 10, '[\"Fast-track entry\"]'::jsonb)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", tierId)
        .setParameter("eventId", eventId)
        .executeUpdate();
  }

  // ---- Page<EventDto> --------------------------------------------------------------------------

  @Test
  void listEvents_matchesPageEnvelope() {
    given()
        .when()
        .get("/v1/events")
        .then()
        .statusCode(200)
        .body("items", notNullValue())
        .body("page", isA(Integer.class))
        .body("size", isA(Integer.class))
        .body("total", isA(Integer.class));
  }

  // ---- EventDto ----------------------------------------------------------------------------------

  @Test
  void getEvent_matchesEventDtoShape() {
    given()
        .when()
        .get("/v1/events/" + eventId)
        .then()
        .statusCode(200)
        .body("id", equalTo(eventId))
        .body("title", isA(String.class))
        .body("artistName", equalTo("Contract Artist"))
        .body("artistId", equalTo("artist-c"))
        .body("lineup", isA(java.util.List.class))
        .body("image", isA(String.class))
        .body("date", isA(String.class))
        .body("doorsTime", equalTo("7:00 PM"))
        .body("venue", equalTo("Contract Venue"))
        .body("city", equalTo("Accra"))
        .body("region", equalTo("Greater Accra"))
        .body("status", isA(String.class))
        .body("category", equalTo("Concert"))
        .body("description", equalTo("desc"))
        .body("popularity", equalTo(77))
        .body("ageRestriction", equalTo("All ages"));
  }

  // ---- TicketTierDto — money shape, capacity/sold NEVER serialized -----------------------------

  @Test
  void getEvent_ticketTierDto_hasMoneyPrice_andSoldOut_noInternalCounters() {
    given()
        .when()
        .get("/v1/events/" + eventId)
        .then()
        .statusCode(200)
        .body("ticketTiers[0].name", equalTo("Regular"))
        .body("ticketTiers[0].price.amount", isA(Float.class))
        .body("ticketTiers[0].price.currency", equalTo("GHS"))
        .body("ticketTiers[0].perks", isA(java.util.List.class))
        .body("ticketTiers[0].soldOut", equalTo(false))
        .body("ticketTiers[0]", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("capacity")))
        .body("ticketTiers[0]", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("sold")))
        .body("ticketTiers[0]", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("id")));
  }

  // ---- Uniform error envelope --------------------------------------------------------------------

  @Test
  void getEventUnknown_returnsUniformErrorEnvelope() {
    given()
        .when()
        .get("/v1/events/no-such-event-" + System.nanoTime())
        .then()
        .statusCode(404)
        .body("error.code", equalTo("NOT_FOUND"))
        .body("error.message", notNullValue());
  }
}
