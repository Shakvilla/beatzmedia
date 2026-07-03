package org.shakvilla.beatzmedia.commerce.domain;

/** Typed wrapper for an {@link Order}'s primary key (UUIDv7 string). Commerce ADD §3. */
public record OrderId(String value) {

  public OrderId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("OrderId value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
