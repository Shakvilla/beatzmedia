package org.shakvilla.beatzmedia.commerce.application.port.in;

import java.util.Map;

/**
 * Read-model / DTO for a cart line. Field names match the {@code CartItem} TypeScript type in
 * {@code Frontend/src/types/index.ts} and API-CONTRACT.md §6. Commerce ADD §6.
 */
public record CartItemView(
    String id,
    String kind,
    String refId,
    String title,
    String subtitle,
    String image,
    MoneyView price,
    int quantity,
    boolean stackable,
    Map<String, Object> metadata) {}
