package org.shakvilla.beatzmedia.events.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end integration for WU-EVT-1 (LLFR-EVENTS-01.1 – 01.2). Testcontainers Postgres +
 * REST-assured against the real events repository. Proves INV-EVT-2 (status/soldOut are derived
 * from live tier availability, never a stored display string) and the DoD #5 dev-seed fixture
 * (afro-nation-gh sold-out). Events ADD §11.
 */
@QuarkusTest
@Tag("integration")
class EventsFlowIT {

  @Inject EntityManager em;

  private String eventId;
  private String tierRegularId;
  private String tierVipId;

  @BeforeEach
  @Transactional
  void seed() {
    long n = System.nanoTime();
    eventId = "evt-it-" + n;
    tierRegularId = eventId + "-regular";
    tierVipId = eventId + "-vip";

    em.createNativeQuery(
            "INSERT INTO event (id, title, artist_name, image, event_at, venue, city, category,"
                + " popularity) VALUES (:id, 'IT Event', 'IT Artist', 'img.png', now() + interval"
                + " '30 days', 'IT Venue', 'ITCity', 'Concert', 42) ON CONFLICT (id) DO NOTHING")
        .setParameter("id", eventId)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO ticket_tier (id, event_id, name, price_minor, capacity, sold)"
                + " VALUES (:id, :eventId, 'Regular', 15000, 1000, 10) ON CONFLICT (id) DO NOTHING")
        .setParameter("id", tierRegularId)
        .setParameter("eventId", eventId)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO ticket_tier (id, event_id, name, price_minor, capacity, sold)"
                + " VALUES (:id, :eventId, 'VIP', 40000, 20, 20) ON CONFLICT (id) DO NOTHING")
        .setParameter("id", tierVipId)
        .setParameter("eventId", eventId)
        .executeUpdate();
  }

  // ---- LLFR-EVENTS-01.1: browse events -------------------------------------------------------

  @Test
  void listEvents_noFilter_returnsPagedEvents() {
    given()
        .when()
        .get("/v1/events")
        .then()
        .statusCode(200)
        .body("items", notNullValue())
        .body("total", greaterThanOrEqualTo(1))
        .body("page", equalTo(1))
        .body("size", equalTo(20));
  }

  @Test
  void listEvents_cityFilter_returnsOnlyMatchingEvents() {
    given()
        .queryParam("city", "ITCity")
        .when()
        .get("/v1/events")
        .then()
        .statusCode(200)
        .body("items.city", everyItem(equalTo("ITCity")))
        .body("items.id", org.hamcrest.Matchers.hasItem(eventId));
  }

  @Test
  void listEvents_categoryFilter_returnsOnlyMatchingEvents() {
    given()
        .queryParam("city", "ITCity")
        .queryParam("category", "Concert")
        .when()
        .get("/v1/events")
        .then()
        .statusCode(200)
        .body("items.category", everyItem(equalTo("Concert")));
  }

  @Test
  void listEvents_invalidCategory_returns422() {
    given()
        .queryParam("category", "Not A Real Category")
        .when()
        .get("/v1/events")
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"));
  }

  @Test
  void listEvents_pagination_respectsPageAndSize() {
    given()
        .queryParam("page", 1)
        .queryParam("size", 1)
        .when()
        .get("/v1/events")
        .then()
        .statusCode(200)
        .body("items.size()", equalTo(1))
        .body("size", equalTo(1));
  }

  // ---- LLFR-EVENTS-01.2: event detail ---------------------------------------------------------

  @Test
  void getEvent_knownEvent_returns200_withTicketTiers() {
    given()
        .when()
        .get("/v1/events/" + eventId)
        .then()
        .statusCode(200)
        .body("id", equalTo(eventId))
        .body("title", equalTo("IT Event"))
        .body("ticketTiers.size()", equalTo(2));
  }

  @Test
  void getEvent_unknownEvent_returns404() {
    given()
        .when()
        .get("/v1/events/does-not-exist-" + System.nanoTime())
        .then()
        .statusCode(404)
        .body("error.code", equalTo("NOT_FOUND"));
  }

  // ---- INV-EVT-2: derived soldOut / status -----------------------------------------------------

  @Test
  void getEvent_tierAtCapacity_soldOutTrue_otherTierNotSoldOut() {
    given()
        .when()
        .get("/v1/events/" + eventId)
        .then()
        .statusCode(200)
        .body("find { it.name == 'VIP' }.soldOut", equalTo(true))
        .body("find { it.name == 'Regular' }.soldOut", equalTo(false));
  }

  // ---- Dev-seed fixture (Events ADD DoD #5) -----------------------------------------------------

  @Test
  void devSeed_afroNationGh_isSoldOut_bothTiersSoldOut() {
    given()
        .when()
        .get("/v1/events/afro-nation-gh")
        .then()
        .statusCode(200)
        .body("status", equalTo("sold-out"))
        .body("ticketTiers.soldOut", everyItem(equalTo(true)));
  }

  @Test
  void devSeed_ironBoyLive_isSellingFast() {
    given()
        .when()
        .get("/v1/events/iron-boy-live")
        .then()
        .statusCode(200)
        .body("status", equalTo("selling-fast"));
  }
}
