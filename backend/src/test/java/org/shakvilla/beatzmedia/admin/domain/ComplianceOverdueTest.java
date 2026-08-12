package org.shakvilla.beatzmedia.admin.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.application.port.in.ComplianceRequestView;

/**
 * Covers the derived overdue state.
 *
 * <p><strong>Why.</strong> {@link ComplianceStatus#OVERDUE} existed and nothing ever set it — no
 * sweep, no transition. The Compliance page counts {@code status == 'overdue'} and styles it red, so
 * it read {@code 0 overdue} for a request a day past its deadline. On the page whose whole purpose
 * is tracking statutory DSAR deadlines, the one indicator that a legal deadline had been missed was
 * permanently stuck at zero.
 */
@Tag("unit")
class ComplianceOverdueTest {

  private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
  private static final Instant YESTERDAY = NOW.minus(Duration.ofDays(1));
  private static final Instant TOMORROW = NOW.plus(Duration.ofDays(1));

  private static ComplianceRequest request(ComplianceStatus status, Instant dueAt) {
    return new ComplianceRequest(
        "cmp-1", ComplianceType.DSAR_EXPORT, "Account:acc-1", "detail", dueAt, status,
        NOW.minus(Duration.ofDays(10)));
  }

  @Test
  void aRequestPastItsDueDateIsOverdue() {
    assertTrue(request(ComplianceStatus.NEW, YESTERDAY).isOverdue(NOW));
  }

  @Test
  void aRequestStillWithinItsDeadlineIsNot() {
    assertFalse(request(ComplianceStatus.NEW, TOMORROW).isOverdue(NOW));
  }

  /**
   * The reason this is derived rather than a stored status. {@code start} moves
   * {@code new|overdue → in_progress}, so a flipped status would be erased the moment anyone began
   * work — exactly when the breach still matters. Starting does not un-miss a deadline.
   */
  @Test
  void startingWorkDoesNotClearOverdue() {
    assertTrue(request(ComplianceStatus.IN_PROGRESS, YESTERDAY).isOverdue(NOW));
  }

  @Test
  void aCompletedRequestIsNeverOverdue() {
    // Completed late is a fact about history, not an open breach to chase.
    assertFalse(request(ComplianceStatus.COMPLETED, YESTERDAY).isOverdue(NOW));
  }

  @Test
  void aRequestWithNoDeadlineIsNeverOverdue() {
    assertFalse(request(ComplianceStatus.NEW, null).isOverdue(NOW));
  }

  @Test
  void theDueInstantItselfIsNotYetOverdue() {
    // Strictly after: a deadline is missed once it passes, not as it arrives.
    assertFalse(request(ComplianceStatus.NEW, NOW).isOverdue(NOW));
  }

  @Test
  void theViewReportsOverdueThroughTheStatusToken() {
    // The page counts status == 'overdue', so reporting it here is what makes the counter move
    // without touching the frontend.
    assertEquals(
        "overdue",
        ComplianceRequestView.of(request(ComplianceStatus.IN_PROGRESS, YESTERDAY), NOW).status());
  }

  @Test
  void theViewKeepsTheRealStatusWhenNotOverdue() {
    assertEquals(
        "in_progress",
        ComplianceRequestView.of(request(ComplianceStatus.IN_PROGRESS, TOMORROW), NOW).status());
  }
}
