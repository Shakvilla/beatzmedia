package org.shakvilla.beatzmedia.catalog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Domain unit tests for the {@link Release} lifecycle FSM (LLFR-CATALOG-02.5). Framework-free;
 * every legal edge succeeds and every illegal edge throws {@link IllegalTransitionException}.
 * Catalog ADD §8 state diagram.
 */
@Tag("unit")
class ReleaseTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant FUTURE = Instant.parse("2026-02-01T00:00:00Z");
  private static final Instant PAST = Instant.parse("2025-01-01T00:00:00Z");

  // ---- Legal edges ----

  @Test
  void submit_creates_release_in_review() {
    Release r = newInReview();
    assertEquals(ReleaseStatus.in_review, r.getStatus());
  }

  @Test
  void approveScheduled_from_in_review_with_future_date_moves_to_scheduled() {
    Release r = newInReview();
    r.approveScheduled(FUTURE, T0);
    assertEquals(ReleaseStatus.scheduled, r.getStatus());
    assertEquals(FUTURE, r.getScheduledAt());
  }

  @Test
  void approveImmediate_from_in_review_moves_to_live_and_stamps_wentLiveAt() {
    Release r = newInReview();
    r.approveImmediate(T0);
    assertEquals(ReleaseStatus.live, r.getStatus());
    assertEquals(T0, r.getWentLiveAt());
  }

  @Test
  void goLive_from_scheduled_moves_to_live_exactly_once() {
    Release r = newInReview();
    r.approveScheduled(FUTURE, T0);
    r.goLive(FUTURE);
    assertEquals(ReleaseStatus.live, r.getStatus());
    assertEquals(FUTURE, r.getWentLiveAt());
  }

  @Test
  void takedown_from_live_moves_to_takedown() {
    Release r = newInReview();
    r.approveImmediate(T0);
    r.takedown(T0);
    assertEquals(ReleaseStatus.takedown, r.getStatus());
  }

  @Test
  void reinstate_from_takedown_moves_back_to_live() {
    Release r = newInReview();
    r.approveImmediate(T0);
    r.takedown(T0);
    r.reinstate(T0);
    assertEquals(ReleaseStatus.live, r.getStatus());
  }

  // ---- Illegal edges: every one must throw IllegalTransitionException (409 ILLEGAL_TRANSITION) ----

  @Test
  void approveScheduled_from_draft_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.draft);
    assertThrows(IllegalTransitionException.class, () -> r.approveScheduled(FUTURE, T0));
  }

  @Test
  void approveScheduled_from_scheduled_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.scheduled);
    assertThrows(IllegalTransitionException.class, () -> r.approveScheduled(FUTURE, T0));
  }

  @Test
  void approveScheduled_from_live_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.live);
    assertThrows(IllegalTransitionException.class, () -> r.approveScheduled(FUTURE, T0));
  }

  @Test
  void approveScheduled_from_takedown_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.takedown);
    assertThrows(IllegalTransitionException.class, () -> r.approveScheduled(FUTURE, T0));
  }

  @Test
  void approveScheduled_with_past_date_is_rejected() {
    Release r = newInReview();
    assertThrows(IllegalArgumentException.class, () -> r.approveScheduled(PAST, T0));
  }

  @Test
  void approveImmediate_from_draft_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.draft);
    assertThrows(IllegalTransitionException.class, () -> r.approveImmediate(T0));
  }

  @Test
  void approveImmediate_from_scheduled_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.scheduled);
    assertThrows(IllegalTransitionException.class, () -> r.approveImmediate(T0));
  }

  @Test
  void approveImmediate_from_live_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.live);
    assertThrows(IllegalTransitionException.class, () -> r.approveImmediate(T0));
  }

  @Test
  void approveImmediate_from_takedown_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.takedown);
    assertThrows(IllegalTransitionException.class, () -> r.approveImmediate(T0));
  }

  @Test
  void goLive_from_draft_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.draft);
    assertThrows(IllegalTransitionException.class, () -> r.goLive(T0));
  }

  @Test
  void goLive_from_in_review_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.in_review);
    assertThrows(IllegalTransitionException.class, () -> r.goLive(T0));
  }

  @Test
  void goLive_from_live_is_illegal_second_call_prevents_double_fire() {
    Release r = newInReview();
    r.approveScheduled(FUTURE, T0);
    r.goLive(FUTURE);
    // Second call must not re-fire — status is now 'live', not 'scheduled'.
    assertThrows(IllegalTransitionException.class, () -> r.goLive(FUTURE));
  }

  @Test
  void goLive_from_takedown_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.takedown);
    assertThrows(IllegalTransitionException.class, () -> r.goLive(T0));
  }

  @Test
  void takedown_from_draft_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.draft);
    assertThrows(IllegalTransitionException.class, () -> r.takedown(T0));
  }

  @Test
  void takedown_from_in_review_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.in_review);
    assertThrows(IllegalTransitionException.class, () -> r.takedown(T0));
  }

  @Test
  void takedown_from_scheduled_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.scheduled);
    assertThrows(IllegalTransitionException.class, () -> r.takedown(T0));
  }

  @Test
  void takedown_from_takedown_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.takedown);
    assertThrows(IllegalTransitionException.class, () -> r.takedown(T0));
  }

  @Test
  void reinstate_from_draft_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.draft);
    assertThrows(IllegalTransitionException.class, () -> r.reinstate(T0));
  }

  @Test
  void reinstate_from_in_review_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.in_review);
    assertThrows(IllegalTransitionException.class, () -> r.reinstate(T0));
  }

  @Test
  void reinstate_from_scheduled_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.scheduled);
    assertThrows(IllegalTransitionException.class, () -> r.reinstate(T0));
  }

  @Test
  void reinstate_from_live_is_illegal() {
    Release r = reconstituteAt(ReleaseStatus.live);
    assertThrows(IllegalTransitionException.class, () -> r.reinstate(T0));
  }

  // ---- helpers ----

  private Release newInReview() {
    return Release.create(
        "rel-1",
        "artist-1",
        "Test Release",
        ReleaseType.single,
        Visibility.PUBLIC,
        null,
        List.of(new ReleaseTrack("t1", 1, 500)),
        24,
        T0);
  }

  private Release reconstituteAt(ReleaseStatus status) {
    return Release.reconstitute(
        "rel-1",
        "artist-1",
        "Test Release",
        ReleaseType.single,
        status,
        Visibility.PUBLIC,
        status == ReleaseStatus.scheduled ? FUTURE : null,
        status == ReleaseStatus.live || status == ReleaseStatus.takedown ? T0 : null,
        500,
        T0,
        T0,
        List.of(new ReleaseTrack("t1", 1, 500)),
        null,
        null);
  }

  // ---- WU-CAT-5: draft create/upload/finalize lifecycle ----

  private static final Instant NOW = Instant.parse("2026-07-17T10:00:00Z");

  private static Release draft() {
    return Release.createDraft(
        "r1", "art1", "Untitled release", ReleaseType.single,
        Visibility.PUBLIC, null, "Afrobeats", "My bio", NOW);
  }

  @Test
  void createDraft_startsInDraftWithNoTracksAndZeroPrice() {
    Release r = draft();
    assertEquals(ReleaseStatus.draft, r.getStatus());
    assertTrue(r.getTracks().isEmpty());
    assertEquals(0L, r.getListPriceMinor());
    assertEquals("Afrobeats", r.getGenre());
  }

  @Test
  void addAndRemoveTrack_onDraft() {
    Release r = draft();
    r.addTrack(new ReleaseTrack("t1", 0, 250L), NOW);
    assertEquals(1, r.getTracks().size());
    r.removeTrack("t1", NOW);
    assertTrue(r.getTracks().isEmpty());
  }

  @Test
  void trackMutation_rejectedWhenNotDraft() {
    Release r = draft();
    r.addTrack(new ReleaseTrack("t1", 0, 250L), NOW);
    r.submit(24, NOW); // -> in_review
    assertThrows(
        IllegalTransitionException.class,
        () -> r.addTrack(new ReleaseTrack("t2", 1, 250L), NOW));
    assertThrows(IllegalTransitionException.class, () -> r.removeTrack("t1", NOW));
    assertThrows(IllegalTransitionException.class, () -> r.replaceTracks(List.of(), NOW));
  }

  @Test
  void submit_transitionsToInReviewAndRecomputesPrice() {
    Release r = draft();
    r.replaceTracks(List.of(new ReleaseTrack("t1", 0, 250L)), NOW);
    r.submit(24, NOW);
    assertEquals(ReleaseStatus.in_review, r.getStatus());
    assertEquals(250L, r.getListPriceMinor()); // single: no bundle discount
  }

  @Test
  void submit_rejectedWhenNotDraft() {
    Release r = draft();
    r.addTrack(new ReleaseTrack("t1", 0, 250L), NOW);
    r.submit(24, NOW);
    assertThrows(IllegalTransitionException.class, () -> r.submit(24, NOW));
  }
}
