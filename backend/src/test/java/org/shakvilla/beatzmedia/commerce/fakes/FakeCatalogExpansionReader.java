package org.shakvilla.beatzmedia.commerce.fakes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.commerce.application.port.out.CatalogExpansionReader;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;

/**
 * In-memory fake {@link CatalogExpansionReader}. Seed album→tracks and ref→creator so grant fan-out
 * (INV-2) and the sale-split creator resolution (INV-4) are exercised deterministically.
 */
public class FakeCatalogExpansionReader implements CatalogExpansionReader {

  private final Map<String, List<String>> tracksByRef = new HashMap<>();
  private final Map<String, String> creatorByRef = new HashMap<>();

  public void seedTrack(String trackId, String creatorId) {
    tracksByRef.put(key(CartItemKind.track, trackId), List.of(trackId));
    creatorByRef.put(key(CartItemKind.track, trackId), creatorId);
  }

  public void seedAlbum(String albumId, String creatorId, List<String> trackIds) {
    tracksByRef.put(key(CartItemKind.album, albumId), trackIds);
    creatorByRef.put(key(CartItemKind.album, albumId), creatorId);
  }

  @Override
  public List<String> tracksToGrant(CartItemKind kind, String refId) {
    return tracksByRef.getOrDefault(key(kind, refId), List.of());
  }

  @Override
  public Optional<String> creatorOf(CartItemKind kind, String refId) {
    return Optional.ofNullable(creatorByRef.get(key(kind, refId)));
  }

  private String key(CartItemKind kind, String refId) {
    return kind.wireValue() + ":" + refId;
  }
}
