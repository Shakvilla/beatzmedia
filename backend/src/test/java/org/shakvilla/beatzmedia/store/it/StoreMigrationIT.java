package org.shakvilla.beatzmedia.store.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import jakarta.inject.Inject;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Migration integration test: verifies the full Flyway migration set (including V955–V957
 * {@code store_item}/{@code license_option}/{@code merch_variant} + the store rows in {@code
 * R__seed_dev_data.sql}) applies cleanly and validates on a fresh database. Store ADD §7 / §12.
 */
@QuarkusTest
@Tag("integration")
class StoreMigrationIT {

  @Inject
  Flyway flyway;

  @Test
  void flyway_migrations_apply_cleanly_and_validate() {
    // Flyway ran at start (quarkus.flyway.migrate-at-start=true).
    assertDoesNotThrow(
        () -> flyway.validate(),
        "Flyway validation must pass: V955-V957 + R__seed must apply on a fresh DB");
  }
}
