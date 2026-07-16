package org.shakvilla.beatzmedia.commerce.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Contract conformance test: validates that the cart response matches the {@code CartItem} /
 * cart response shape in {@code Frontend/src/types/index.ts} and API-CONTRACT.md §6 — field
 * names, money shape {@code {amount, currency}}, quantity as an integer, and the uniform error
 * envelope. Commerce ADD §11 / testing-strategy §5.
 */
@QuarkusTest
@Tag("integration")
class CommerceContractTest {

  private static final String ARTIST_ID = "com-contract-artist-1";
  private static final String TRACK_ID = "com-contract-track-1";

  @Inject
  EntityManager em;

  @Test
  void cart_response_has_required_top_level_fields() {
    String token = fan();

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/me/cart")
        .then()
        .statusCode(200)
        .body("items", notNullValue())
        .body("subtotal", notNullValue())
        .body("subtotal.amount", isA(Number.class))
        .body("subtotal.currency", equalTo("GHS"))
        .body("fee", notNullValue())
        .body("fee.currency", equalTo("GHS"))
        .body("total", notNullValue())
        .body("total.currency", equalTo("GHS"))
        .body("count", isA(Integer.class));
  }

  @Test
  void cart_item_response_has_required_fields_matching_frontend_CartItem() {
    String token = fan();
    seedTrack();

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "kind": "track", "refId": "%s" }
            """.formatted(TRACK_ID))
        .when()
        .post("/v1/me/cart/items")
        .then()
        .statusCode(200)
        .body("items[0].id", isA(String.class))
        .body("items[0].kind", equalTo("track"))
        .body("items[0].title", isA(String.class))
        .body("items[0].image", isA(String.class))
        .body("items[0].price.amount", isA(Number.class))
        .body("items[0].price.currency", equalTo("GHS"))
        .body("items[0].quantity", isA(Integer.class))
        .body("items[0].stackable", isA(Boolean.class));
  }

  @Test
  void order_response_has_required_fields_matching_frontend_OrderSnapshot() {
    String token = fan();
    seedTrack();

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "kind": "track", "refId": "%s" }
            """.formatted(TRACK_ID))
        .when()
        .post("/v1/me/cart/items")
        .then()
        .statusCode(200);

    String orderId =
        given()
            .header("Authorization", "Bearer " + token)
            .header("Idempotency-Key", "contract-order-key-" + System.nanoTime())
            .contentType(ContentType.JSON)
            .body("""
                { "paymentMethodId": "mtn" }
                """)
            .when()
            .post("/v1/checkout")
            .then()
            .statusCode(202)
            // WU-COM-4: the checkout response carries checkoutUrl (nullable) — present-but-null on the
            // MoMo/sandbox path, non-null only for a Redde card redirect.
            .body("checkoutUrl", nullValue())
            .extract()
            .jsonPath()
            .getString("orderId");

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/me/orders/" + orderId)
        .then()
        .statusCode(200)
        .body("orderId", isA(String.class))
        .body("reference", isA(String.class))
        .body("status", isA(String.class))
        .body("subtotal.amount", isA(Number.class))
        .body("subtotal.currency", equalTo("GHS"))
        .body("fee.amount", isA(Number.class))
        .body("total.amount", isA(Number.class))
        .body("items[0].id", isA(String.class))
        .body("items[0].kind", equalTo("track"))
        .body("items[0].refId", equalTo(TRACK_ID))
        .body("items[0].title", isA(String.class))
        .body("items[0].unitPrice.amount", isA(Number.class))
        .body("items[0].unitPrice.currency", equalTo("GHS"))
        .body("items[0].quantity", isA(Integer.class));
  }

  @Test
  void error_envelope_has_correct_structure_on_validation_failure() {
    String token = fan();

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "kind": "not-a-real-kind", "refId": "x" }
            """)
        .when()
        .post("/v1/me/cart/items")
        .then()
        .statusCode(422)
        .body("error", notNullValue())
        .body("error.code", equalTo("VALIDATION"))
        .body("error.message", isA(String.class));
  }

  @Test
  void not_stackable_error_code_conforms_to_contract() {
    String token = fan();
    seedTrack();

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "kind": "track", "refId": "%s" }
            """.formatted(TRACK_ID))
        .when()
        .post("/v1/me/cart/items")
        .then()
        .statusCode(200);

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "qty": 3 }
            """)
        .when()
        .patch("/v1/me/cart/items/track:" + TRACK_ID)
        .then()
        .statusCode(409)
        .body("error.code", equalTo("NOT_STACKABLE"))
        .body("error.message", isA(String.class));
  }

  // ---- helpers ----

  private String fan() {
    String email = "com-contract-fan-" + System.nanoTime() + "@example.com";
    String password = "password123";
    given()
        .contentType(ContentType.JSON)
        .body("""
            { "name": "Contract Fan", "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when().post("/v1/auth/signup")
        .then().statusCode(201);

    return given()
        .contentType(ContentType.JSON)
        .body("""
            { "email": "%s", "password": "%s" }
            """.formatted(email, password))
        .when().post("/v1/auth/login")
        .then().statusCode(200)
        .extract().jsonPath().getString("token");
  }

  @Transactional
  void seedTrack() {
    em.createNativeQuery(
            "INSERT INTO artist_profile (id, name, image, verified)"
                + " VALUES (:aid, 'Contract Artist', 'av.jpg', false)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("aid", ARTIST_ID)
        .executeUpdate();
    em.createNativeQuery(
            "INSERT INTO track (id, title, artist_id, artist_name, duration_sec, image, "
                + "ownership, price_minor)"
                + " VALUES (:id, 'Contract Track', :aid, 'Contract Artist', 180, 'img.jpg', "
                + "'for-sale', 500)"
                + " ON CONFLICT (id) DO NOTHING")
        .setParameter("id", TRACK_ID)
        .setParameter("aid", ARTIST_ID)
        .executeUpdate();
  }
}
