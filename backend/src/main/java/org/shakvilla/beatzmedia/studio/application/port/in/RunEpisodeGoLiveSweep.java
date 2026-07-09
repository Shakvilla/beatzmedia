package org.shakvilla.beatzmedia.studio.application.port.in;

/**
 * Input port: sweeps all {@code scheduled} episodes whose {@code scheduledAt} has passed and
 * transitions each to {@code published} (INV-7). Invoked exclusively by the {@code
 * EpisodeGoLiveJob} scheduler adapter (job name {@code studio.episode-go-live}) — never exposed on
 * an HTTP path. Mirrors {@code catalog.application.port.in.RunGoLiveSweep} exactly. Studio ADD §4.1
 * / §8 / §9 (WU-STU-2).
 *
 * <p>Idempotent and safe under restart/concurrency: each candidate episode is guard-checked by the
 * underlying {@code Episode#goLive} transition (only {@code status = scheduled} rows are eligible),
 * so re-running the sweep never fires an episode published twice.
 */
public interface RunEpisodeGoLiveSweep {

  /**
   * Runs one sweep. Returns the number of episodes successfully transitioned to {@code published}.
   * Episodes that raced to a different terminal state between enumeration and transition are
   * skipped, not counted as failures.
   */
  int run();
}
