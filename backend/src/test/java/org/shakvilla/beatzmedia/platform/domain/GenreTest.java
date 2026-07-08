package org.shakvilla.beatzmedia.platform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for the shared {@link Genre} taxonomy (kernel; WU-STU-1 introduces it). */
@Tag("unit")
class GenreTest {

  @Test
  void isValid_knownGenre_true() {
    assertTrue(Genre.isValid("Afrobeats"));
    assertTrue(Genre.isValid("R&B"));
  }

  @Test
  void isValid_unknownGenre_false() {
    assertFalse(Genre.isValid("Dubstep"));
    assertFalse(Genre.isValid(null));
    // Case-sensitive: must match the wire value exactly.
    assertFalse(Genre.isValid("afrobeats"));
  }

  @Test
  void fromWireValue_knownGenre_returnsConstant() {
    assertEquals(Genre.RNB, Genre.fromWireValue("R&B"));
    assertEquals(Genre.DRILL, Genre.fromWireValue("Drill"));
  }

  @Test
  void fromWireValue_unknownGenre_throws() {
    assertThrows(IllegalArgumentException.class, () -> Genre.fromWireValue("Dubstep"));
  }

  @Test
  void wireValue_roundTripsForAllConstants() {
    for (Genre genre : Genre.values()) {
      assertEquals(genre, Genre.fromWireValue(genre.wireValue()));
    }
  }
}
