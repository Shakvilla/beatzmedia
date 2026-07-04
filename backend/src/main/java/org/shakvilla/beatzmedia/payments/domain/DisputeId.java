package org.shakvilla.beatzmedia.payments.domain;

import java.util.Objects;

/**
 * Identity of a {@link Dispute} aggregate (payments ADD §3). A thin typed wrapper over the UUIDv7
 * string id so a dispute id can never be confused with any other id in a signature. Framework-free.
 */
public record DisputeId(String value) {

  public DisputeId {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("dispute id must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
