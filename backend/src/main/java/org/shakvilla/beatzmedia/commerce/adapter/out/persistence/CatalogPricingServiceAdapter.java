package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.AlbumEntity;
import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.TrackEntity;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricedItem;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricingService;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Implements {@link PricingService}. {@code track}/{@code album}/{@code album-rest} resolve
 * authoritatively from catalog's own JPA entities in-process (same pattern as
 * {@code library.adapter.out.persistence.CatalogReaderAdapter}) — no cross-module FK, read-only,
 * same schema, never trusts client-supplied prices (INV-2, INV-11).
 *
 * <p>{@code episode}/{@code season-pass}/{@code ticket}/{@code store} have no backing module yet
 * (podcasts/events/store ship in Phase 4: WU-POD-1, WU-EVT-1, WU-STO-1). For those kinds this
 * adapter validates and echoes caller-supplied {@code metadata} fields ({@code title},
 * {@code image}, {@code priceMinor}) as a documented interim measure until the owning module
 * exists and exposes a proper price-lookup input port. Commerce ADD §4.2 / §9.
 */
@ApplicationScoped
public class CatalogPricingServiceAdapter implements PricingService {

  private final EntityManager em;

  @Inject
  public CatalogPricingServiceAdapter(EntityManager em) {
    this.em = em;
  }

  @Override
  public PricedItem priceFor(CartItemKind kind, String refId, Map<String, Object> metadata) {
    return switch (kind) {
      case track -> priceTrack(refId);
      case album -> priceAlbum(refId);
      case album_rest -> priceAlbum(refId);
      case episode, season_pass, ticket, store -> priceFromMetadata(kind, refId, metadata);
    };
  }

  private PricedItem priceTrack(String refId) {
    TrackEntity track = em.find(TrackEntity.class, refId);
    if (track == null) {
      throw new PriceUnavailableException("track", refId);
    }
    if (!"for-sale".equals(track.ownership) || track.priceMinor == null) {
      throw new PriceUnavailableException("track", refId);
    }
    return new PricedItem(
        track.title, track.artistName, track.image,
        Money.ofMinor(track.priceMinor, Currency.GHS));
  }

  private PricedItem priceAlbum(String refId) {
    AlbumEntity album = em.find(AlbumEntity.class, refId);
    if (album == null) {
      throw new PriceUnavailableException("album", refId);
    }
    if (album.listPriceMinor <= 0) {
      throw new PriceUnavailableException("album", refId);
    }
    return new PricedItem(
        album.title, album.artistName, album.coverImage,
        Money.ofMinor(album.listPriceMinor, Currency.GHS));
  }

  /**
   * Interim resolution for kinds without a live backing module (episode/season-pass/ticket/store).
   * Requires {@code metadata.title} and a positive {@code metadata.priceMinor} integer; otherwise
   * rejects with 404/422 rather than silently defaulting a price.
   *
   * <p>// G2 — this echo of client-supplied {@code metadata.priceMinor} is used ONLY on the
   * add-to-cart path (no money moves there). WU-COM-2 checkout GATES these four kinds
   * (409 {@code CHECKOUT_KIND_UNSUPPORTED}, ADR-23) so a spoofed price can never reach a real charge.
   * REPLACE this branch with authoritative price-lookup input ports
   * ({@code podcasts}/{@code events}/{@code store} {@code application.port.in}) when WU-POD-1 /
   * WU-EVT-1 / WU-STO-1 ship, then relax the checkout gate per-kind. Do NOT start trusting these
   * prices for checkout before the owning module exists.
   */
  private PricedItem priceFromMetadata(CartItemKind kind, String refId, Map<String, Object> metadata) {
    if (metadata == null) {
      throw new PriceUnavailableException(kind.wireValue(), refId);
    }
    Object titleObj = metadata.get("title");
    Object priceObj = metadata.get("priceMinor");
    if (!(titleObj instanceof String title) || title.isBlank()) {
      throw new PriceUnavailableException(kind.wireValue(), refId);
    }
    long priceMinor = toPositiveLong(priceObj);
    String subtitle = metadata.get("subtitle") instanceof String s ? s : null;
    String image = metadata.get("image") instanceof String i ? i : null;
    return new PricedItem(title, subtitle, image, Money.ofMinor(priceMinor, Currency.GHS));
  }

  private long toPositiveLong(Object value) {
    long minor;
    if (value instanceof Number n) {
      minor = n.longValue();
    } else if (value instanceof String s) {
      try {
        minor = Long.parseLong(s);
      } catch (NumberFormatException e) {
        throw new ValidationException("metadata.priceMinor must be a positive integer", "metadata.priceMinor");
      }
    } else {
      throw new ValidationException("metadata.priceMinor must be a positive integer", "metadata.priceMinor");
    }
    if (minor <= 0) {
      throw new ValidationException("metadata.priceMinor must be a positive integer", "metadata.priceMinor");
    }
    return minor;
  }
}
