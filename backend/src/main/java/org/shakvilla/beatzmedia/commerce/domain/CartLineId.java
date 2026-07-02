package org.shakvilla.beatzmedia.commerce.domain;

/**
 * Typed wrapper for a cart line id, e.g. {@code track:last-last} or {@code ticket:iron-boy:VIP}.
 * Stable and human-readable; used as the PATCH/DELETE path parameter. Commerce ADD §3.
 */
public record CartLineId(String value) {

  public CartLineId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("CartLineId value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
