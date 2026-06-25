package org.shakvilla.beatzmedia.identity.domain;

/**
 * Typed identity wrapper for an account's opaque string id. Conventions §3 / identity ADD §3.
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
