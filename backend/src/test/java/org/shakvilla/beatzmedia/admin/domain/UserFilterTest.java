package org.shakvilla.beatzmedia.admin.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

@Tag("unit")
class UserFilterTest {

  @Test
  void null_value_returns_null_no_filter() {
    assertNull(UserFilter.fromWireValue(null));
  }

  @Test
  void blank_value_returns_null_no_filter() {
    assertNull(UserFilter.fromWireValue("  "));
  }

  @Test
  void parses_all_known_wire_values_case_insensitive() {
    assertEquals(UserFilter.FANS, UserFilter.fromWireValue("fans"));
    assertEquals(UserFilter.ARTISTS, UserFilter.fromWireValue("ARTISTS"));
    assertEquals(UserFilter.VERIFIED, UserFilter.fromWireValue("Verified"));
    assertEquals(UserFilter.SUSPENDED, UserFilter.fromWireValue("suspended"));
  }

  @Test
  void unknown_value_throws_ValidationException() {
    assertThrows(ValidationException.class, () -> UserFilter.fromWireValue("bogus"));
  }
}
