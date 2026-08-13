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
  // releaseId -> the artist's download choice (Task 5).
  private final Map<String, Boolean> downloadableByRelease = new HashMap<>();
  // trackId -> its owning releaseId, mirroring TrackEntity.releaseId (Task 5).
  private final Map<String, String> releaseIdByTrack = new HashMap<>();

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

  /** Seed a release's download choice (Task 5). Overwrites any prior value for this release id. */
  public void seedRelease(String releaseId, boolean downloadable) {
    downloadableByRelease.put(releaseId, downloadable);
  }

  /**
   * Associate a track with its owning release (Task 5), mirroring {@code TrackEntity.releaseId} —
   * needed so {@link #isDownloadable} can resolve a {@code track}-kind line to its release's choice.
   * Does not seed the track's tracksToGrant/creator wiring; combine with {@link #seedTrack} for that.
   */
  public void seedTrackForRelease(String trackId, String releaseId, String creatorId) {
    seedTrack(trackId, creatorId);
    releaseIdByTrack.put(trackId, releaseId);
  }

  @Override
  public List<String> tracksToGrant(CartItemKind kind, String refId) {
    return tracksByRef.getOrDefault(key(kind, refId), List.of());
  }

  @Override
  public boolean isDownloadable(CartItemKind kind, String refId) {
    return switch (kind) {
      case track -> Boolean.TRUE.equals(downloadableByRelease.get(releaseIdByTrack.get(refId)));
      // album/album-rest ids are seeded as release ids directly, mirroring the real adapter where
      // the album row shares its release's id one-to-one.
      case album, album_rest -> Boolean.TRUE.equals(downloadableByRelease.get(refId));
      case episode, season_pass, ticket, store -> false;
    };
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
