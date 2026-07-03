package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.AlbumEntity;
import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.TrackEntity;
import org.shakvilla.beatzmedia.commerce.application.port.out.CatalogExpansionReader;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;

/**
 * Implements {@link CatalogExpansionReader} by reading catalog's own JPA entities in-process — the
 * same read-only cross-module pattern as {@code CatalogPricingServiceAdapter} and
 * {@code library.CatalogReaderAdapter} (no cross-module FK, no shared persistence). Resolves
 * album→tracks (INV-2) and track/album→creator (artist, INV-4). Commerce ADD §5.2 / §8.
 *
 * <p>// G2 — {@code episode}/{@code season-pass} expansion (podcasts) and their creator resolution are
 * intentionally NOT implemented here: those kinds are GATED at checkout (G3, see
 * {@code CheckoutService.gateKind}) until WU-POD-1 ships an authoritative podcasts read port, so they
 * can never reach settlement in this WU. When they land, add their expansion branch here.
 */
@ApplicationScoped
public class CatalogExpansionReaderAdapter implements CatalogExpansionReader {

  private final EntityManager em;

  @Inject
  public CatalogExpansionReaderAdapter(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<String> tracksToGrant(CartItemKind kind, String refId) {
    return switch (kind) {
      case track -> trackIfExists(refId);
      case album, album_rest -> albumTrackIds(refId);
      // Gated at checkout (G3) — never reaches settlement in this WU.
      case episode, season_pass, ticket, store -> List.of();
    };
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

  private List<String> albumTrackIds(String albumId) {
    return em
        .createQuery("SELECT t.id FROM TrackEntity t WHERE t.albumId = :aid", String.class)
        .setParameter("aid", albumId)
        .getResultList();
  }
}
