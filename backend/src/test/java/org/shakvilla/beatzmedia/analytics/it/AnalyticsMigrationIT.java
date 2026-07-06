package org.shakvilla.beatzmedia.analytics.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import jakarta.inject.Inject;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Migration integration test: verifies the full Flyway migration set (including V949
 * {@code sales_rollup}/{@code audience_rollup} + the four {@code analytics_*_fact} staging tables)
 * applies cleanly and validates on a fresh database. Analytics ADD §7 / data-and-migrations §9.
 */
@QuarkusTest
@Tag("integration")
class AnalyticsMigrationIT {

  @Inject
  Flyway flyway;

  @Test
  void flyway_migrations_apply_cleanly_and_validate() {
    assertDoesNotThrow(
        () -> flyway.validate(), "Flyway validation must pass: V949 must apply on a fresh DB");
  }
}
