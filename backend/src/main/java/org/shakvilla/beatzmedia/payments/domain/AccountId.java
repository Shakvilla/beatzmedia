package org.shakvilla.beatzmedia.payments.domain;

/**
 * Typed wrapper for the opaque account-id of the principal initiating a money operation. Payments
 * references accounts by id only (cross-module rule); the value is the JWT subject of the
 * authenticated caller. Used as the {@code AuditEntry} actor (INV-10 — the audit trail records WHO
 * acted) and persisted on the payment intent. Payments ADD §3.
 */
public record AccountId(String value) {

  public AccountId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("AccountId value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
