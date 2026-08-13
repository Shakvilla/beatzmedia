package org.shakvilla.beatzmedia.media.adapter.out.integration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

import org.jboss.logging.Logger;
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

  private static final Logger LOG = Logger.getLogger(InProcessTranscodeJobAdapter.class);

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final AudioTranscoderPort transcoder;

  @Inject
  MediaApplicationService mediaApplicationService;

  @Inject
  TransactionSynchronizationRegistry txRegistry;

  @Inject
  public InProcessTranscodeJobAdapter(AudioTranscoderPort transcoder) {
    this.transcoder = transcoder;
  }

  /**
   * Hand the job to the worker, but never before the caller's transaction has COMMITTED.
   *
   * <p>{@code submit} is called from inside {@code uploadOriginal}'s still-open {@code
   * @Transactional} scope, and the worker opens its OWN transaction to load the asset. Dispatching
   * immediately is therefore a race the worker usually wins: it read {@code media_asset} before the
   * inserting transaction committed, got {@code NotFoundException}, and — because the throwable was
   * parked in an unread {@code Future} — died silently, stranding every audio upload in {@code
   * UPLOADING} with an empty delivery bucket and no log line. Observed at ~82ms from queue to
   * death, i.e. it lost essentially every time.
   *
   * <p>So: if a transaction is active, register an interposed synchronization and dispatch in
   * {@code afterCompletion} only on {@code STATUS_COMMITTED} — a rolled-back upload must not
   * transcode an asset that no longer exists. With no active transaction (unit tests, an already
   * committed re-enqueue) dispatch straight away.
   */
  @Override
  public void submit(TranscodeJob job) {
    if (txRegistry.getTransactionKey() != null) {
      txRegistry.registerInterposedSynchronization(new Synchronization() {
        @Override
        public void beforeCompletion() {
          // nothing to do — the point is to wait for the commit
        }

        @Override
        public void afterCompletion(int status) {
          if (status == Status.STATUS_COMMITTED) {
            dispatch(job);
          } else {
            LOG.infof(
                "transcode: caller rolled back, not dispatching asset=%s", job.assetId().value());
          }
        }
      });
      return;
    }
    dispatch(job);
  }

  private void dispatch(TranscodeJob job) {
    LOG.infof("transcode: queued asset=%s original=%s", job.assetId().value(), job.original().key());
    executor.submit(() -> {
      // Everything is inside this try. `markTranscoding` and `onResult` used to sit OUTSIDE it,
      // and `executor.submit` parks any throwable in a Future that nobody reads — so if either
      // threw, the asset stayed UPLOADING forever with not one line in the log. Uploads appeared
      // to hang indefinitely, and the wizard reported it as "metadata missing". A background job
      // that can fail invisibly is worse than one that fails loudly.
      try {
        // B4: persist TRANSCODING status before invoking ffmpeg so the state machine is correct.
        // This runs in its own @Transactional unit inside markTranscoding.
        mediaApplicationService.markTranscoding(job.assetId());

        TranscodeResult result;
        try {
          int durationSec = transcoder.probeDurationSec(job.original());
          ObjectKey fullKey = transcoder.transcodeFull(job.original(), job.assetId());
          ObjectKey previewKey =
              transcoder.clipPreview(job.original(), job.assetId(), job.previewSeconds());
          LOG.infof(
              "transcode: ok asset=%s duration=%ds full=%s preview=%s",
              job.assetId().value(), durationSec, fullKey.key(), previewKey.key());
          result = new TranscodeResult(job.assetId(), fullKey, previewKey, durationSec, true, null);
        } catch (Exception ex) {
          LOG.errorf(ex, "transcode: ffmpeg stage failed for asset=%s", job.assetId().value());
          result =
              new TranscodeResult(
                  job.assetId(), null, null, 0, false, "TRANSCODE_FAILED: " + ex.getMessage());
        }
        onResult(result);
        if (result.ok()) {
          // AFTER markReady (via onResult above), never before: READY means playable, and the
          // FLAC transcode is the slowest stage here — needed only for downloads, so it must not
          // delay playback for every stream. See MediaAsset#markLosslessReady javadoc.
          tryTranscodeLossless(job);
        }
      } catch (Throwable fatal) {
        // markTranscoding / onResult themselves failed (DB, CDI context, OOM…). Nothing further
        // can persist a state for this asset, so at minimum make it visible instead of silent.
        LOG.errorf(
            fatal,
            "transcode: worker died outside the ffmpeg stage for asset=%s — asset is left in its "
                + "previous state and will need re-enqueueing",
            job.assetId().value());
      }
    });
  }

  @Override
  public void onResult(TranscodeResult result) {
    mediaApplicationService.handleTranscodeResult(result);
  }

  /**
   * Lossless (FLAC) delivery rendition — the download payload. Deliberately a best-effort,
   * contained step: a failure here must NOT fail the asset. Losing the FLAC costs a download
   * (recoverable — the download endpoint answers {@code 409 DOWNLOAD_NOT_READY} and it can be
   * regenerated later); marking the asset ERROR here would cost the artist a release that will
   * not stream at all, over a rendition most listeners never download. So: log and move on,
   * leaving {@code losslessKey} null on an otherwise READY asset.
   */
  private void tryTranscodeLossless(TranscodeJob job) {
    try {
      ObjectKey losslessKey = transcoder.transcodeLossless(job.original(), job.assetId());
      mediaApplicationService.markLosslessReady(job.assetId(), losslessKey);
      LOG.infof(
          "transcode: lossless ok asset=%s lossless=%s", job.assetId().value(), losslessKey.key());
    } catch (Exception ex) {
      LOG.errorf(
          ex,
          "transcode: lossless stage failed for asset=%s — asset stays READY, download "
              + "unavailable until this is re-run",
          job.assetId().value());
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
