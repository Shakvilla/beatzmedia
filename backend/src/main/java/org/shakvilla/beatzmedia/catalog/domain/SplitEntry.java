package org.shakvilla.beatzmedia.catalog.domain;

/**
 * Revenue split allocation for a track. The sum of all split percents on a track must be ≤ 100
 * (INV-12). Domain value object; no framework imports.
 */
public record SplitEntry(
    String id,
    String trackId,
    String name,
    String email,
    String role,
    int percent,
    SplitConfirmation confirmation) {}
