package org.shakvilla.beatzmedia.media.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
  }

  private MediaAssetId seedTranscodingAsset() {
    MediaAssetId id = new MediaAssetId("transcode-001");
    MediaAsset asset = new MediaAsset(
        id,
        new OwnerRef("catalog", "track-t1"),
        MediaKind.AUDIO,
        MediaStatus.TRANSCODING,
        0,
        new ObjectKey("orig", "originals/audio/transcode-001"),
        null, null,
        Instant.parse("2026-01-01T00:00:00Z"),
        "hash-t1");
    repository.save(asset);
    return id;
  }

  @Test
  void successful_transcode_marks_ready_and_fires_event() {
    MediaAssetId id = seedTranscodingAsset();
    ObjectKey hls = new ObjectKey("delivery", "delivery/transcode-001/hls/playlist.m3u8");
    ObjectKey preview = new ObjectKey("delivery", "delivery/transcode-001/preview/preview.m3u8");

    service.handleTranscodeResult(new TranscodeResult(id, hls, preview, 183, true, null));

    MediaAsset saved = repository.findById(id).orElseThrow();
    assertEquals(MediaStatus.READY, saved.getStatus());
    assertEquals(183, saved.getDurationSec());
    assertEquals(hls, saved.getHlsKey());
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
        ObjectKey hls = new ObjectKey("test-delivery", "delivery/" + job.assetId().value() + "/hls/playlist.m3u8");
        ObjectKey preview = new ObjectKey("test-delivery", "delivery/" + job.assetId().value() + "/preview/preview.m3u8");
        svcHolder[0].handleTranscodeResult(
            new TranscodeResult(job.assetId(), hls, preview, 42, true, null));
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
        null, null,
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
