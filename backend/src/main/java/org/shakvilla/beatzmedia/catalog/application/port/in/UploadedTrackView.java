package org.shakvilla.beatzmedia.catalog.application.port.in;

/**
 * View returned after a successful track upload to a release. Catalog ADD §4.1 /
 * LLFR-CATALOG-02.4.
 */
public record UploadedTrackView(
    String id,
    String title,
    int duration,
    String status,
    int progress,
    String src,
    MoneyView price,
    boolean explicit) {}
