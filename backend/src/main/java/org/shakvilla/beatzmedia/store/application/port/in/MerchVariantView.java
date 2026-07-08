package org.shakvilla.beatzmedia.store.application.port.in;

import java.util.List;

/**
 * Read-model / DTO for a merch product's configurable attribute (size, colour, …). Field names
 * match the {@code MerchVariant} TypeScript type in {@code Frontend/src/types/index.ts} and
 * {@code API-CONTRACT.md} §7. Store ADD §6.
 */
public record MerchVariantView(String label, List<String> options) {}
