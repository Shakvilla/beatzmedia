package org.shakvilla.beatzmedia.payments.domain;

/**
 * A client-supplied idempotency key for a money-moving request. Same key + same request body ⇒
 * same result (no double effect); same key + different body ⇒ conflict (payments ADD §9.2, §9.2 of
 * PRD). Value objects carry no framework imports.
 */
public record IdempotencyKey(String value) {

  public IdempotencyKey {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey must not be blank");
    }
  }
}
