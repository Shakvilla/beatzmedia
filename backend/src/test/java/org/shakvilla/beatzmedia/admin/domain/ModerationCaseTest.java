package org.shakvilla.beatzmedia.admin.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link ModerationCase} aggregate. Pure Java, no fakes needed.
 * Testing-strategy §2 / admin ADD §3 (LLFR-ADMIN-04.1).
 */
@Tag("unit")
class ModerationCaseTest {

  private static final Instant NOW = Instant.parse("2026-07-11T10:00:00Z");

  private ModerationCase newOpenCase() {
    return ModerationCase.open(
        "m1", "release:r1", "admin-1", ModReason.COPYRIGHT, ModSeverity.MED, NOW);
  }

  @Test
  void open_stamps_sla_due_at_six_hours_after_created_at() {
    ModerationCase c = newOpenCase();
    assertEquals(ModStatus.OPEN, c.getStatus());
    assertEquals(NOW, c.getCreatedAt());
    assertEquals(NOW.plusSeconds(ModerationCase.DEFAULT_SLA_HOURS * 3600L), c.getSlaDueAt());
    assertFalse(c.isEscalated());
  }

  @Test
  void review_moves_open_to_in_review() {
    ModerationCase c = newOpenCase();
    c.review();
    assertEquals(ModStatus.IN_REVIEW, c.getStatus());
  }

  @Test
  void approve_resolves_the_case() {
    ModerationCase c = newOpenCase();
    c.approve();
    assertEquals(ModStatus.RESOLVED, c.getStatus());
  }

  @Test
  void remove_resolves_the_case() {
    ModerationCase c = newOpenCase();
    c.remove();
    assertEquals(ModStatus.RESOLVED, c.getStatus());
  }

  @Test
  void dismiss_resolves_the_case() {
    ModerationCase c = newOpenCase();
    c.dismiss();
    assertEquals(ModStatus.RESOLVED, c.getStatus());
  }

  @Test
  void escalate_sets_flag_without_changing_status() {
    ModerationCase c = newOpenCase();
    c.escalate();
    assertTrue(c.isEscalated());
    assertEquals(ModStatus.OPEN, c.getStatus(), "escalate is orthogonal to the queue status");
  }

  @Test
  void escalate_twice_throws_IllegalModerationTransitionException() {
    ModerationCase c = newOpenCase();
    c.escalate();
    assertThrows(IllegalModerationTransitionException.class, c::escalate);
  }

  @Test
  void any_action_on_a_resolved_case_throws_IllegalModerationTransitionException() {
    ModerationCase c = newOpenCase();
    c.approve();
    assertThrows(IllegalModerationTransitionException.class, c::review);
    assertThrows(IllegalModerationTransitionException.class, c::approve);
    assertThrows(IllegalModerationTransitionException.class, c::remove);
    assertThrows(IllegalModerationTransitionException.class, c::dismiss);
    assertThrows(IllegalModerationTransitionException.class, c::escalate);
  }

  @Test
  void mod_reason_wire_values_match_admin_data_ts() {
    assertEquals("Copyright", ModReason.COPYRIGHT.wireValue());
    assertEquals("Hate speech", ModReason.HATE_SPEECH.wireValue());
    assertEquals("Sexual content", ModReason.SEXUAL_CONTENT.wireValue());
    assertEquals("Spam", ModReason.SPAM.wireValue());
    assertEquals("Impersonation", ModReason.IMPERSONATION.wireValue());
  }

  @Test
  void mod_status_from_wire_value_null_means_no_filter() {
    assertEquals(null, ModStatus.fromWireValue(null));
    assertEquals(null, ModStatus.fromWireValue(""));
    assertEquals(ModStatus.IN_REVIEW, ModStatus.fromWireValue("in_review"));
  }

  @Test
  void mod_status_from_invalid_wire_value_throws_validation() {
    assertThrows(
        org.shakvilla.beatzmedia.platform.domain.ValidationException.class,
        () -> ModStatus.fromWireValue("bogus"));
  }

  @Test
  void mod_reason_from_invalid_wire_value_throws_validation() {
    assertThrows(
        org.shakvilla.beatzmedia.platform.domain.ValidationException.class,
        () -> ModReason.fromWireValue("bogus"));
  }

  @Test
  void catalog_filter_from_wire_value_null_means_no_filter() {
    assertEquals(null, CatalogFilter.fromWireValue(null));
    assertEquals(CatalogFilter.PENDING, CatalogFilter.fromWireValue("pending"));
    assertEquals(CatalogFilter.PUBLISHED, CatalogFilter.fromWireValue("published"));
    assertEquals(CatalogFilter.TAKEDOWN, CatalogFilter.fromWireValue("takedown"));
  }

  @Test
  void catalog_filter_from_invalid_wire_value_throws_validation() {
    assertThrows(
        org.shakvilla.beatzmedia.platform.domain.ValidationException.class,
        () -> CatalogFilter.fromWireValue("bogus"));
  }

  @Test
  void constructing_with_blank_id_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ModerationCase(
            " ", "release:r1", "admin-1", ModReason.SPAM, ModSeverity.LOW, ModStatus.OPEN,
            NOW, false, NOW));
  }
}
