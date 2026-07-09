package org.shakvilla.beatzmedia.media.adapter.out.integration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.media.application.port.out.AudioTranscoderPort;
import org.shakvilla.beatzmedia.media.application.port.out.TranscodeJob;
import org.shakvilla.beatzmedia.media.application.port.out.TranscodeJobPort;
import org.shakvilla.beatzmedia.media.application.port.out.TranscodeResult;
import org.shakvilla.beatzmedia.media.application.service.MediaApplicationService;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;

/**
 * In-process transcode job adapter. Runs ffmpeg off the request thread. The worker first
 * transitions the asset to TRANSCODING (B4 — persisted before ffmpeg is invoked) and then calls
 * {@link MediaApplicationService#handleTranscodeResult} with the outcome, which persists the final
 * state in its own {@code @Transactional} unit. ADD §4.2 / ADR (WU-MED-1 §1).
 *
 * <h3>Executor choice — plain {@link ExecutorService}, not the CDI-injected {@code
 * ManagedExecutor} (bug fix, WU-STU-2 as-built).</h3>
 * {@code ManagedExecutor} (MicroProfile Context Propagation) propagates ambient thread contexts —
 * including, via Quarkus's {@code narayana-jta} integration, the CALLER's currently-active JTA
 * transaction — onto the task it runs. {@link #submit} is invoked from inside {@code
 * UploadOriginalUseCase#uploadOriginal}'s own still-open {@code @Transactional} scope (every AUDIO
 * upload, across every module — catalog/studio/podcasts — calls it synchronously as the last step
 * of their own upload use case). With a {@code ManagedExecutor}, the worker thread this spawns gets
 * ASSOCIATED with that SAME still-active transaction while the original request thread is also still
 * using it, which Narayana rejects at commit time with {@code ARJUNA012094}/{@code ARJUNA012107}
 * ("commiting with 2 threads active") — surfaced to the caller as a 500 with "Enlisted connection
 * used without active transaction". A plain, unmanaged {@link ExecutorService} propagates no thread
 * context at all, so the worker never touches the caller's transaction; {@code
 * mediaApplicationService}'s own methods each open their OWN independent transaction as normal
 * (they are {@code @ApplicationScoped}, not request-scoped, so no CDI request-context is needed on
 * the worker thread either). Virtual threads (Java 25) keep this cheap per-submission.
 */
@ApplicationScoped
public class InProcessTranscodeJobAdapter implements TranscodeJobPort {

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final AudioTranscoderPort transcoder;

  @Inject
  MediaApplicationService mediaApplicationService;

  @Inject
  public InProcessTranscodeJobAdapter(AudioTranscoderPort transcoder) {
    this.transcoder = transcoder;
  }

  @Override
  public void submit(TranscodeJob job) {
    executor.submit(() -> {
      // B4: persist TRANSCODING status before invoking ffmpeg so the state machine is correct.
      // This runs in its own @Transactional unit inside markTranscoding.
      mediaApplicationService.markTranscoding(job.assetId());

      TranscodeResult result;
      try {
        int durationSec = transcoder.probeDurationSec(job.original());
        ObjectKey hlsKey = transcoder.transcodeHls(job.original(), job.assetId());
        ObjectKey previewKey =
            transcoder.clipPreviewHls(job.original(), job.assetId(), job.previewSeconds());
        result = new TranscodeResult(job.assetId(), hlsKey, previewKey, durationSec, true, null);
      } catch (Exception ex) {
        result =
            new TranscodeResult(
                job.assetId(), null, null, 0, false, "TRANSCODE_FAILED: " + ex.getMessage());
      }
      onResult(result);
    });
  }

  @Override
  public void onResult(TranscodeResult result) {
    mediaApplicationService.handleTranscodeResult(result);
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
