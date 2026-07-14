package org.shakvilla.beatzmedia.catalog.adapter.in.rest;

import java.util.List;

/**
 * Request DTO for POST /v1/catalog/resolve. Every field is nullable — a missing or null list is
 * treated as empty by the application layer. Catalog ADD §5.1 / §6.
 */
public record ResolveCatalogRequest(
    List<String> trackIds,
    List<String> artistIds,
    List<String> albumIds,
    List<String> playlistIds) {}
