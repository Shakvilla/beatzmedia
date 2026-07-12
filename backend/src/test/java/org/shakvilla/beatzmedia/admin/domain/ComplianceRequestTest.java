package org.shakvilla.beatzmedia.admin.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link ComplianceRequest} aggregate's guarded transitions (LLFR-ADMIN-09.1). */
@Tag("unit")
class ComplianceRequestTest {

  private ComplianceRequest of(ComplianceStatus status) {
    return new ComplianceRequest(
        "co1", ComplianceType.DSAR_EXPORT, "@ama_b", "Personal data export",
        Instant.parse("2026-07-20T00:00:00Z"), status, Instant.parse("2026-07-12T10:00:00Z"));
  }

  @Test
  void startMovesNewToInProgress() {
    ComplianceRequest r = of(ComplianceStatus.NEW);
    r.start();
    assertEquals(ComplianceStatus.IN_PROGRESS, r.getStatus());
  }

  @Test
  void startMovesOverdueToInProgress() {
    ComplianceRequest r = of(ComplianceStatus.OVERDUE);
    r.start();
    assertEquals(ComplianceStatus.IN_PROGRESS, r.getStatus());
  }

  @Test
  void startOnInProgressOrCompletedIs409() {
    assertThrows(IllegalComplianceTransitionException.class, () -> of(ComplianceStatus.IN_PROGRESS).start());
    assertThrows(IllegalComplianceTransitionException.class, () -> of(ComplianceStatus.COMPLETED).start());
  }

  @Test
  void completeMovesToCompleted() {
    ComplianceRequest r = of(ComplianceStatus.IN_PROGRESS);
    r.complete();
    assertEquals(ComplianceStatus.COMPLETED, r.getStatus());
  }

  @Test
  void completeOnAlreadyCompletedIs409() {
    assertThrows(IllegalComplianceTransitionException.class, () -> of(ComplianceStatus.COMPLETED).complete());
  }

  @Test
  void typeFromWireValueParsesAndRejects() {
    assertEquals(ComplianceType.TAKEDOWN, ComplianceType.fromWireValue("Takedown"));
    assertThrows(
        org.shakvilla.beatzmedia.platform.domain.ValidationException.class,
        () -> ComplianceType.fromWireValue("Nope"));
  }
}
