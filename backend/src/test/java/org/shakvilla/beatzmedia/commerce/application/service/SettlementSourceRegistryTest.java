package org.shakvilla.beatzmedia.commerce.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.port.out.SettlementContext;
import org.shakvilla.beatzmedia.commerce.application.port.out.SettlementSource;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Unit tests for {@link SettlementSourceRegistry} (WU-COM-4). */
class SettlementSourceRegistryTest {

  private static SettlementSource source(String entityType) {
    return new SettlementSource() {
      @Override
      public String entityType() {
        return entityType;
      }

      @Override
      public Optional<AccountId> payee(String refId) {
        return Optional.of(new AccountId("creator-1"));
      }

      @Override
      public void fulfill(SettlementContext ctx) {
        // no-op test double
      }
    };
  }

  @Test
  void indexesByEntityTypeAndResolvesByKind() {
    SettlementSource ticket = source("ticket");
    var registry = new SettlementSourceRegistry(List.of(ticket, source("store")));

    assertSame(ticket, registry.forEntityType("ticket").orElseThrow());
    assertSame(ticket, registry.forKind(CartItemKind.ticket).orElseThrow());
    assertTrue(registry.forKind(CartItemKind.store).isPresent());
  }

  @Test
  void unknownEntityTypeIsEmpty() {
    var registry = new SettlementSourceRegistry(List.of(source("ticket")));
    assertFalse(registry.forKind(CartItemKind.episode).isPresent());
  }

  @Test
  void duplicateEntityTypeIsRejected() {
    assertThrows(
        IllegalStateException.class,
        () -> new SettlementSourceRegistry(List.of(source("store"), source("store"))));
  }
}
