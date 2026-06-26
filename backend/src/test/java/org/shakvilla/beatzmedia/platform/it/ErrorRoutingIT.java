package org.shakvilla.beatzmedia.platform.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end checks that framework-thrown JAX-RS errors keep their real HTTP status and the uniform
 * error envelope, instead of being swallowed into 500 by the catch-all {@code
 * FallbackExceptionMapper}. Regression for the 404/405 → 500 bug; proves {@code
 * WebApplicationExceptionMapper} is the mapper the runtime selects for these exceptions.
 *
 * <p>Conventions §4 / API-CONTRACT.md §1.
 */
@QuarkusTest
@Tag("integration")
class ErrorRoutingIT {

  @Test
  void unknownRoute_returns_404_with_NOT_FOUND_envelope() {
    given()
        .accept("application/json")
        .when()
        .get("/v1/this-route-does-not-exist")
        .then()
        .statusCode(404)
        .body("error.code", equalTo("NOT_FOUND"));
  }

  @Test
  void wrongMethod_on_existing_route_returns_405_with_METHOD_NOT_ALLOWED_envelope() {
    // /v1/auth/login is POST-only; DELETE must yield 405, not 500.
    given()
        .accept("application/json")
        .when()
        .delete("/v1/auth/login")
        .then()
        .statusCode(405)
        .body("error.code", equalTo("METHOD_NOT_ALLOWED"));
  }
}
