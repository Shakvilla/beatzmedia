package org.shakvilla.beatzmedia.catalog.application.port.in;

import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;

/**
 * Read-only view of a studio release returned to the artist. Catalog ADD §4.1 / LLFR-CATALOG-02.1.
 */
public record StudioReleaseView(
    String id,
    String title,
    ReleaseType type,
    ReleaseStatus status,
    String date,
    int trackCount,
    long streams,
    MoneyView revenue,
    MoneyView price) {}
