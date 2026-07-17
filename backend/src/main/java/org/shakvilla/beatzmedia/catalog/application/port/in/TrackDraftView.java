package org.shakvilla.beatzmedia.catalog.application.port.in;

/**
 * View of a single track within a {@link StudioReleaseDetailView}'s ordered track list. No splits
 * this WU — deferred to WU-CAT-6. Catalog ADD §4.1 / WU-CAT-5.
 */
public record TrackDraftView(
    String trackId, String title, int duration, String status, int position, MoneyView price) {}
