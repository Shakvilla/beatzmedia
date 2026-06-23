package org.shakvilla.beatzmedia.media.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.media.application.port.in.UploadCommand;
import org.shakvilla.beatzmedia.media.application.service.MagicByteValidator;
import org.shakvilla.beatzmedia.media.application.service.MediaApplicationService;
import org.shakvilla.beatzmedia.media.domain.FileRejectedException;
import org.shakvilla.beatzmedia.media.domain.FileTooLargeException;
import org.shakvilla.beatzmedia.media.domain.MediaHandle;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.MediaStatus;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;
import org.shakvilla.beatzmedia.media.domain.UnsupportedFormatException;
import org.shakvilla.beatzmedia.media.fakes.FakeMediaAssetRepository;
import org.shakvilla.beatzmedia.media.fakes.FakeMediaReadyEvent;
import org.shakvilla.beatzmedia.media.fakes.FakeObjectStore;
import org.shakvilla.beatzmedia.media.fakes.FakeTranscodeJobPort;
import org.shakvilla.beatzmedia.media.fakes.FakeUrlSigner;
import org.shakvilla.beatzmedia.media.fakes.FakeVirusScan;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * LLFR-MEDIA-01.1 acceptance criteria:
 * - WAV/FLAC accepted; returns UPLOADING MediaHandle with assetId.
 * - Non-WAV/FLAC → 422 UNSUPPORTED_FORMAT.
 * - Oversize → 413.
 * - Re-upload of identical (ownerRef, contentHash) returns existing handle (idempotency).
 * - File rejected by virus scan → FILE_REJECTED.
 */
class UploadOriginalUseCaseTest {

  private FakeMediaAssetRepository repository;
  private FakeVirusScan virusScan;
  private FakeTranscodeJobPort transcodeJobPort;
  private MediaApplicationService service;

  @BeforeEach
  void setUp() {
    repository = new FakeMediaAssetRepository();
    virusScan = new FakeVirusScan();
    transcodeJobPort = new FakeTranscodeJobPort();
    service = new MediaApplicationService(
        repository,
        new FakeObjectStore(),
        new FakeUrlSigner(),
        transcodeJobPort,
        virusScan,
        new MagicByteValidator(),
        FakeIds.sequential("asset"),
        FakeClock.fixed(),
        new FakeMediaReadyEvent(),
        30);
  }

  // ---- Accept cases ----

  @Test
  void wav_upload_returns_uploading_handle() {
    UploadCommand cmd = wavCommand("hash-wav-001");
    MediaHandle handle = service.uploadOriginal(cmd);
    assertNotNull(handle.assetId());
    assertEquals(MediaKind.AUDIO, handle.kind());
    assertEquals(MediaStatus.UPLOADING, handle.status());
  }

  @Test
  void flac_upload_returns_uploading_handle() {
    UploadCommand cmd = flacCommand("hash-flac-001");
    MediaHandle handle = service.uploadOriginal(cmd);
    assertNotNull(handle.assetId());
    assertEquals(MediaStatus.UPLOADING, handle.status());
  }

  @Test
  void wav_upload_enqueues_transcode_job() {
    service.uploadOriginal(wavCommand("hash-wav-002"));
    assertEquals(1, transcodeJobPort.getSubmitted().size());
  }

  // ---- Reject cases ----

  @Test
  void mp3_upload_rejected_422_unsupported_format() {
    byte[] mp3Bytes = mp3Bytes();
    UploadCommand cmd = new UploadCommand(
        new OwnerRef("catalog", "track-mp3"),
        MediaKind.AUDIO, "track.mp3", "audio/mpeg",
        mp3Bytes.length, new ByteArrayInputStream(mp3Bytes), "hash-mp3");
    assertThrows(UnsupportedFormatException.class, () -> service.uploadOriginal(cmd));
    assertEquals(0, repository.size()); // no row persisted
  }

  @Test
  void exe_upload_rejected_422_unsupported_format() {
    byte[] exeBytes = new byte[]{0x4D, 0x5A, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    UploadCommand cmd = new UploadCommand(
        new OwnerRef("catalog", "track-exe"),
        MediaKind.AUDIO, "virus.exe", "application/octet-stream",
        exeBytes.length, new ByteArrayInputStream(exeBytes), "hash-exe");
    assertThrows(UnsupportedFormatException.class, () -> service.uploadOriginal(cmd));
  }

  @Test
  void oversize_file_rejected_413() {
    byte[] tiny = wavBytes();
    // Declare a size larger than the limit
    long oversizeBytes = MediaApplicationService.MAX_SIZE_BYTES + 1;
    UploadCommand cmd = new UploadCommand(
        new OwnerRef("catalog", "track-large"),
        MediaKind.AUDIO, "large.wav", "audio/wav",
        oversizeBytes, new ByteArrayInputStream(tiny), "hash-large");
    assertThrows(FileTooLargeException.class, () -> service.uploadOriginal(cmd));
  }

  @Test
  void virus_scan_failure_throws_file_rejected() {
    virusScan.setClean(false);
    UploadCommand cmd = wavCommand("hash-virus");
    assertThrows(FileRejectedException.class, () -> service.uploadOriginal(cmd));
  }

  // ---- Idempotency ----

  @Test
  void re_upload_same_owner_and_hash_returns_existing_handle() {
    UploadCommand first = wavCommand("hash-idem-001");
    MediaHandle firstHandle = service.uploadOriginal(first);
    assertEquals(1, repository.size());

    UploadCommand second = wavCommand("hash-idem-001"); // same hash
    MediaHandle secondHandle = service.uploadOriginal(second);

    assertEquals(1, repository.size(), "No second row should be created");
    assertEquals(firstHandle.assetId(), secondHandle.assetId(), "Same assetId returned");
  }

  // ---- Helpers ----

  private UploadCommand wavCommand(String contentHash) {
    byte[] body = wavBytes();
    return new UploadCommand(
        new OwnerRef("catalog", "track-001"),
        MediaKind.AUDIO, "track.wav", "audio/wav",
        body.length, new ByteArrayInputStream(body), contentHash);
  }

  private UploadCommand flacCommand(String contentHash) {
    byte[] body = flacBytes();
    return new UploadCommand(
        new OwnerRef("catalog", "track-002"),
        MediaKind.AUDIO, "track.flac", "audio/flac",
        body.length, new ByteArrayInputStream(body), contentHash);
  }

  static byte[] wavBytes() {
    byte[] b = new byte[64];
    // RIFF....WAVE
    b[0] = 0x52; b[1] = 0x49; b[2] = 0x46; b[3] = 0x46;
    b[4] = 0x3C; b[5] = 0x00; b[6] = 0x00; b[7] = 0x00;
    b[8] = 0x57; b[9] = 0x41; b[10] = 0x56; b[11] = 0x45;
    return b;
  }

  static byte[] flacBytes() {
    byte[] b = new byte[64];
    b[0] = 0x66; b[1] = 0x4C; b[2] = 0x61; b[3] = 0x43; // fLaC
    return b;
  }

  static byte[] mp3Bytes() {
    byte[] b = new byte[64];
    b[0] = (byte) 0xFF; b[1] = (byte) 0xFB; // ID3/MP3 sync
    return b;
  }
}
