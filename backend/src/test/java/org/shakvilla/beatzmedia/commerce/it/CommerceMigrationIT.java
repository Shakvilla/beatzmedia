package org.shakvilla.beatzmedia.commerce.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import jakarta.inject.Inject;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Migration integration test: verifies the Flyway migration set (including V943 cart/cart_item)
 * applies cleanly and validates on a fresh database. Commerce ADD §9 / data-and-migrations §9.
 */
@QuarkusTest
@Tag("integration")
class CommerceMigrationIT {

  @Inject
  Flyway flyway;

  @Test
  void flyway_migrations_apply_cleanly_and_validate() {
    assertDoesNotThrow(() -> flyway.validate(),
        "Flyway validation must pass: V943 (cart, cart_item) must apply on a fresh DB");
  }
}
