package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;

/**
 * Read-only detail view of a studio release returned to the artist by {@code GET /:id} and every
 * mutating draft-flow endpoint. Additive superset of {@link StudioReleaseView} (the list view is
 * unchanged) — adds draft-authoring fields and the ordered track list. Catalog ADD §4.1 /
 * WU-CAT-5.
 */
public record StudioReleaseDetailView(
    String id,
    String title,
    ReleaseType type,
    ReleaseStatus status,
    String date,
    int trackCount,
    long streams,
    MoneyView revenue,
    MoneyView price,
    String genre,
    String description,
    String visibility,
    String scheduledAt,
    List<TrackDraftView> tracks) {}
