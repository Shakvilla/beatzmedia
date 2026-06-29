package org.shakvilla.beatzmedia.catalog.domain;

/**
 * A track entry within a release. Tracks the ordered position and per-track price. Domain value
 * object; no framework imports. Catalog ADD §3.
 */
public record ReleaseTrack(String trackId, int position, long priceMinor) {}
