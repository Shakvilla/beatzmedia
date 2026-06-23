package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link SystemClockAdapter} and {@link Uuidv7IdGenerator}. These adapters
 * have no framework dependency that requires Quarkus context; they can be tested in isolation.
 * Testing-strategy §5.
 */
@Tag("unit")
class PlatformAdapterUnitTest {

  // ---- SystemClockAdapter --------------------------------------------------

  @Test
  void systemClock_now_returns_non_null_instant() {
    SystemClockAdapter clock = new SystemClockAdapter();
    assertNotNull(clock.now(), "now() must return a non-null Instant");
  }

  @Test
  void systemClock_now_is_approximately_current_time() {
    SystemClockAdapter clock = new SystemClockAdapter();
    long before = System.currentTimeMillis();
    long clockMs = clock.now().toEpochMilli();
    long after = System.currentTimeMillis();
    assertTrue(clockMs >= before && clockMs <= after,
        "clock.now() epoch millis must be within the test's wall-clock window");
  }

  @Test
  void systemClock_today_returns_correct_date_for_utc() {
    SystemClockAdapter clock = new SystemClockAdapter();
    assertNotNull(clock.today(ZoneId.of("UTC")), "today() must not return null");
  }

  @Test
  void systemClock_today_returns_correct_date_for_accra_zone() {
    SystemClockAdapter clock = new SystemClockAdapter();
    // Africa/Accra is GMT+0, same as UTC; should never throw
    assertNotNull(clock.today(ZoneId.of("Africa/Accra")));
  }

  // ---- Uuidv7IdGenerator ---------------------------------------------------

  @Test
  void uuidv7_newId_returns_valid_uuid_format() {
    Uuidv7IdGenerator gen = new Uuidv7IdGenerator();
    String id = gen.newId();
    assertNotNull(id);
    // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
    assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}"),
        "Generated ID must be a valid UUIDv7: " + id);
  }

  @Test
  void uuidv7_newId_is_unique_across_calls() {
    Uuidv7IdGenerator gen = new Uuidv7IdGenerator();
    String id1 = gen.newId();
    String id2 = gen.newId();
    assertTrue(!id1.equals(id2), "Two successive newId() calls must produce different IDs");
  }

  @Test
  void uuidv7_newId_version_nibble_is_7() {
    Uuidv7IdGenerator gen = new Uuidv7IdGenerator();
    String id = gen.newId();
    // The version nibble is the first character of the 3rd group (index 14 in the UUID string)
    assertEquals('7', id.charAt(14), "Version nibble must be '7' for UUIDv7");
  }

  @Test
  void uuidv7_newOrderRef_returns_correct_format() {
    Uuidv7IdGenerator gen = new Uuidv7IdGenerator();
    String ref = gen.newOrderRef(2026);
    assertNotNull(ref);
    assertTrue(ref.startsWith("BZ-2026-"),
        "Order ref must start with BZ-YYYY-: " + ref);
    assertTrue(ref.matches("BZ-\\d{4}-\\d{5}"),
        "Order ref must match BZ-YYYY-NNNNN: " + ref);
  }

  @Test
  void uuidv7_orderRef_sequence_increments() {
    Uuidv7IdGenerator gen = new Uuidv7IdGenerator();
    String ref1 = gen.newOrderRef(2026);
    String ref2 = gen.newOrderRef(2026);
    // Extract sequences and compare
    int seq1 = Integer.parseInt(ref1.substring(ref1.lastIndexOf('-') + 1));
    int seq2 = Integer.parseInt(ref2.substring(ref2.lastIndexOf('-') + 1));
    assertEquals(seq1 + 1, seq2, "Order ref sequence must increment by 1");
  }
}
