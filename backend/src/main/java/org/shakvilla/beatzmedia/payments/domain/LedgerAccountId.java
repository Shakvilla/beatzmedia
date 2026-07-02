package org.shakvilla.beatzmedia.payments.domain;

/**
 * Typed identifier for a {@link LedgerAccount}. Opaque UUIDv7 string (TEXT in the DB, matching the
 * rest of the codebase). Framework-free value object.
 */
public record LedgerAccountId(String value) {

  public LedgerAccountId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("LedgerAccountId value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
