package org.shakvilla.beatzmedia.commerce.adapter.in.rest;

/** Request DTO for PATCH /v1/me/cart/items/:lineId. Commerce ADD §5.1. */
public record UpdateCartItemRequest(Integer qty) {}
