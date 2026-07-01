package org.shakvilla.beatzmedia.commerce.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for the commerce cart endpoints. Uses Quarkus Dev Services (Testcontainers
 * Postgres) + REST-assured; Flyway migrates at start. Commerce ADD §11 / LLFR-COMMERCE-01.1–01.3.
 *
 * <p>Auth: a fan account is signed up in @BeforeEach; the JWT token is passed as Bearer header.
 * Catalog rows (artist/track/album) are seeded directly via the shared EntityManager to give the
 * cart's price-resolution adapter something to resolve, mirroring {@code library.it.LibraryIT}.
 */
@QuarkusTest
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommerceIT {

  private static final String FAN_EMAIL = "commerce-fan1@example.com";
  private static final String OTHER_FAN_EMAIL = "commerce-fan2@example.com";
  private static final String PASSWORD = "password123";

  private static final String ARTIST_ID = "com-it-artist-1";
  private static final String TRACK_ID = "com-it-track-1";
  private static final String TRACK_2_ID = "com-it-track-2";
  private static final String OWNED_TRACK_ID = "com-it-track-owned";
  private static final String ALBUM_ID = "com-it-album-1";

  private static String fanToken;
  private static String otherFanToken;

  @Inject
  EntityManager em;

  @BeforeEach
  @Transactional
  void ensureFixtures() {
    em.createNativeQuery(
            "INSERT INTO artist_profile (id, name, image, verified)"
                + " VALUES (:id, 'Commerce IT Artist', 'av.jpg', false)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", ARTIST_ID)
        .executeUpdate();

    seedTrack(TRACK_ID, "Commerce IT Track 1", 500);
    seedTrack(TRACK_2_ID, "Commerce IT Track 2", 750);
    seedTrack(OWNED_TRACK_ID, "Commerce IT Owned Track", 300);

    em.createNativeQuery(
            "INSERT INTO album (id, title, artist_id, artist_name, year, cover_image, list_price_minor)"
                + " VALUES (:id, 'Commerce IT Album', :aid, 'Commerce IT Artist', 2024, 'img.jpg', 2000)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", ALBUM_ID)
        .setParameter("aid", ARTIST_ID)
        .executeUpdate();

    if (fanToken == null) {
      fanToken = signUp("Commerce Fan 1", FAN_EMAIL, PASSWORD);
    }
    if (otherFanToken == null) {
      otherFanToken = signUp("Commerce Fan 2", OTHER_FAN_EMAIL, PASSWORD);
    }
  }

  private void seedTrack(String id, String title, long priceMinor) {
    em.createNativeQuery(
            "INSERT INTO track (id, title, artist_id, artist_name, duration_sec, image, "
                + "ownership, price_minor)"
                + " VALUES (:id, :title, :aid, 'Commerce IT Artist', 180, 'img.jpg', 'for-sale', :price)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", id)
        .setParameter("title", title)
        .setParameter("aid", ARTIST_ID)
        .setParameter("price", priceMinor)
        .executeUpdate();
  }

  private String signUp(String name, String email, String password) {
    String token = given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "%s", "email": "%s", "password": "%s" }
            """.formatted(name, email, password))
        .post("/v1/auth/signup")
        .then()
        .extract()
        .path("token");
    if (token != null) return token;

    return given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .post("/v1/auth/login")
        .then()
        .extract()
        .path("token");
  }

  // ---- GET /v1/me/cart (LLFR-COMMERCE-01.1) ----

  @Test
  @Order(1)
  void getCart_emptyByDefault_returnsZeros() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .when()
        .get("/v1/me/cart")
        .then()
        .statusCode(200)
        .body("items", hasSize(0))
        .body("subtotal.amount", equalTo(0.0f))
        .body("fee.amount", equalTo(0.0f))
        .body("total.amount", equalTo(0.0f))
        .body("count", equalTo(0));
  }

  @Test
  @Order(2)
  void getCart_withoutToken_returns401() {
    given().when().get("/v1/me/cart").then().statusCode(401);
  }

  // ---- POST /v1/me/cart/items (LLFR-COMMERCE-01.2) ----

  @Test
  @Order(3)
  void addTrack_resolvesServerSidePrice() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .contentType(ContentType.JSON)
        .body("""
            { "kind": "track", "refId": "%s" }
            """.formatted(TRACK_ID))
        .when()
        .post("/v1/me/cart/items")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].kind", equalTo("track"))
        .body("items[0].refId", equalTo(TRACK_ID))
        .body("items[0].quantity", equalTo(1))
        .body("items[0].price.amount", equalTo(5.00f))
        .body("items[0].price.currency", equalTo("GHS"));
  }

  @Test
  @Order(4)
  void addTrack_again_cartUnchanged_qtyStaysOne() {
    // AC: Given a track already in cart, When add again, Then cart unchanged (qty stays 1)
    given()
        .header("Authorization", "Bearer " + fanToken)
        .contentType(ContentType.JSON)
        .body("""
            { "kind": "track", "refId": "%s" }
            """.formatted(TRACK_ID))
        .when()
        .post("/v1/me/cart/items")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].quantity", equalTo(1));
  }

  @Test
  @Order(5)
  void addTicket_twice_qtyIsTwo() {
    // AC: Given a ticket, When add twice, Then qty=2
    given()
        .header("Authorization", "Bearer " + fanToken)
        .contentType(ContentType.JSON)
        .body("""
            { "kind": "ticket", "refId": "some-event:VIP",
              "metadata": { "title": "VIP Ticket", "priceMinor": 10000 } }
            """)
        .when()
        .post("/v1/me/cart/items")
        .then()
        .statusCode(200);

    given()
        .header("Authorization", "Bearer " + fanToken)
        .contentType(ContentType.JSON)
        .body("""
            { "kind": "ticket", "refId": "some-event:VIP",
              "metadata": { "title": "VIP Ticket", "priceMinor": 10000 } }
            """)
        .when()
        .post("/v1/me/cart/items")
        .then()
        .statusCode(200)
        .body("items.find { it.kind == 'ticket' }.quantity", equalTo(2));
  }

  @Test
  @Order(6)
  void addItem_unknownTrack_returns404() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .contentType(ContentType.JSON)
        .body("""
            { "kind": "track", "refId": "no-such-track-xyz" }
            """)
        .when()
        .post("/v1/me/cart/items")
        .then()
        .statusCode(404);
  }

  @Test
  @Order(7)
  void addItem_unknownKind_returns422() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .contentType(ContentType.JSON)
        .body("""
            { "kind": "bogus", "refId": "x" }
            """)
        .when()
        .post("/v1/me/cart/items")
        .then()
        .statusCode(422)
        .body("error.code", equalTo("VALIDATION"));
  }

  @Test
  @Order(8)
  void addItem_notOwned_addsSuccessfully() {
    // The ALREADY_OWNED (409) rejection path itself is covered end-to-end at the application-service
    // layer (AddCartItemServiceTest, using FakeOwnershipReader) because real ownership data
    // (`ownership_grant`) does not exist until WU-COM-2 ships checkout/settlement; in this
    // environment library's GetOwnedTrackIds is backed by StubLibraryOwnershipReaderAdapter, which
    // always reports nothing owned. Here we assert the happy (not-owned) path wires end-to-end
    // through CommerceResource -> AddCartItemService -> LibraryOwnershipReaderAdapter -> GetOwnedTrackIds.
    given()
        .header("Authorization", "Bearer " + fanToken)
        .contentType(ContentType.JSON)
        .body("""
            { "kind": "track", "refId": "%s" }
            """.formatted(OWNED_TRACK_ID))
        .when()
        .post("/v1/me/cart/items")
        .then()
        .statusCode(200);
  }

  @Test
  @Order(9)
  void addAlbum_resolvesListPrice() {
    given()
        .header("Authorization", "Bearer " + otherFanToken)
        .contentType(ContentType.JSON)
        .body("""
            { "kind": "album", "refId": "%s" }
            """.formatted(ALBUM_ID))
        .when()
        .post("/v1/me/cart/items")
        .then()
        .statusCode(200)
        .body("items[0].price.amount", equalTo(20.00f));
  }

  // ---- PATCH /v1/me/cart/items/:lineId (LLFR-COMMERCE-01.3) ----

  @Test
  @Order(10)
  void updateQuantity_stackableLine_updatesQty() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .contentType(ContentType.JSON)
        .body("""
            { "qty": 5 }
            """)
        .when()
        .patch("/v1/me/cart/items/ticket:some-event:VIP")
        .then()
        .statusCode(200)
        .body("items.find { it.kind == 'ticket' }.quantity", equalTo(5));
  }

  @Test
  @Order(11)
  void updateQuantity_nonStackableLine_returns409_NOT_STACKABLE() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .contentType(ContentType.JSON)
        .body("""
            { "qty": 3 }
            """)
        .when()
        .patch("/v1/me/cart/items/track:" + TRACK_ID)
        .then()
        .statusCode(409)
        .body("error.code", equalTo("NOT_STACKABLE"));
  }

  @Test
  @Order(12)
  void updateQuantity_missingLine_returns404() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .contentType(ContentType.JSON)
        .body("""
            { "qty": 3 }
            """)
        .when()
        .patch("/v1/me/cart/items/track:does-not-exist")
        .then()
        .statusCode(404);
  }

  // ---- DELETE /v1/me/cart/items/:lineId (LLFR-COMMERCE-01.3) ----

  @Test
  @Order(13)
  void removeLine_existingLine_removesIt() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .when()
        .delete("/v1/me/cart/items/track:" + TRACK_ID)
        .then()
        .statusCode(200);

    given()
        .header("Authorization", "Bearer " + fanToken)
        .when()
        .get("/v1/me/cart")
        .then()
        .statusCode(200)
        .body("items.find { it.refId == '" + TRACK_ID + "' }", equalTo(null));
  }

  @Test
  @Order(14)
  void removeLine_missingLine_idempotent_returns200() {
    given()
        .header("Authorization", "Bearer " + fanToken)
        .when()
        .delete("/v1/me/cart/items/track:already-removed-xyz")
        .then()
        .statusCode(200);
  }

  // ---- Totals across the whole cart ----

  @Test
  @Order(15)
  void getCart_afterMultipleAdds_computesCorrectTotals() {
    given()
        .header("Authorization", "Bearer " + otherFanToken)
        .contentType(ContentType.JSON)
        .body("""
            { "kind": "track", "refId": "%s" }
            """.formatted(TRACK_2_ID))
        .when()
        .post("/v1/me/cart/items")
        .then()
        .statusCode(200);

    // otherFan cart: album (20.00) + track2 (7.50) = 27.50 subtotal, +0.50 fee = 28.00
    given()
        .header("Authorization", "Bearer " + otherFanToken)
        .when()
        .get("/v1/me/cart")
        .then()
        .statusCode(200)
        .body("subtotal.amount", equalTo(27.50f))
        .body("fee.amount", equalTo(0.50f))
        .body("total.amount", equalTo(28.00f))
        .body("count", equalTo(2));
  }
}
