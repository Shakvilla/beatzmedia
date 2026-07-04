package org.shakvilla.beatzmedia.payments.domain;

/**
 * Typed identifier for a {@link WithdrawalRequest} (a creator cash-out). Framework-free. Payments
 * ADD §3.
 */
public record WithdrawalId(String value) {

  public WithdrawalId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("WithdrawalId value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
