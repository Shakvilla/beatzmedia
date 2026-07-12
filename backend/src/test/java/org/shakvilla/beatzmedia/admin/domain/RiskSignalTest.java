package org.shakvilla.beatzmedia.admin.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link RiskSignal} aggregate's guarded transitions (LLFR-ADMIN-07.1). */
@Tag("unit")
class RiskSignalTest {

  private RiskSignal open() {
    return new RiskSignal(
        "r1", "@new_artist", "Payment fraud", "3 failed payouts", RiskLevel.HIGH, RiskStatus.OPEN,
        Instant.parse("2026-07-12T10:00:00Z"));
  }

  @Test
  void clearTransitionsOpenToCleared() {
    RiskSignal s = open();
    s.clear();
    assertEquals(RiskStatus.CLEARED, s.getStatus());
  }

  @Test
  void banTransitionsOpenToBanned() {
    RiskSignal s = open();
    s.ban();
    assertEquals(RiskStatus.BANNED, s.getStatus());
  }

  @Test
  void reviewIsAnAcknowledgmentThatLeavesStatusOpen() {
    RiskSignal s = open();
    assertDoesNotThrow(s::review);
    assertEquals(RiskStatus.OPEN, s.getStatus());
  }

  @Test
  void actionsOnAClearedSignalAreIllegalTransitions() {
    RiskSignal s = open();
    s.clear();
    assertThrows(IllegalRiskTransitionException.class, s::review);
    assertThrows(IllegalRiskTransitionException.class, s::clear);
    assertThrows(IllegalRiskTransitionException.class, s::ban);
  }

  @Test
  void actionsOnABannedSignalAreIllegalTransitions() {
    RiskSignal s = open();
    s.ban();
    assertThrows(IllegalRiskTransitionException.class, s::clear);
    assertThrows(IllegalRiskTransitionException.class, s::ban);
  }

  @Test
  void constructorRejectsBlankRequiredFields() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RiskSignal(
                "r1", "  ", "Payment fraud", null, RiskLevel.HIGH, RiskStatus.OPEN, Instant.now()));
  }
}
