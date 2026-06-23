package org.shakvilla.beatzmedia.media.application.port.out;

/**
 * Output port for the in-process transcode job queue. The adapter uses a ManagedExecutor
 * to run jobs off the request thread. ADD §4.2 / ADR (WU-MED-1 decision 1).
 */
public interface TranscodeJobPort {

  /**
   * Enqueue a transcode job. Returns immediately; the job runs on a managed thread.
   *
   * @param job the job specification
   */
  void submit(TranscodeJob job);

  /**
   * Callback invoked by the worker when a job completes (success or failure). Persists the result
   * in its own short @Transactional unit and publishes MediaReady on success.
   *
   * @param result the transcode outcome
   */
  void onResult(TranscodeResult result);
}
