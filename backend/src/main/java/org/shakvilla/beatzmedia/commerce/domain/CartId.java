package org.shakvilla.beatzmedia.commerce.domain;

/** Typed wrapper for the cart's own primary key (UUIDv7 string). Commerce ADD §3. */
public record CartId(String value) {

  public CartId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("CartId value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
