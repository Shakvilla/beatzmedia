package org.shakvilla.beatzmedia.audit.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.adapter.in.rest.AuditEntryDto;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;

/**
 * Contract test: verifies {@link AuditEntryDto} matches the {@code AuditEntry} type in
 * {@code Frontend/src/lib/admin-data.ts} and API-CONTRACT §13.
 *
 * <pre>
 * AuditEntry { id, actor, action, target, type, time }
 * type: 'user' | 'catalog' | 'finance' | 'moderation' | 'settings' | 'editorial'
 * </pre>
 *
 * Audit ADD §6.2 / DoD §11 contract-test requirement.
 */
@Tag("unit")
class AuditEntryContractTest {

  private static final Set<String> VALID_TYPES =
      Set.of("user", "catalog", "finance", "moderation", "settings", "editorial");

  @Test
  void dto_has_all_required_fields() {
    AuditEntry entry = new AuditEntry(
        "id-1", "actor-id", "Alice", "Invited admin",
        "AdminMember", "m-1", AuditType.SETTINGS, null,
        Instant.parse("2026-06-27T10:00:00Z"));

    AuditEntryDto dto = AuditEntryDto.from(entry);

    assertNotNull(dto.id(), "id must not be null");
    assertNotNull(dto.actor(), "actor must not be null");
    assertNotNull(dto.action(), "action must not be null");
    assertNotNull(dto.target(), "target must not be null");
    assertNotNull(dto.type(), "type must not be null");
    assertNotNull(dto.time(), "time must not be null");
  }

  @Test
  void dto_field_names_match_contract() throws Exception {
    var components = AuditEntryDto.class.getRecordComponents();
    var names = new java.util.HashSet<String>();
    for (var c : components) {
      names.add(c.getName());
    }
    assertTrue(names.contains("id"), "must have 'id' field");
    assertTrue(names.contains("actor"), "must have 'actor' field");
    assertTrue(names.contains("action"), "must have 'action' field");
    assertTrue(names.contains("target"), "must have 'target' field");
    assertTrue(names.contains("type"), "must have 'type' field");
    assertTrue(names.contains("time"), "must have 'time' field");
  }

  @Test
  void actor_uses_actorName_when_present() {
    AuditEntry entry = new AuditEntry(
        "id-1", "actor-id", "Alice", "Action",
        "Tgt", "t-1", AuditType.USER, null, Instant.now());
    AuditEntryDto dto = AuditEntryDto.from(entry);
    assertEquals("Alice", dto.actor());
  }

  @Test
  void actor_falls_back_to_actorId_when_actorName_is_null() {
    AuditEntry entry = new AuditEntry(
        "id-1", "actor-id", "Action",
        "Tgt", "t-1", AuditType.USER, null, Instant.now());
    AuditEntryDto dto = AuditEntryDto.from(entry);
    assertEquals("actor-id", dto.actor());
  }

  @Test
  void target_is_compound_type_colon_id() {
    AuditEntry entry = new AuditEntry(
        "id-1", "actor-id", "Alice", "Action",
        "AdminMember", "m-99", AuditType.SETTINGS, null, Instant.now());
    AuditEntryDto dto = AuditEntryDto.from(entry);
    assertEquals("AdminMember:m-99", dto.target());
  }

  @Test
  void type_is_lowercase() {
    for (AuditType at : AuditType.values()) {
      AuditEntry entry = new AuditEntry(
          "id", "actor", "Alice", "Action",
          "T", "t", at, null, Instant.now());
      AuditEntryDto dto = AuditEntryDto.from(entry);
      assertTrue(VALID_TYPES.contains(dto.type()),
          "AuditType." + at.name() + " must map to a valid lowercase wire value, got: "
              + dto.type());
    }
  }

  @Test
  void time_is_iso8601() {
    Instant now = Instant.parse("2026-06-27T10:00:00Z");
    AuditEntry entry = new AuditEntry(
        "id-1", "actor-id", "Alice", "Action",
        "T", "t", AuditType.USER, null, now);
    AuditEntryDto dto = AuditEntryDto.from(entry);
    assertEquals("2026-06-27T10:00:00Z", dto.time());
  }
}
