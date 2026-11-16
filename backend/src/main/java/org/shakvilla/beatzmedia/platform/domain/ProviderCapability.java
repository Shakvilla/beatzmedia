package org.shakvilla.beatzmedia.platform.domain;

import java.util.Locale;

/** External capability whose active implementation can be selected operationally. */
public enum ProviderCapability {
  STORAGE,
  EMAIL,
  SMS,
  PAYMENT;

  public String wireValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static ProviderCapability fromWireValue(String value) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("capability is required", "capability");
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Unsupported provider capability: " + value, "capability");
    }
  }
}
