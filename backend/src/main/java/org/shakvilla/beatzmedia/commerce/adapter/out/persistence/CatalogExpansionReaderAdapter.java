package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.AlbumEntity;
import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.TrackEntity;
import org.shakvilla.beatzmedia.commerce.application.port.out.CatalogExpansionReader;
import org.shakvilla.beatzmedia.commerce.application.port.out.OwnershipRepository;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Implements {@link CatalogExpansionReader} by reading catalog's own JPA entities in-process — the
 * same read-only cross-module pattern as {@code CatalogPricingServiceAdapter} and
 * {@code library.CatalogReaderAdapter} (no cross-module FK, no shared persistence). Resolves
 * album→tracks (INV-2), the caller's remaining purchasable tracks for {@code album-rest} (F2), and
 * track/album→creator (artist, INV-4). Commerce ADD §5.2 / §8.
 *
 * <p>Only {@code for-sale} tracks with a positive price are ownable via a purchase — {@code free} and
 * authoring-{@code owned} tracks are excluded from both {@code album-rest} pricing and granting.
 *
 * <p>// G2 — {@code episode}/{@code season-pass} expansion (podcasts) and their creator resolution are
 * intentionally NOT implemented here: those kinds are GATED at checkout (G3, see
 * {@code CheckoutService.gateKind}) until WU-POD-1 ships an authoritative podcasts read port, so they
 * can never reach settlement in this WU. When they land, add their expansion branch here.
 */
@ApplicationScoped
public class CatalogExpansionReaderAdapter implements CatalogExpansionReader {

  private static final String FOR_SALE = "for-sale";

  private final EntityManager em;
  private final OwnershipRepository ownershipRepository;

  @Inject
  public CatalogExpansionReaderAdapter(EntityManager em, OwnershipRepository ownershipRepository) {
    this.em = em;
    this.ownershipRepository = ownershipRepository;
  }

  @Override
  public List<String> tracksToGrant(CartItemKind kind, String refId) {
    return switch (kind) {
      case track -> trackIfExists(refId);
      // Full album: grant every constituent track (INV-2).
      case album -> allAlbumTrackIds(refId);
      // Buy-the-rest: grant only the album's for-sale tracks (F2). The per-track already-owned guard
      // in GrantOwnershipService then skips any the caller already owns, so exactly the paid-for
      // tracks are granted — free / authoring-owned tracks are never in this list.
      case album_rest -> forSaleAlbumTrackIds(refId);
      // Gated at checkout (G3) — never reaches settlement in this WU.
      case episode, season_pass, ticket, store -> List.of();
    };
  }

  @Override
  public List<PurchasableTrack> remainingForSaleTracks(AccountId account, String albumRefId) {
    List<Object[]> rows =
        em.createQuery(
                "SELECT t.id, t.priceMinor FROM TrackEntity t"
                    + " WHERE t.albumId = :aid AND t.ownership = :fs AND t.priceMinor IS NOT NULL"
                    + " AND t.priceMinor > 0 ORDER BY t.id",
                Object[].class)
            .setParameter("aid", albumRefId)
            .setParameter("fs", FOR_SALE)
            .getResultList();
    List<PurchasableTrack> remaining = new ArrayList<>();
    for (Object[] row : rows) {
      String trackId = (String) row[0];
      // Ownership-aware: skip tracks the caller already actively owns (F2).
      if (ownershipRepository.existsActiveForTrack(account, trackId)) {
        continue;
      }
      remaining.add(new PurchasableTrack(trackId, ((Number) row[1]).longValue()));
    }
    return remaining;
  }

  @Override
  public Optional<String> creatorOf(CartItemKind kind, String refId) {
    return switch (kind) {
      case track -> {
        TrackEntity t = em.find(TrackEntity.class, refId);
        yield t == null ? Optional.empty() : Optional.ofNullable(t.artistId);
      }
      case album, album_rest -> {
        AlbumEntity a = em.find(AlbumEntity.class, refId);
        yield a == null ? Optional.empty() : Optional.ofNullable(a.artistId);
      }
      case episode, season_pass, ticket, store -> Optional.empty();
    };
  }

  private List<String> trackIfExists(String refId) {
    TrackEntity t = em.find(TrackEntity.class, refId);
    return t == null ? List.of() : List.of(t.id);
  }

  private List<String> allAlbumTrackIds(String albumId) {
    return em
        .createQuery("SELECT t.id FROM TrackEntity t WHERE t.albumId = :aid", String.class)
        .setParameter("aid", albumId)
        .getResultList();
  }

  private List<String> forSaleAlbumTrackIds(String albumId) {
    return em
        .createQuery(
            "SELECT t.id FROM TrackEntity t WHERE t.albumId = :aid AND t.ownership = :fs"
                + " AND t.priceMinor IS NOT NULL AND t.priceMinor > 0",
            String.class)
        .setParameter("aid", albumId)
        .setParameter("fs", FOR_SALE)
        .getResultList();
  }
}
