package org.shakvilla.beatzmedia.platform.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(CorsPolicyIT.CorsProfile.class)
class CorsPolicyIT {

  private static final String APPROVED_ORIGIN = "https://app.beatzclik.test";

  @ParameterizedTest
  @MethodSource("supportedMethods")
  void acceptsPreflightForEverySupportedMethod(String method) {
    given()
        .header("Origin", APPROVED_ORIGIN)
        .header("Access-Control-Request-Method", method)
        .header("Access-Control-Request-Headers", "Authorization,Content-Type,Idempotency-Key")
        .when()
        .options("/v1/catalog/tracks")
        .then()
        .statusCode(200)
        .header("Access-Control-Allow-Origin", equalTo(APPROVED_ORIGIN))
        .header("Access-Control-Allow-Methods", containsString(method))
        .header("Access-Control-Allow-Credentials", equalTo("true"));
  }

  @Test
  void permitsAnApprovedCrossOriginPutToReachApplicationSecurity() {
    given()
        .header("Origin", APPROVED_ORIGIN)
        .contentType("application/json")
        .body("{}")
        .when()
        .put("/v1/admin/settings")
        .then()
        .statusCode(not(equalTo(403)))
        .header("Access-Control-Allow-Origin", equalTo(APPROVED_ORIGIN));
  }

  @Test
  void rejectsAnUnapprovedProductionOrigin() {
    given()
        .header("Origin", "https://attacker.example")
        .header("Access-Control-Request-Method", "PUT")
        .when()
        .options("/v1/admin/settings")
        .then()
        .statusCode(403)
        .header("Access-Control-Allow-Origin", not(equalTo("https://attacker.example")));
  }

  static Stream<String> supportedMethods() {
    return Stream.of("GET", "POST", "PUT", "PATCH", "DELETE");
  }

  public static final class CorsProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "quarkus.http.cors.origins", APPROVED_ORIGIN,
          "quarkus.http.cors.access-control-allow-credentials", "true");
    }
  }
}
