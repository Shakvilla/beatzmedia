package org.shakvilla.beatzmedia.commerce.adapter.in.rest;

import java.util.Map;

/**
 * Request DTO for POST /v1/me/cart/items. {@code id} is accepted per API-CONTRACT.md but ignored
 * server-side (the line id is always derived server-side from {@code kind:refId} — INV-11, never
 * trust client-supplied identity for pricing). Commerce ADD §5.1.
 */
public record AddCartItemRequest(String id, String kind, String refId, Integer qty, Map<String, Object> metadata) {}
