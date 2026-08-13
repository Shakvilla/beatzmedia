package org.shakvilla.beatzmedia.media.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.media.application.port.in.UploadCommand;
import org.shakvilla.beatzmedia.media.application.port.out.TranscodeJob;
import org.shakvilla.beatzmedia.media.application.port.out.TranscodeResult;
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
 * Tests for transcode result handling in {@link MediaApplicationService}.
 * Covers the state transitions driven by {@code handleTranscodeResult}.
 */
class TranscodeResultHandlingTest {

  private FakeMediaAssetRepository repository;
  private FakeMediaReadyEvent mediaReadyEvent;
  private MediaApplicationService service;
  private FakeAudioTranscoderPort transcoder;

  @BeforeEach
  void setUp() {
    repository = new FakeMediaAssetRepository();
    mediaReadyEvent = new FakeMediaReadyEvent();
    service = new MediaApplicationService(
        repository,
        new FakeObjectStore(),
        new FakeUrlSigner(),
        new FakeTranscodeJobPort(),
        new FakeVirusScan(),
        new FakeArtworkProcessor(),
        new MagicByteValidator(),
        FakeIds.sequential("asset"),
        FakeClock.fixed(),
        mediaReadyEvent,
        30);
    transcoder = new FakeAudioTranscoderPort();
  }

  private MediaAssetId seedTranscodingAsset() {
    return seedTranscodingAsset(new MediaAssetId("transcode-001"), "track-t1", "hash-t1");
  }

  private MediaAssetId seedTranscodingAsset(MediaAssetId id, String trackRef, String contentHash) {
    MediaAsset asset = new MediaAsset(
        id,
        new OwnerRef("catalog", trackRef),
        MediaKind.AUDIO,
        MediaStatus.TRANSCODING,
        0,
        new ObjectKey("orig", "originals/audio/" + id.value()),
        null, null, null,
        Instant.parse("2026-01-01T00:00:00Z"),
        contentHash);
    repository.save(asset);
    return id;
  }

  /**
   * Mirrors {@code InProcessTranscodeJobAdapter#dispatch} after the Task 12 fix: full + preview
   * transcode, {@code markReady} (via {@code handleTranscodeResult}), and ONLY THEN the lossless
   * (FLAC) transcode — never before markReady, since READY means playable and the FLAC step is the
   * slowest one, needed only for downloads. A failed lossless transcode is logged and swallowed:
   * it must not fail the (already READY) asset.
   */
  private void runJobFor(MediaAssetId id) {
    service.markTranscoding(id);
    ObjectKey original = repository.findById(id).orElseThrow().getOriginalKey();
    int durationSec = transcoder.probeDurationSec(original);
    ObjectKey fullKey = transcoder.transcodeFull(original, id);
    ObjectKey previewKey = transcoder.clipPreview(original, id, 30);
    service.handleTranscodeResult(new TranscodeResult(id, fullKey, previewKey, durationSec, true, null));

    try {
      ObjectKey losslessKey = transcoder.transcodeLossless(original, id);
      service.markLosslessReady(id, losslessKey);
    } catch (Exception ex) {
      // Contained deliberately — see InProcessTranscodeJobAdapter#tryTranscodeLossless.
    }
  }

  @Test
  void aTranscodedAssetAlsoGetsItsLosslessRendition() {
    MediaAssetId id = seedTranscodingAsset(new MediaAssetId("lossless-001"), "track-lossless", "hash-lossless");

    runJobFor(id);

    assertNotNull(
        repository.findById(id).orElseThrow().getLosslessKey(),
        "without this the download endpoint answers 409 DOWNLOAD_NOT_READY forever");
  }

  @Test
  void aFailedLosslessTranscodeStillLeavesTheAssetPlayable() {
    // READY means playable. The FLAC is only needed for downloads, so losing it must not cost the
    // fan playback they already paid for — or cost the artist a release that will not stream.
    MediaAssetId id =
        seedTranscodingAsset(new MediaAssetId("lossless-fail-001"), "track-lossless-fail", "hash-lossless-fail");
    transcoder.failLosslessOnly();

    runJobFor(id);

    MediaAsset asset = repository.findById(id).orElseThrow();
    assertEquals(MediaStatus.READY, asset.getStatus());
    assertNull(asset.getLosslessKey());
  }

  @Test
  void successful_transcode_marks_ready_and_fires_event() {
    MediaAssetId id = seedTranscodingAsset();
    ObjectKey full = new ObjectKey("delivery", "delivery/transcode-001/full.m4a");
    ObjectKey preview = new ObjectKey("delivery", "delivery/transcode-001/preview.m4a");

    service.handleTranscodeResult(new TranscodeResult(id, full, preview, 183, true, null));

    MediaAsset saved = repository.findById(id).orElseThrow();
    assertEquals(MediaStatus.READY, saved.getStatus());
    assertEquals(183, saved.getDurationSec());
    assertEquals(full, saved.getFullKey());
    assertEquals(preview, saved.getPreviewKey());
    assertEquals(1, mediaReadyEvent.getFired().size());
    assertEquals(id, mediaReadyEvent.getFired().get(0).assetId());
  }

