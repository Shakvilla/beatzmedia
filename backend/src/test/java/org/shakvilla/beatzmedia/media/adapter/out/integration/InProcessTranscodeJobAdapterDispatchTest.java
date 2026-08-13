package org.shakvilla.beatzmedia.media.adapter.out.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.media.application.port.out.TranscodeJob;
import org.shakvilla.beatzmedia.media.application.service.MagicByteValidator;
import org.shakvilla.beatzmedia.media.application.service.MediaApplicationService;
import org.shakvilla.beatzmedia.media.domain.MediaAsset;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.MediaStatus;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;
import org.shakvilla.beatzmedia.media.fakes.FakeArtworkProcessor;
import org.shakvilla.beatzmedia.media.fakes.FakeAudioTranscoderPort;
import org.shakvilla.beatzmedia.media.fakes.FakeMediaAssetRepository;
import org.shakvilla.beatzmedia.media.fakes.FakeMediaReadyEvent;
import org.shakvilla.beatzmedia.media.fakes.FakeObjectStore;
import org.shakvilla.beatzmedia.media.fakes.FakeTranscodeJobPort;
import org.shakvilla.beatzmedia.media.fakes.FakeUrlSigner;
import org.shakvilla.beatzmedia.media.fakes.FakeVirusScan;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Drives {@link InProcessTranscodeJobAdapter} itself — the actual production call site — rather
 * than a hand-written re-implementation of {@code #dispatch}. {@code
 * TranscodeResultHandlingTest#runJobFor} mirrors the adapter's sequencing in a test helper, so
 * deleting {@code tryTranscodeLossless(job)} from the real adapter leaves that suite green while
 * every download would answer {@code 409 DOWNLOAD_NOT_READY} forever (Task 12). These tests
 * construct the real adapter, inject a fake {@link org.shakvilla.beatzmedia.media.application.port.out.AudioTranscoderPort}
 * and a real {@link MediaApplicationService} wired to in-memory fakes, submit a job through
 * {@link InProcessTranscodeJobAdapter#submit}, and wait for the adapter's own virtual-thread
 * worker to finish — so a regression in the adapter's wiring is caught here, not just in the
 * test's idea of what the adapter should do.
 */
class InProcessTranscodeJobAdapterDispatchTest {

  private static final int PREVIEW_SECONDS = 30;

  /** No transaction ever active on the test thread, so {@code submit} dispatches immediately. */
  private static final TransactionSynchronizationRegistry NO_ACTIVE_TRANSACTION =
      new TransactionSynchronizationRegistry() {
        @Override
        public Object getTransactionKey() {
          return null;
        }

        @Override
        public void putResource(Object key, Object value) {
          throw new UnsupportedOperationException("no active transaction in this test");
        }

        @Override
        public Object getResource(Object key) {
          throw new UnsupportedOperationException("no active transaction in this test");
        }

        @Override
        public void registerInterposedSynchronization(Synchronization sync) {
          throw new UnsupportedOperationException("no active transaction in this test");
        }

        @Override
        public int getTransactionStatus() {
          return jakarta.transaction.Status.STATUS_NO_TRANSACTION;
        }

        @Override
        public void setRollbackOnly() {
          throw new UnsupportedOperationException("no active transaction in this test");
        }

        @Override
        public boolean getRollbackOnly() {
          throw new UnsupportedOperationException("no active transaction in this test");
        }
      };

  private static MediaAsset seedUploadingAsset(FakeMediaAssetRepository repository, MediaAssetId id) {
    MediaAsset asset =
        new MediaAsset(
            id,
            new OwnerRef("catalog", "track-" + id.value()),
            MediaKind.AUDIO,
            MediaStatus.UPLOADING,
            0,
            new ObjectKey("orig", "originals/audio/" + id.value()),
            null,
            null,
            null,
            Instant.parse("2026-01-01T00:00:00Z"),
            "hash-" + id.value());
    repository.save(asset);
    return asset;
  }

  private static InProcessTranscodeJobAdapter buildAdapter(
      FakeAudioTranscoderPort transcoder, MediaApplicationService service) {
    InProcessTranscodeJobAdapter adapter = new InProcessTranscodeJobAdapter(transcoder);
    // CDI never runs in this plain unit test, so the @Inject fields are wired by hand. Both are
    // package-private, and this test class lives in the adapter's own package for exactly that
    // reason — plain field assignment, no reflection needed.
    adapter.mediaApplicationService = service;
    adapter.txRegistry = NO_ACTIVE_TRANSACTION;
    return adapter;
  }

  /**
   * Counts down the moment the real {@link MediaApplicationService#markLosslessReady} persists —
   * the last thing the success path does — so the test can wait for the async worker
   * deterministically instead of polling or sleeping.
   */
  private static final class TrackingMediaApplicationService extends MediaApplicationService {

    private final CountDownLatch losslessPersisted;

    TrackingMediaApplicationService(
        FakeMediaAssetRepository repository,
        FakeTranscodeJobPort jobPort,
        FakeMediaReadyEvent readyEvent,
        CountDownLatch losslessPersisted) {
      super(
          repository,
          new FakeObjectStore(),
          new FakeUrlSigner(),
          jobPort,
          new FakeVirusScan(),
          new FakeArtworkProcessor(),
          new MagicByteValidator(),
          FakeIds.sequential("dispatch-test"),
          FakeClock.fixed(),
          readyEvent,
          PREVIEW_SECONDS);
      this.losslessPersisted = losslessPersisted;
    }

    @Override
    public void markLosslessReady(MediaAssetId assetId, ObjectKey losslessKey) {
      super.markLosslessReady(assetId, losslessKey);
      losslessPersisted.countDown();
    }
  }

  /**
   * Counts down as soon as the fake transcoder's {@code transcodeLossless} is entered/exits
   * (success or failure) — the adapter calls {@code handleTranscodeResult} strictly before this,
   * so by the time this fires the asset's READY status and (null-or-not) lossless key are already
   * durably persisted.
   */
  private static final class TrackingAudioTranscoderPort extends FakeAudioTranscoderPort {

    private final CountDownLatch losslessAttempted;

    TrackingAudioTranscoderPort(CountDownLatch losslessAttempted) {
      this.losslessAttempted = losslessAttempted;
    }

    @Override
    public ObjectKey transcodeLossless(ObjectKey original, MediaAssetId id) {
      try {
        return super.transcodeLossless(original, id);
      } finally {
        losslessAttempted.countDown();
      }
    }
  }

  @Test
  void theRealAdapterRunsTheLosslessTranscodeAfterTheAssetIsReady() throws Exception {
    FakeMediaAssetRepository repository = new FakeMediaAssetRepository();
    MediaAssetId id = new MediaAssetId("dispatch-lossless-001");
    seedUploadingAsset(repository, id);

    CountDownLatch losslessPersisted = new CountDownLatch(1);
    TrackingMediaApplicationService service =
        new TrackingMediaApplicationService(
            repository, new FakeTranscodeJobPort(), new FakeMediaReadyEvent(), losslessPersisted);
    FakeAudioTranscoderPort transcoder = new FakeAudioTranscoderPort();
    InProcessTranscodeJobAdapter adapter = buildAdapter(transcoder, service);

    adapter.submit(
        new TranscodeJob(id, new ObjectKey("orig", "originals/audio/" + id.value()), PREVIEW_SECONDS));

    assertTrue(
        losslessPersisted.await(10, TimeUnit.SECONDS),
        "the real adapter must run the lossless transcode and persist it after the asset is ready");

    MediaAsset asset = repository.findById(id).orElseThrow();
    assertEquals(MediaStatus.READY, asset.getStatus());
    assertNotNull(
        asset.getLosslessKey(),
        "without the real adapter calling tryTranscodeLossless, the download endpoint answers "
            + "409 DOWNLOAD_NOT_READY forever");
    assertEquals("delivery/" + id.value() + "/lossless.flac", asset.getLosslessKey().key());
  }

  @Test
  void theRealAdapterContainsALosslessFailureAndLeavesTheAssetReady() throws Exception {
    FakeMediaAssetRepository repository = new FakeMediaAssetRepository();
    MediaAssetId id = new MediaAssetId("dispatch-lossless-fail-001");
    seedUploadingAsset(repository, id);

    CountDownLatch losslessAttempted = new CountDownLatch(1);
    TrackingMediaApplicationService service =
        new TrackingMediaApplicationService(
            repository, new FakeTranscodeJobPort(), new FakeMediaReadyEvent(), new CountDownLatch(1));
    TrackingAudioTranscoderPort transcoder = new TrackingAudioTranscoderPort(losslessAttempted);
    transcoder.failLosslessOnly();
    InProcessTranscodeJobAdapter adapter = buildAdapter(transcoder, service);

    adapter.submit(
        new TranscodeJob(id, new ObjectKey("orig", "originals/audio/" + id.value()), PREVIEW_SECONDS));

    assertTrue(
        losslessAttempted.await(10, TimeUnit.SECONDS),
        "the real adapter must actually attempt the lossless transcode, not skip it");

    MediaAsset asset = repository.findById(id).orElseThrow();
    assertEquals(
        MediaStatus.READY,
        asset.getStatus(),
        "READY means playable — a failed FLAC rendition must not fail the asset");
    assertNull(asset.getLosslessKey(), "a failed lossless transcode leaves the key null, not stale");
  }
}
