package org.shakvilla.beatzmedia.commerce.fakes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.shakvilla.beatzmedia.commerce.application.port.out.CatalogExpansionReader;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * In-memory fake {@link CatalogExpansionReader}. Seed album→tracks and ref→creator so grant fan-out
 * (INV-2), the sale-split creator resolution (INV-4), and album-rest ownership-aware pricing (F2) are
 * exercised deterministically.
 */
public class FakeCatalogExpansionReader implements CatalogExpansionReader {

  private final Map<String, List<String>> tracksByRef = new HashMap<>();
  private final Map<String, String> creatorByRef = new HashMap<>();
  // album id -> ordered list of (trackId, priceMinor) for its for-sale tracks (F2).
  private final Map<String, List<PurchasableTrack>> forSaleTracksByAlbum = new HashMap<>();
  // (account:trackId) the account already actively owns (F2 ownership-aware album-rest).
  private final Set<String> owned = new HashSet<>();

  public void seedTrack(String trackId, String creatorId) {
    tracksByRef.put(key(CartItemKind.track, trackId), List.of(trackId));
    creatorByRef.put(key(CartItemKind.track, trackId), creatorId);
  }

  public void seedAlbum(String albumId, String creatorId, List<String> trackIds) {
    tracksByRef.put(key(CartItemKind.album, albumId), trackIds);
    creatorByRef.put(key(CartItemKind.album, albumId), creatorId);
  }

  /**
   * Seed an album's for-sale tracks (id + individual price) for album-rest pricing/granting (F2). Also
   * registers album-rest expansion to those track ids and the album-rest creator.
   */
  public void seedForSaleTracks(String albumId, String creatorId, List<PurchasableTrack> tracks) {
    forSaleTracksByAlbum.put(albumId, List.copyOf(tracks));
    tracksByRef.put(
        key(CartItemKind.album_rest, albumId),
        tracks.stream().map(PurchasableTrack::trackId).toList());
    creatorByRef.put(key(CartItemKind.album_rest, albumId), creatorId);
  }

  /** Mark a track as already owned by the account (excluded from album-rest remaining/pricing). */
  public void markOwned(AccountId account, String trackId) {
    owned.add(account.value() + ":" + trackId);
  }

  @Override
  public List<String> tracksToGrant(CartItemKind kind, String refId) {
    return tracksByRef.getOrDefault(key(kind, refId), List.of());
  }

  @Override
  public List<PurchasableTrack> remainingForSaleTracks(AccountId account, String albumRefId) {
    List<PurchasableTrack> all = forSaleTracksByAlbum.getOrDefault(albumRefId, List.of());
    List<PurchasableTrack> remaining = new ArrayList<>();
    for (PurchasableTrack t : all) {
      if (!owned.contains(account.value() + ":" + t.trackId())) {
        remaining.add(t);
      }
    }
    return remaining;
  }

  @Override
  public Optional<String> creatorOf(CartItemKind kind, String refId) {
    return Optional.ofNullable(creatorByRef.get(key(kind, refId)));
  }

  private String key(CartItemKind kind, String refId) {
    return kind.wireValue() + ":" + refId;
  }
}
