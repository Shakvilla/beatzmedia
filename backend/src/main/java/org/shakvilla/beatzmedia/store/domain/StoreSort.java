package org.shakvilla.beatzmedia.store.domain;

/**
 * Sort options for the store catalog feed. Lifted verbatim from the {@code StoreSort} TypeScript
 * union in {@code Frontend/src/types/index.ts}. Each value is backed by a dedicated composite
 * index — no full-table sort (Store ADD §5.1 / §7):
 *
 * <ul>
 *   <li>{@link #POPULAR} (default) — {@code popularity DESC NULLS LAST}.
 *   <li>{@link #NEWEST} — {@code created_at DESC}.
 *   <li>{@link #PRICE_ASC} / {@link #PRICE_DESC} — {@code price_minor} ASC / DESC.
 * </ul>
 */
public enum StoreSort {
  POPULAR("popular"),
  NEWEST("newest"),
  PRICE_ASC("price-asc"),
  PRICE_DESC("price-desc");

  private final String wireValue;

  StoreSort(String wireValue) {
    this.wireValue = wireValue;
  }

  /** The exact wire string used by the frontend/API (e.g. {@code "price-asc"}). */
  public String wireValue() {
    return wireValue;
  }

  /** Parse the wire string back to the enum constant. */
  public static StoreSort fromWireValue(String wireValue) {
    for (StoreSort sort : values()) {
      if (sort.wireValue.equals(wireValue)) {
        return sort;
      }
    }
    throw new IllegalArgumentException("Unknown store sort: " + wireValue);
  }
}
