package org.shakvilla.beatzmedia.commerce.application.port.in;

import java.util.List;

/**
 * Read-model / DTO for the cart response: {@code { items, subtotal, fee, total, count }}.
 * LLFR-COMMERCE-01.1 / API-CONTRACT.md §6. Commerce ADD §4.1.
 */
public record CartView(
    List<CartItemView> items, MoneyView subtotal, MoneyView fee, MoneyView total, int count) {}
