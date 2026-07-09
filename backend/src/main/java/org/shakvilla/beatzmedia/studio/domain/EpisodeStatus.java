package org.shakvilla.beatzmedia.studio.domain;

/**
 * Episode lifecycle status — sealed 3-state FSM (INV-7). Verbatim from {@code studio-data.ts}
 * {@code EpisodeStatus}. Studio ADD §3.
 *
 * <pre>
 *   draft --> scheduled  (schedule: date is future)
 *   draft --> published  (publish now)
 *   scheduled --> published  (scheduler at scheduledAt, INV-7, exactly-once)
 *   scheduled --> draft  (unschedule via PATCH)
 * </pre>
 *
 * {@code published} is terminal: no further status transition is permitted (only delete, guarded
 * by OQ-8 / 409 {@code EPISODE_PUBLISHED} when the episode has any owner).
 */
public enum EpisodeStatus {
  draft,
  scheduled,
  published
}
