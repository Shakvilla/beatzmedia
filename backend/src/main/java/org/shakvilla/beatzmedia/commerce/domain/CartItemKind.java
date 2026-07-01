package org.shakvilla.beatzmedia.commerce.domain;

/**
 * Cart item kinds. Mirrors the frontend {@code CartItemKind} type exactly (Frontend
 * {@code src/types/index.ts}) and PRD §6.5. Commerce ADD §3.
 *
 * <p><b>Stackability (domain rule):</b> {@code track, album, album-rest, episode, season-pass} are
 * digital one-offs — non-stackable, qty fixed at 1, re-add is a no-op. {@code ticket} and
 * {@code store} (merch) are stackable, qty clamped {@code 1..99}.
 */
public enum CartItemKind {
  track(false),
  album(false),
  album_rest(false),
  store(true),
  episode(false),
  season_pass(false),
  ticket(true);

  private final boolean stackable;

  CartItemKind(boolean stackable) {
    this.stackable = stackable;
  }

  public boolean isStackable() {
    return stackable;
  }

  /** Wire value uses hyphens ({@code album-rest}, {@code season-pass}); enum names use underscores. */
  public String wireValue() {
    return name().replace('_', '-');
  }

  /** Parse the wire value ({@code album-rest}, {@code season-pass}, etc.) into the enum constant. */
  public static CartItemKind fromWireValue(String wireValue) {
    if (wireValue == null) {
      throw new IllegalArgumentException("kind must not be null");
    }
    try {
      return CartItemKind.valueOf(wireValue.replace('-', '_'));
    } catch (IllegalArgumentException e) {
      throw new InvalidCartItemKindException(wireValue);
    }
  }
}
