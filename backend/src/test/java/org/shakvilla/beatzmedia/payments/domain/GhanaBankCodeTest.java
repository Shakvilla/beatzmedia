package org.shakvilla.beatzmedia.payments.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link GhanaBankCode} (WU-PAY-7) — the Redde bank-code allow-list. */
@Tag("unit")
class GhanaBankCodeTest {

  @Test
  void isValid_accepts_known_tokens_and_rejects_others() {
    assertTrue(GhanaBankCode.isValid("GCB"));
    assertTrue(GhanaBankCode.isValid("ZBL"));
    assertFalse(GhanaBankCode.isValid("XXX"));
    assertFalse(GhanaBankCode.isValid(null));
    assertFalse(GhanaBankCode.isValid("gcb")); // case-sensitive: Redde expects the exact token
  }

  @Test
  void of_resolves_a_known_token_and_throws_on_unknown() {
    assertEquals(GhanaBankCode.GCB, GhanaBankCode.of("GCB"));
    assertThrows(IllegalArgumentException.class, () -> GhanaBankCode.of("NOPE"));
    assertThrows(IllegalArgumentException.class, () -> GhanaBankCode.of(null));
  }

  @Test
  void every_code_has_a_display_name_and_the_constant_name_is_the_wire_token() {
    for (GhanaBankCode c : GhanaBankCode.values()) {
      assertFalse(c.displayName() == null || c.displayName().isBlank(), c + " display name");
      assertTrue(GhanaBankCode.isValid(c.name()), c + " name is a valid token");
    }
  }
}
