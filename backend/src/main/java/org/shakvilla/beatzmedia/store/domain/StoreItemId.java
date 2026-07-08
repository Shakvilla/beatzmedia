package org.shakvilla.beatzmedia.store.domain;

/** Typed wrapper for a {@link StoreItem}'s primary key (opaque string). Store ADD §3. */
public record StoreItemId(String value) {

  public StoreItemId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("StoreItemId value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
