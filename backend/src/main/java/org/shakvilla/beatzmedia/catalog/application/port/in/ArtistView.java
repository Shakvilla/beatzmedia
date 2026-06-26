package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

/**
 * Read-model / DTO for an artist profile. Serialized to JSON by the REST adapter. Field names
 * match the {@code Artist} TypeScript type in {@code Frontend/src/types/index.ts} and
 * {@code API-CONTRACT.md} §3. Catalog ADD §6.
 */
public record ArtistView(
    String id,
    String name,
    String image,
    String coverImage,
    Boolean verified,
    Long monthlyListeners,
    Long followers,
    String bio,
    String location,
    List<String> genres) {}
