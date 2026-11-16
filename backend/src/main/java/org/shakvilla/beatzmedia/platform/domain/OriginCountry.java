package org.shakvilla.beatzmedia.platform.domain;

import java.util.Arrays;
import java.util.Locale;

/** Optional, explicitly supplied ISO 3166-1 alpha-2 country for content origin. */
public final class OriginCountry {

  private OriginCountry() {}

  public static String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    String code = value.trim().toUpperCase(Locale.ROOT);
    if (!Arrays.asList(Locale.getISOCountries()).contains(code)) {
      throw new ValidationException("originCountry must be an ISO country code", "originCountry");
    }
    return code;
  }
}
