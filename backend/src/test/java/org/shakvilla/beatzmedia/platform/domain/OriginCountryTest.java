package org.shakvilla.beatzmedia.platform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OriginCountryTest {

  @Test
  void normalizesExplicitCountriesAndKeepsUnknownUnassigned() {
    assertEquals("GH", OriginCountry.normalize(" gh "));
    assertEquals("NG", OriginCountry.normalize("NG"));
    assertNull(OriginCountry.normalize(null));
    assertNull(OriginCountry.normalize("  "));
    assertThrows(ValidationException.class, () -> OriginCountry.normalize("Ghana"));
    assertThrows(ValidationException.class, () -> OriginCountry.normalize("ZZ"));
  }
}
