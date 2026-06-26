package org.shakvilla.beatzmedia.catalog.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import jakarta.inject.Inject;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Migration integration test: verifies that the Flyway migration set (V301–V303 + R__ seed)
 * applies cleanly and validates on a fresh database. Catalog ADD §9 / data-and-migrations §9.
 */
@QuarkusTest
@Tag("integration")
class CatalogMigrationIT {

  @Inject
  Flyway flyway;

  @Test
  void flyway_migrations_apply_cleanly_and_validate() {
    // Flyway ran at start (quarkus.flyway.migrate-at-start=true).
    // Calling validate() ensures no checksum drift, no out-of-order migrations, no failed ones.
    assertDoesNotThrow(() -> flyway.validate(),
        "Flyway validation must pass: V301/V302/V303 + R__seed must apply on a fresh DB");
  }
}
