package org.shakvilla.beatzmedia.commerce.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CartItemKind} wire-value parsing + stackability flags. */
@Tag("unit")
class CartItemKindTest {

  @Test
  void fromWireValue_hyphenatedKinds_parseCorrectly() {
    assertEquals(CartItemKind.album_rest, CartItemKind.fromWireValue("album-rest"));
    assertEquals(CartItemKind.season_pass, CartItemKind.fromWireValue("season-pass"));
  }

  @Test
  void fromWireValue_simpleKinds_parseCorrectly() {
    assertEquals(CartItemKind.track, CartItemKind.fromWireValue("track"));
    assertEquals(CartItemKind.album, CartItemKind.fromWireValue("album"));
    assertEquals(CartItemKind.store, CartItemKind.fromWireValue("store"));
    assertEquals(CartItemKind.episode, CartItemKind.fromWireValue("episode"));
    assertEquals(CartItemKind.ticket, CartItemKind.fromWireValue("ticket"));
  }

  @Test
  void fromWireValue_unknownKind_throwsInvalidCartItemKind() {
    assertThrows(InvalidCartItemKindException.class, () -> CartItemKind.fromWireValue("bogus"));
  }

  @Test
  void wireValue_roundTrips() {
    for (CartItemKind kind : CartItemKind.values()) {
      assertEquals(kind, CartItemKind.fromWireValue(kind.wireValue()));
    }
  }

  @Test
  void stackability_digitalOneOffsAreNonStackable() {
    assertFalse(CartItemKind.track.isStackable());
    assertFalse(CartItemKind.album.isStackable());
    assertFalse(CartItemKind.album_rest.isStackable());
    assertFalse(CartItemKind.episode.isStackable());
    assertFalse(CartItemKind.season_pass.isStackable());
  }

  @Test
  void stackability_ticketAndStoreAreStackable() {
    assertTrue(CartItemKind.ticket.isStackable());
    assertTrue(CartItemKind.store.isStackable());
  }
}
