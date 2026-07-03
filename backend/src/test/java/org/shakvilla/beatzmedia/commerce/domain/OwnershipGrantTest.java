package org.shakvilla.beatzmedia.commerce.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Unit tests for {@link OwnershipGrant}: single-target invariant, active/revoke lifecycle (INV-9). */
@Tag("unit")
class OwnershipGrantTest {

  private static final AccountId ACCOUNT = new AccountId("acct-1");
  private static final OrderId ORDER = new OrderId("o1");
  private static final Instant NOW = Instant.parse("2026-07-03T10:00:00Z");

  @Test
  void forTrack_isActiveTrackGrant() {
    OwnershipGrant g = OwnershipGrant.forTrack("g1", ACCOUNT, "t1", ORDER, NOW);
    assertEquals("t1", g.getTrackId());
    assertTrue(g.isActive());
  }

  @Test
  void bothTargets_rejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new OwnershipGrant("g1", ACCOUNT, "t1", "e1", ORDER, NOW, null),
        "exactly one of track/episode must be set");
  }

  @Test
  void neitherTarget_rejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new OwnershipGrant("g1", ACCOUNT, null, null, ORDER, NOW, null));
  }

  @Test
  void revoke_setsRevokedAt_andIsIdempotent() {
    OwnershipGrant g = OwnershipGrant.forTrack("g1", ACCOUNT, "t1", ORDER, NOW);
    Instant revokeAt = NOW.plusSeconds(60);
    g.revoke(revokeAt);
    assertFalse(g.isActive());
    assertEquals(revokeAt, g.getRevokedAt());
    // Re-revoking must not overwrite the original revocation instant (idempotent, INV-9).
    g.revoke(NOW.plusSeconds(120));
    assertEquals(revokeAt, g.getRevokedAt());
  }
}
