package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.domain.Cart;
import org.shakvilla.beatzmedia.commerce.domain.CartId;
import org.shakvilla.beatzmedia.commerce.domain.CartItem;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.domain.CartLineId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maps between the {@link Cart} domain aggregate and JPA entities. Domain carries no ORM
 * annotations (ArchUnit-enforced); this is the only place the mapping happens. Commerce ADD §5.2.
 */
@ApplicationScoped
public class CartEntityMapper {

  private final ObjectMapper objectMapper;
  private final IdGenerator ids;

  @Inject
  public CartEntityMapper(ObjectMapper objectMapper, IdGenerator ids) {
    this.objectMapper = objectMapper;
    this.ids = ids;
  }

  Cart toDomain(CartEntity entity) {
    List<CartItem> items =
        entity.items.stream().map(this::toDomainItem).toList();
    CartId cartId = entity.id == null ? null : new CartId(entity.id);
    return new Cart(cartId, new AccountId(entity.accountId), items);
  }

  private CartItem toDomainItem(CartItemEntity e) {
    return new CartItem(
        new CartLineId(e.lineId),
        CartItemKind.fromWireValue(e.kind),
        e.refId,
        e.title,
        e.subtitle,
        e.image,
        Money.ofMinor(e.unitPriceMinor, Currency.valueOf(e.currency)),
        e.qty,
        e.stackable,
        readMetadata(e.metadataJson));
  }

  CartEntity toEntity(Cart cart, CartEntity target) {
    CartEntity entity = target != null ? target : new CartEntity();
    entity.id = cart.getId().value();
    entity.accountId = cart.getAccountId().value();

    // Reconcile items: update existing, add new, remove missing (avoids orphan-removal churn).
    List<CartItem> domainItems = cart.getItems();
    Map<String, CartItemEntity> existingByLineId =
        entity.items.stream().collect(Collectors.toMap(i -> i.lineId, i -> i));

    List<CartItemEntity> reconciled = new ArrayList<>();
    for (CartItem item : domainItems) {
      CartItemEntity itemEntity = existingByLineId.get(item.getLineId().value());
      if (itemEntity == null) {
        itemEntity = new CartItemEntity();
        itemEntity.id = ids.newId();
      }
      itemEntity.cart = entity;
      itemEntity.lineId = item.getLineId().value();
      itemEntity.kind = item.getKind().wireValue();
      itemEntity.refId = item.getRefId();
      itemEntity.title = item.getTitle();
      itemEntity.subtitle = item.getSubtitle();
      itemEntity.image = item.getImage();
      itemEntity.unitPriceMinor = item.getUnitPrice().minor();
      itemEntity.currency = item.getUnitPrice().currency().name();
      itemEntity.qty = item.getQty();
      itemEntity.stackable = item.isStackable();
      itemEntity.metadataJson = writeMetadata(item.getMetadata());
      reconciled.add(itemEntity);
    }
    entity.items.clear();
    entity.items.addAll(reconciled);
    return entity;
  }

  private Map<String, Object> readMetadata(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      return Map.of();
    }
  }

  private String writeMetadata(Map<String, Object> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return "{}";
    }
    try {
      return objectMapper.writeValueAsString(metadata);
    } catch (Exception e) {
      return "{}";
    }
  }
}
