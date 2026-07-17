package org.shakvilla.beatzmedia.catalog.application.port.in;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;

/**
 * Input port: finalize a draft release ({@code POST /v1/studio/releases/:id/submit}) — {@code
 * draft -> in_review}. Validates INV-12 track count, recomputes the INV-5 list price, and appends
 * a {@code SUBMIT_RELEASE} audit entry (INV-10), all in one transaction. Idempotency-Key required
 * — a replay with the same key returns the same view without re-transitioning. Not-draft throws
 * {@link org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException} (409). Catalog ADD
 * §4.1 / WU-CAT-5.
 */
public interface FinalizeRelease {

  StudioReleaseDetailView finalize(ReleaseId id, ArtistId artistId, String idempotencyKey);
}
