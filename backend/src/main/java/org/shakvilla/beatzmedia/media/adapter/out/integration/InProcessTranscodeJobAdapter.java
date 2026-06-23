package org.shakvilla.beatzmedia.media.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.shakvilla.beatzmedia.media.application.port.out.AudioTranscoderPort;
import org.shakvilla.beatzmedia.media.application.port.out.TranscodeJob;
import org.shakvilla.beatzmedia.media.application.port.out.TranscodeJobPort;
import org.shakvilla.beatzmedia.media.application.port.out.TranscodeResult;
import org.shakvilla.beatzmedia.media.application.service.MediaApplicationService;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;

/**
 * In-process transcode job adapter. Uses MicroProfile ManagedExecutor to run ffmpeg off the
 * request thread. The worker first transitions the asset to TRANSCODING (B4 — persisted before
 * ffmpeg is invoked) and then calls {@link MediaApplicationService#handleTranscodeResult} with the
 * outcome, which persists the final state in its own {@code @Transactional} unit.
 * ADD §4.2 / ADR (WU-MED-1 §1).
 */
@ApplicationScoped
public class InProcessTranscodeJobAdapter implements TranscodeJobPort {

  private final ManagedExecutor executor;
  private final AudioTranscoderPort transcoder;

  @Inject
  MediaApplicationService mediaApplicationService;

  @Inject
  public InProcessTranscodeJobAdapter(ManagedExecutor executor, AudioTranscoderPort transcoder) {
    this.executor = executor;
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
}
