package org.shakvilla.beatzmedia.commerce.application.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.application.port.out.SettlementSource;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;

/**
 * Discovers the {@link SettlementSource} beans contributed by the owning modules (CDI {@code
 * Instance}, same pattern as {@code ReindexService}'s {@code Instance<IndexSource>}) and indexes them
 * by {@code entityType}. Shared by {@code GrantOwnershipService} (settlement fulfillment) and {@code
 * CheckoutService} (the un-gate payee guard). WU-COM-4.
 */
@ApplicationScoped
public class SettlementSourceRegistry {

  private final Map<String, SettlementSource> byEntityType;

  @Inject
  public SettlementSourceRegistry(Instance<SettlementSource> sources) {
    this(sources.stream().toList());
  }

  // Package-private test seam — no CDI Instance needed.
  SettlementSourceRegistry(List<SettlementSource> sources) {
    Map<String, SettlementSource> map = new HashMap<>();
    for (SettlementSource source : sources) {
      SettlementSource previous = map.put(source.entityType(), source);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate SettlementSource for entityType=" + source.entityType());
      }
    }
    this.byEntityType = Map.copyOf(map);
  }

  public Optional<SettlementSource> forEntityType(String entityType) {
    return Optional.ofNullable(byEntityType.get(entityType));
  }

  public Optional<SettlementSource> forKind(CartItemKind kind) {
    return forEntityType(kind.wireValue());
  }
}
