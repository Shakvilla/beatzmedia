package org.shakvilla.beatzmedia.discovery.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@Tag("integration")
class PersonalizedRecommendationsIT {

  @Test
  void recommendationsRequireAuthenticationAndUseTheJwtSubject() {
    given().when().get("/v1/me/recommendations").then().statusCode(401);

    String email = "recommendations-" + System.nanoTime() + "@example.com";
    given()
        .contentType(ContentType.JSON)
        .body(
            "{\"name\":\"Discovery Fan\",\"email\":\"%s\",\"password\":\"password123\"}"
                .formatted(email))
        .when()
        .post("/v1/auth/signup")
        .then()
        .statusCode(201);
    String token =
        given()
            .contentType(ContentType.JSON)
            .body(
                "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email))
            .when()
            .post("/v1/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .path("token");

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/me/recommendations?limit=500")
        .then()
        .statusCode(200)
        .body("items.size()", lessThanOrEqualTo(24));
  }
}
