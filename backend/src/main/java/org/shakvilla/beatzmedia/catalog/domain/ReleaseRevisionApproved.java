package org.shakvilla.beatzmedia.catalog.domain;

/** The artist's approved revision is now the active catalog data. */
public record ReleaseRevisionApproved(String releaseId, String artistId) {}
