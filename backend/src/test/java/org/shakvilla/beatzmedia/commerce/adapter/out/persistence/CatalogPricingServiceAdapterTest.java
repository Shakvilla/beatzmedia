package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.port.out.ModulePriceSource;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricedItem;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Unit tests for {@link CatalogPricingServiceAdapter}'s dispatch of external cart kinds
 * (episode/season-pass/ticket/store) to their contributed {@link ModulePriceSource} beans (WU-COM-4).
 * The external-kind path never touches the {@code EntityManager}, so a {@code null} em is fine here.
 */
class CatalogPricingServiceAdapterTest {

  /** Minimal fake {@link ModulePriceSource}: returns {@code out}, or throws when {@code out} is null. */
  private static ModulePriceSource source(String entityType, PricedItem out) {
    return new ModulePriceSource() {
      @Override
      public String entityType() {
        return entityType;
      }

      @Override
      public PricedItem price(String refId, Map<String, Object> metadata) {
        if (out == null) {
          throw new PriceUnavailableException(entityType, refId);
        }
        return out;
      }
    };
  }

  @Test
  void dispatchesEpisodeToItsSource() {
    PricedItem episode = new PricedItem("Ep 1", "Show", "img", Money.ofMinor(500, Currency.GHS));
    var adapter =
        new CatalogPricingServiceAdapter(
            null, List.of(source("episode", episode), source("ticket", null)));

    PricedItem result = adapter.priceFor(CartItemKind.episode, "ep-1", Map.of());

    assertSame(episode, result);
  }

  @Test
  void dispatchesSeasonPassByWireValue() {
    PricedItem pass = new PricedItem("Show S1", "Publisher", "img", Money.ofMinor(2000, Currency.GHS));
    var adapter = new CatalogPricingServiceAdapter(null, List.of(source("season-pass", pass)));

    PricedItem result = adapter.priceFor(CartItemKind.season_pass, "show-1", Map.of());

    assertEquals(pass, result);
  }

  @Test
  void unknownEntityTypeThrowsPriceUnavailable() {
    var adapter = new CatalogPricingServiceAdapter(null, List.of());

    assertThrows(
        PriceUnavailableException.class,
        () -> adapter.priceFor(CartItemKind.season_pass, "show-1", Map.of()));
  }

  @Test
  void sourcePriceUnavailablePropagates() {
    var adapter = new CatalogPricingServiceAdapter(null, List.of(source("ticket", null)));

    assertThrows(
        PriceUnavailableException.class,
        () -> adapter.priceFor(CartItemKind.ticket, "evt-1:VIP", Map.of()));
  }

  @Test
  void duplicateEntityTypeIsRejectedAtConstruction() {
    PricedItem a = new PricedItem("A", null, null, Money.ofMinor(1, Currency.GHS));
    assertThrows(
        IllegalStateException.class,
        () ->
            new CatalogPricingServiceAdapter(
                null, List.of(source("store", a), source("store", a))));
  }
}
