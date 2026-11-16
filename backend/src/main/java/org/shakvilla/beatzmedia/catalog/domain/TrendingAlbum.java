package org.shakvilla.beatzmedia.catalog.domain;

/** Album ranked for discovery by the combined play count of its published tracks. */
public record TrendingAlbum(Album album, ReleaseType type, long plays) {}
