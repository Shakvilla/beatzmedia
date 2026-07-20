package org.shakvilla.beatzmedia.catalog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SplitInviteTest {

  private static final Instant T0 = Instant.parse("2026-07-19T00:00:00Z");

  @Test
  void issue_isPendingAndUnconsumed() {
    SplitInvite invite = SplitInvite.issue("inv-1", "rel-1", "bob@x.com", "hash", T0.plusSeconds(3600), T0);
    assertFalse(invite.isConsumed());
    assertFalse(invite.isExpired(T0));
    assertNull(invite.outcome());
    assertNull(invite.consumedAt());
  }

  @Test
  void isExpired_trueAtOrAfterExpiry() {
    SplitInvite invite = SplitInvite.issue("inv-1", "rel-1", "bob@x.com", "hash", T0.plusSeconds(10), T0);
    assertFalse(invite.isExpired(T0.plusSeconds(9)));
    assertTrue(invite.isExpired(T0.plusSeconds(10)));
  }

  @Test
  void consume_setsOutcomeAndTimestamp() {
    SplitInvite invite = SplitInvite.issue("inv-1", "rel-1", "bob@x.com", "hash", T0.plusSeconds(3600), T0);
    invite.consume(InviteOutcome.accepted, T0.plusSeconds(5));
    assertTrue(invite.isConsumed());
    assertEquals(InviteOutcome.accepted, invite.outcome());
    assertEquals(T0.plusSeconds(5), invite.consumedAt());
  }

  @Test
  void consume_rejectsDoubleConsume() {
    SplitInvite invite = SplitInvite.issue("inv-1", "rel-1", "bob@x.com", "hash", T0.plusSeconds(3600), T0);
    invite.consume(InviteOutcome.accepted, T0.plusSeconds(5));
    assertThrows(IllegalStateException.class,
        () -> invite.consume(InviteOutcome.declined, T0.plusSeconds(6)));
  }

  @Test
  void splitEntry_sevenArgCtor_defaultsAccountIdNull() {
    SplitEntry e = new SplitEntry("s1", "t1", "Bob", "bob@x.com", "producer", 20, SplitConfirmation.pending);
    assertNull(e.accountId());
  }
}
