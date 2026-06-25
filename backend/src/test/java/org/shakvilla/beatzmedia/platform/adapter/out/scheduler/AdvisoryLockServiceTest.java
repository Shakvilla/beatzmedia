package org.shakvilla.beatzmedia.platform.adapter.out.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link AdvisoryLockService} static helpers. No DB or Quarkus context
 * required. Testing-strategy §5 / WU-PLT-2.
 */
@Tag("unit")
class AdvisoryLockServiceTest {

  @Test
  void lockKeyFor_sameInput_returnsSameKey() {
    long key1 = AdvisoryLockService.lockKeyFor("catalog.go-live");
    long key2 = AdvisoryLockService.lockKeyFor("catalog.go-live");
    assertEquals(key1, key2, "lockKeyFor must be deterministic for the same input");
  }

  @Test
  void lockKeyFor_differentNames_returnsDifferentKeys() {
    long keyA = AdvisoryLockService.lockKeyFor("catalog.go-live");
    long keyB = AdvisoryLockService.lockKeyFor("payments.payout-window");
    assertNotEquals(keyA, keyB, "Different job names must produce different lock keys");
  }

  @Test
  void lockKeyFor_allKnownJobNames_areNonZero() {
    String[] names = {
        "catalog.go-live",
        "payments.payout-window",
        "payments.payment-recon",
        "notifications.digest",
        "analytics.rollup",
        "search.reindex"
    };
    for (String name : names) {
      long key = AdvisoryLockService.lockKeyFor(name);
      assertNotEquals(0L, key, "Lock key for '" + name + "' must not be zero");
    }
  }

  @Test
  void lockKeyFor_returnsLongFromJobName() {
    // Verify the key is derived from the job name's hash, not a random value
    long key = AdvisoryLockService.lockKeyFor("test.job");
    assertNotNull(key);  // trivially non-null for long, but verifies it compiles
    // Call again — must be same (determinism already tested above but ensures no random)
    assertEquals(key, AdvisoryLockService.lockKeyFor("test.job"));
  }
}
