package org.shakvilla.beatzmedia.payments.domain;

/**
 * A creator's KYC verification state (payments ADD §3, INV-8). A withdrawal is gated on {@link
 * #VERIFIED}. Mirrors the frontend {@code KycStatus} type and the {@code kyc_record.status} column
 * (V704). Framework-free.
 */
public enum KycStatus {
  NONE,
  PENDING,
  VERIFIED,
  REJECTED;

  /** Wire/DB token (lower-case), e.g. {@code verified}. */
  public String wire() {
    return name().toLowerCase();
  }

  /** Parse a wire/DB token to the enum; unknown/blank values map to {@link #NONE}. */
  public static KycStatus fromWire(String value) {
    if (value == null || value.isBlank()) {
      return NONE;
    }
    return valueOf(value.trim().toUpperCase());
  }

  /** True iff a withdrawal may be requested/executed for this state (INV-8). */
  public boolean isVerified() {
    return this == VERIFIED;
  }
}
