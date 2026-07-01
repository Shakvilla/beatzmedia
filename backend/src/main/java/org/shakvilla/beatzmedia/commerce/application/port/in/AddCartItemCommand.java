package org.shakvilla.beatzmedia.commerce.application.port.in;

import java.util.Map;

/**
 * Command for {@link AddCartItem}. {@code qty} is optional (defaults to 1 / increment 1);
 * {@code metadata} carries {@code licenseTier}/{@code merchVariants} for store items.
 * LLFR-COMMERCE-01.2.
 */
public record AddCartItemCommand(
    String kind, String refId, Integer qty, Map<String, Object> metadata) {}
