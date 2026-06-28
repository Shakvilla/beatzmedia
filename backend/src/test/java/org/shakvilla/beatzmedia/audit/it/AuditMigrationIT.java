package org.shakvilla.beatzmedia.audit.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.adapter.out.persistence.AuditEntryEntity;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Migration integration test for the audit module. Verifies that V941 + V942 apply cleanly on an
 * empty DB and that the {@code audit_entry} table is usable by {@link AuditWriter}. Audit ADD §7 /
 * DoD §11 migration-IT requirement.
 */
@QuarkusTest
@Tag("integration")
class AuditMigrationIT {

  @Inject
  AuditWriter auditWriter;

  @Inject
  EntityManager em;

  @Test
  @Transactional
  void audit_entry_table_exists_and_accepts_insert() {
    AuditEntry entry = new AuditEntry(
        "migration-it-id-1",
        "actor-migration",
        "Migration Test Actor",
        "Migration test action",
        "TestEntity",
        "entity-1",
        AuditType.SETTINGS,
        "migration-it reason",
        Instant.parse("2026-06-27T10:00:00Z"));

    assertDoesNotThrow(() -> auditWriter.append(entry));

    // Force a DB round-trip so the read validates the migrated schema, not the first-level cache.
    em.flush();
    em.clear();

    AuditEntryEntity found = em.find(AuditEntryEntity.class, "migration-it-id-1");
    assertNotNull(found, "Inserted entity should be findable");
    assertEquals("actor-migration", found.actorId);
    assertEquals("Migration Test Actor", found.actorName);
    assertEquals("Migration test action", found.action);
    assertEquals("SETTINGS", found.type);
  }

  @Test
  @Transactional
  void actor_name_column_accepts_null_for_legacy_entries() {
    // Legacy entries (written by WU-IDN-4 before V942) have no actor_name
    AuditEntry legacy = new AuditEntry(
        "migration-it-legacy-1",
        "actor-legacy",
        "Legacy action",
        "LegacyEntity",
        "leg-1",
        AuditType.USER,
        null,
        Instant.parse("2026-06-27T10:01:00Z"));

    assertDoesNotThrow(() -> auditWriter.append(legacy),
        "Legacy entry with null actorName must insert without error");

    // Force a DB round-trip so the read validates the migrated schema, not the first-level cache.
    em.flush();
    em.clear();

    AuditEntryEntity found = em.find(AuditEntryEntity.class, "migration-it-legacy-1");
    assertNotNull(found);
    assertEquals("actor-legacy", found.actorId);
    // actorName is null for legacy entries
    org.junit.jupiter.api.Assertions.assertNull(found.actorName);
  }

  @Test
  @Transactional
  void audit_entry_type_check_constraint_accepts_all_valid_types() {
    int i = 0;
    for (AuditType type : AuditType.values()) {
      AuditEntry entry = new AuditEntry(
          "migration-type-" + i++,
          "actor-type",
          "Action for " + type.name(),
          "Entity",
          "entity-t-" + i,
          type,
          null,
          Instant.now());
      assertDoesNotThrow(() -> auditWriter.append(entry),
          "AuditType." + type.name() + " must be accepted by the CHECK constraint");
    }
  }
}