  @Test
  void failed_transcode_marks_error_and_does_not_fire_event() {
    MediaAssetId id = seedTranscodingAsset();

    service.handleTranscodeResult(new TranscodeResult(id, null, null, 0, false, "TRANSCODE_FAILED"));

    MediaAsset saved = repository.findById(id).orElseThrow();
    assertEquals(MediaStatus.ERROR, saved.getStatus());
    assertEquals(0, mediaReadyEvent.getFired().size());
  }

  /**
   * B4: Asset must pass through TRANSCODING before reaching READY.
   * Uses a synchronous fake job port that captures the sequence of state snapshots.
   */
  @Test
  void asset_transitions_through_transcoding_before_ready() {
    // Capture state snapshots at each step using a recording fake job port
    List<MediaStatus> stateLog = new ArrayList<>();

    // A synchronous inline TranscodeJobPort that drives markTranscoding + handleTranscodeResult
    // in the same thread (mimics the async worker but synchronously for testing).
    FakeMediaAssetRepository testRepo = new FakeMediaAssetRepository();
    FakeMediaReadyEvent testEvent = new FakeMediaReadyEvent();

    // We build the service with a special job port that records states
    MediaApplicationService[] svcHolder = new MediaApplicationService[1];
    FakeTranscodeJobPort recordingJobPort = new FakeTranscodeJobPort() {
      @Override
      public void submit(TranscodeJob job) {
        super.submit(job);
        // Immediately execute the job synchronously (simulating the worker)
        svcHolder[0].markTranscoding(job.assetId());
        stateLog.add(testRepo.findById(job.assetId()).orElseThrow().getStatus());
        // Now "transcode" succeeds
        ObjectKey full = new ObjectKey("test-delivery", "delivery/" + job.assetId().value() + "/full.m4a");
        ObjectKey preview = new ObjectKey("test-delivery", "delivery/" + job.assetId().value() + "/preview.m4a");
        svcHolder[0].handleTranscodeResult(
            new TranscodeResult(job.assetId(), full, preview, 42, true, null));
        stateLog.add(testRepo.findById(job.assetId()).orElseThrow().getStatus());
      }
    };

    svcHolder[0] = new MediaApplicationService(
        testRepo, new FakeObjectStore(), new FakeUrlSigner(), recordingJobPort,
        new FakeVirusScan(), new FakeArtworkProcessor(), new MagicByteValidator(),
        FakeIds.sequential("b4-asset"), FakeClock.fixed(), testEvent, 30);

    byte[] body = UploadOriginalUseCaseTest.wavBytes();
    svcHolder[0].uploadOriginal(new UploadCommand(
        new OwnerRef("catalog", "track-b4"),
        MediaKind.AUDIO, "track.wav", "audio/wav",
        body.length, new ByteArrayInputStream(body), "hash-b4"));

    // First state snapshot should be TRANSCODING, second should be READY
    assertEquals(2, stateLog.size(), "Expected 2 state snapshots");
    assertEquals(MediaStatus.TRANSCODING, stateLog.get(0),
        "Asset must be TRANSCODING before ffmpeg runs (B4)");
    assertEquals(MediaStatus.READY, stateLog.get(1),
        "Asset must reach READY after transcode");
  }

  @Test
  void transcode_job_enqueued_on_retry() {
    MediaAssetId id = new MediaAssetId("retry-001");
    MediaAsset asset = new MediaAsset(
        id,
        new OwnerRef("catalog", "track-t2"),
        MediaKind.AUDIO,
        MediaStatus.ERROR,
        0,
        new ObjectKey("orig", "originals/audio/retry-001"),
        null, null, null,
        Instant.parse("2026-01-01T00:00:00Z"),
        "hash-retry");
    repository.save(asset);

    FakeTranscodeJobPort jobPort = new FakeTranscodeJobPort();
    MediaApplicationService svc = new MediaApplicationService(
        repository, new FakeObjectStore(), new FakeUrlSigner(), jobPort,
        new FakeVirusScan(), new FakeArtworkProcessor(), new MagicByteValidator(),
        FakeIds.sequential("x"), FakeClock.fixed(), new FakeMediaReadyEvent(), 30);

    svc.enqueueTranscode(id);

    assertEquals(1, jobPort.getSubmitted().size());
    assertEquals(id, jobPort.getSubmitted().get(0).assetId());
    assertEquals(MediaStatus.TRANSCODING, repository.findById(id).orElseThrow().getStatus());
  }
}
