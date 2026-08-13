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
    List<TrackDraftView> tracks,
    /**
     * Whether buyers may download this release's audio, straight from {@link
     * org.shakvilla.beatzmedia.catalog.domain.Release#getDownloadable()}. {@code null} means the
     * artist has not chosen yet — a live/published draft-flow client, unlike the public
     * track/album views, needs to tell "not chosen" apart from "chosen: no" to drive the
     * required-choice UI, so this is NOT coerced to {@code false}.
     */
    Boolean downloadable) {}
