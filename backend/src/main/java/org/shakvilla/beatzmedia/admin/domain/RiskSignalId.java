package org.shakvilla.beatzmedia.admin.domain;

/** Identity of a {@link RiskSignal}. Admin ADD §3 (LLFR-ADMIN-07.1). */
public record RiskSignalId(String value) {

  public RiskSignalId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("RiskSignalId must not be blank");
    }
  }
}
