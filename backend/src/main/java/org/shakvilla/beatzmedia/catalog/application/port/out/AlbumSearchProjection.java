package org.shakvilla.beatzmedia.catalog.application.port.out;

import org.shakvilla.beatzmedia.catalog.domain.Album;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;

/**
 * Outbound port: keep an album's search document in step with the {@code album} row itself.
 *
 * <p><strong>Why this exists (GAP-27).</strong> Taking a release down hid its <em>track</em>
 * documents but left the <em>album</em> document untouched — same {@code indexed_at}, still
 * {@code visible = true}. Meanwhile {@code ProjectReleaseAlbumService.remove} correctly deleted the
 * {@code album} row. The result, observed on a live takedown:
 *
 * <pre>
 * GET /v1/search?q=Test  ->  topResult: { entityType: "ALBUM", title: "Test", ... }
 * GET /v1/albums/{id}    ->  404 ALBUM_NOT_FOUND
 * </pre>
 *
 * <p>So a release pulled for a copyright claim stayed the top result in public search and dead-ended
 * on a 404. Nothing recovered from this on its own: the periodic reindex loads from the {@code album}
 * table, which no longer had the row, and indexing is upsert-only — a document nothing loads is a
 * document nothing can correct. It would have stayed visible indefinitely.
 *
 * <p><strong>Why the projection owns this rather than the search observer.</strong>
 * {@code ReleaseWentLive} and {@code ContentTakenDown} are each observed by two independent
 * {@code AFTER_SUCCESS} observers, and CDI does not order them. Had the search observer indexed the
 * album, it could have run before the album row was written and found nothing to index. Giving the
 * one component that writes the row the job of writing the document removes the ordering question
 * instead of answering it.
 */
public interface AlbumSearchProjection {

  /** Upserts the album's search document. Idempotent. */
  void index(Album album);

  /**
   * Deletes the album's search document.
   *
   * <p>A hard delete, not the soft {@code visible = false} used for tracks: a track outlives its
   * release's takedown and the backfill keeps reconciling it, whereas the album row is gone, so a
   * hidden document would be a permanently unreconcilable orphan. Idempotent — a no-op when absent.
   */
  void remove(AlbumId id);
}
