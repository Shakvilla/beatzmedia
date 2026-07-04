package org.shakvilla.beatzmedia.payments.domain;

/**
 * Typed identifier for a {@link PayoutMethod} (a creator's cash-out destination). Framework-free.
 * Payments ADD §3.
 */
public record PayoutMethodId(String value) {

  public PayoutMethodId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("PayoutMethodId value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
