package org.shakvilla.beatzmedia.catalog.application.port.in;

/**
 * Input port: keep the fan-facing {@code album} row in step with a release's lifecycle.
 *
 * <p><strong>Why this exists.</strong> Publishing a release wrote {@code release},
 * {@code release_track} and {@code track} — and never an {@code album}. The only
 * {@code INSERT INTO album} in the codebase was the dev seed, so on any database without it the
 * table was empty forever, taking six fan surfaces with it: the "New releases" and featured rails,
 * {@code GET /v1/albums/:id}, an artist's album list, saved albums, and the whole album detail
 * route. Every endpoint answered 200, so the rails just read as "no data yet".
 *
 * <p>Called from the release lifecycle events rather than from the publish path directly, so a
 * release that goes live via the scheduled sweep is projected identically to one an admin approves.
 */
public interface ProjectReleaseAlbum {

  /**
   * Projects a live release into an album. Idempotent — re-projecting an already-projected release
   * updates it in place, so a replayed event or a second approval is harmless.
   */
  void project(String releaseId);

  /** Removes the album for a release that is no longer live. Idempotent. */
  void remove(String releaseId);
}
