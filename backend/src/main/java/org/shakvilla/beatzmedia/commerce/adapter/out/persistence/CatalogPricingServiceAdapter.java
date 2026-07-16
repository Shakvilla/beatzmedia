package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.AlbumEntity;
import org.shakvilla.beatzmedia.catalog.adapter.out.persistence.TrackEntity;
import org.shakvilla.beatzmedia.commerce.application.port.out.ModulePriceSource;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricedItem;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricingService;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Implements {@link PricingService}. {@code track}/{@code album}/{@code album-rest} resolve
 * authoritatively from catalog's own JPA entities in-process (same pattern as
 * {@code library.adapter.out.persistence.CatalogReaderAdapter}) — no cross-module FK, read-only,
 * same schema, never trusts client-supplied prices (INV-2, INV-11).
 *
 * <p>{@code episode}/{@code season-pass}/{@code ticket}/{@code store} resolve authoritatively too,
 * but from the OWNING module (podcasts/events/store) via a {@link ModulePriceSource} SPI bean the
 * module contributes — commerce never reads another module's tables. The bean whose {@link
 * ModulePriceSource#entityType()} matches the kind's {@code wireValue()} is dispatched to; an
 * unregistered kind is a 404 (WU-COM-4, replacing the former client-metadata price echo). Commerce
 * ADD §4.2 / §9.
 */
@ApplicationScoped
public class CatalogPricingServiceAdapter implements PricingService {

  private final EntityManager em;
  private final Map<String, ModulePriceSource> priceSourcesByType;

  @Inject
  public CatalogPricingServiceAdapter(EntityManager em, Instance<ModulePriceSource> priceSources) {
    this(em, priceSources.stream().toList());
  }

  // Package-private test seam (mirrors ReindexService's List constructor) — no CDI Instance needed.
  CatalogPricingServiceAdapter(EntityManager em, List<ModulePriceSource> priceSources) {
    this.em = em;
    Map<String, ModulePriceSource> byType = new HashMap<>();
    for (ModulePriceSource source : priceSources) {
      ModulePriceSource previous = byType.put(source.entityType(), source);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate ModulePriceSource for entityType=" + source.entityType());
      }
    }
    this.priceSourcesByType = Map.copyOf(byType);
  }

  @Override
  public PricedItem priceFor(CartItemKind kind, String refId, Map<String, Object> metadata) {
    return switch (kind) {
      case track -> priceTrack(refId);
      case album -> priceAlbum(refId);
      case album_rest -> priceAlbum(refId);
      case episode, season_pass, ticket, store -> priceFromModule(kind, refId, metadata);
    };
  }

  private PricedItem priceFromModule(CartItemKind kind, String refId, Map<String, Object> metadata) {
    ModulePriceSource source = priceSourcesByType.get(kind.wireValue());
    if (source == null) {
      throw new PriceUnavailableException(kind.wireValue(), refId);
    }
    return source.price(refId, metadata);
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
}
