package org.shakvilla.beatzmedia.catalog.application.port.in;

/**
 * Read-model for a concert show on an artist page. Field names match the {@code Show} TypeScript
 * type. Catalog ADD §6 / API-CONTRACT.md §3.
 */
public record ShowView(String date, String city, String venue) {}
