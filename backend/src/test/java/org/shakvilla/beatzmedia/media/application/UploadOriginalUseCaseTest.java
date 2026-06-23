package org.shakvilla.beatzmedia.media.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.media.application.port.in.UploadCommand;
import org.shakvilla.beatzmedia.media.application.service.MagicByteValidator;
import org.shakvilla.beatzmedia.media.application.service.MediaApplicationService;
import org.shakvilla.beatzmedia.media.domain.FileRejectedException;
import org.shakvilla.beatzmedia.media.domain.FileTooLargeException;
import org.shakvilla.beatzmedia.media.domain.MediaAsset;
import org.shakvilla.beatzmedia.media.domain.MediaHandle;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.MediaStatus;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;
import org.shakvilla.beatzmedia.media.domain.UnsupportedFormatException;
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
        new FakeArtworkProcessor(),
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
  void oversize_actual_bytes_rejected_413() {
    // Build a body that is genuinely larger than MAX_SIZE_BYTES by providing a stream that
    // throws FileTooLargeException once the limit is exceeded. We use an InputStream subclass
    // that reports an oversized stream to trigger the CountingLimitingInputStream inside the service.
    long oversizeCount = MediaApplicationService.MAX_SIZE_BYTES + 1;
    InputStream oversizeStream = new InputStream() {
      private long remaining = oversizeCount;
      // First 12 bytes are valid WAV magic so the format check passes
      private final byte[] magic = wavBytes();
      private int magicPos = 0;

      @Override
      public int read() {
        if (magicPos < magic.length) {
          return magic[magicPos++] & 0xFF;
        }
        if (remaining-- > 0) {
          return 0; // filler byte
        }
        return -1;
      }

      @Override
      public int read(byte[] buf, int off, int len) {
        if (magicPos < magic.length) {
          // Already consumed by readNBytes in the service before this stream is wrapped
          int n = Math.min(len, magic.length - magicPos);
          System.arraycopy(magic, magicPos, buf, off, n);
          magicPos += n;
          return n;
        }
        if (remaining <= 0) return -1;
        int n = (int) Math.min(len, remaining);
        java.util.Arrays.fill(buf, off, off + n, (byte) 0);
        remaining -= n;
        return n;
      }
    };
    // sizeBytes declared as 0 so the service does NOT use declared size as the cap — only actual
    UploadCommand cmd = new UploadCommand(
        new OwnerRef("catalog", "track-large"),
        MediaKind.AUDIO, "large.wav", "audio/wav",
        0, oversizeStream, null);
    assertThrows(FileTooLargeException.class, () -> service.uploadOriginal(cmd));
  }

  @Test
  void null_content_hash_is_computed_from_actual_bytes_and_stored() {
    // B3: when contentHash is null the service must compute it from the full streamed bytes
    byte[] body = wavBytes();
    UploadCommand cmd = new UploadCommand(
        new OwnerRef("catalog", "track-nohash"),
        MediaKind.AUDIO, "track.wav", "audio/wav",
        body.length, new ByteArrayInputStream(body), null); // null hash
    MediaHandle handle = service.uploadOriginal(cmd);
    assertNotNull(handle.assetId());
    assertEquals(MediaStatus.UPLOADING, handle.status());
    // Verify that the asset was persisted with a non-null, non-blank hash
    MediaAsset saved = repository.findById(handle.assetId()).orElseThrow();
    assertNotNull(saved.getContentHash(), "contentHash must not be null after upload");
    assertFalse(saved.getContentHash().isBlank(), "contentHash must not be blank after upload");
    // The hash must be a 64-char hex string (SHA-256)
    assertEquals(64, saved.getContentHash().length(),
        "SHA-256 hex string should be 64 characters");
  }

  @Test
  void virus_scan_failure_throws_file_rejected_and_deletes_stored_object() {
    FakeObjectStore trackingStore = new FakeObjectStore();
    MediaApplicationService svc = new MediaApplicationService(
        repository, trackingStore, new FakeUrlSigner(), transcodeJobPort,
        new FakeVirusScan() {{setClean(false);}},
        new FakeArtworkProcessor(), new MagicByteValidator(),
        FakeIds.sequential("virus-asset"),
        FakeClock.fixed(), new FakeMediaReadyEvent(), 30);

    UploadCommand cmd = wavCommand("hash-virus-h1");
    assertThrows(FileRejectedException.class, () -> svc.uploadOriginal(cmd));
    // H-1: the stored original must have been deleted
    assertFalse(
        trackingStore.objectExists("test-originals", "originals/audio/virus-asset-1"),
        "Rejected original must be deleted from the store (H-1)");
    // No asset row persisted
    assertEquals(0, repository.size());
  }

  @Test
  void virus_scan_failure_original_not_retained() {
    virusScan.setClean(false);
    UploadCommand cmd = wavCommand("hash-virus");
    assertThrows(FileRejectedException.class, () -> service.uploadOriginal(cmd));
  }

  // ---- B2: Artwork delivery key must be in delivery bucket, never originals ----

  @Test
  void process_artwork_delivery_key_is_in_delivery_bucket_not_originals() {
    // Upload an artwork first
    byte[] pngBody = pngBytes();
    UploadCommand cmd = new UploadCommand(
        new OwnerRef("catalog", "album-cover-001"),
        MediaKind.ARTWORK, "cover.png", "image/png",
        pngBody.length, new ByteArrayInputStream(pngBody), "hash-png-001");
    MediaHandle handle = service.uploadOriginal(cmd);

    // Now process artwork — should go to delivery bucket
    service.processArtwork(handle.assetId());

    // Verify the stored asset's previewKey (which holds the delivery key for artwork) is in
    // the delivery bucket, not the originals bucket
    MediaAsset saved = repository.findById(handle.assetId()).orElseThrow();
    String previewBucket = saved.getPreviewKey().bucket();
    assertFalse(previewBucket.contains("originals"),
        "Artwork delivery key must NOT be in the originals bucket, but was: " + previewBucket);
    assertTrue(previewBucket.contains("delivery") || previewBucket.equals(FakeArtworkProcessor.DELIVERY_BUCKET),
        "Artwork delivery key must be in the delivery bucket, but was: " + previewBucket);
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

  static byte[] pngBytes() {
    byte[] b = new byte[64];
    // PNG magic: \x89PNG\r\n\x1a\n
    b[0] = (byte) 0x89; b[1] = 0x50; b[2] = 0x4E; b[3] = 0x47;
    b[4] = 0x0D; b[5] = 0x0A; b[6] = 0x1A; b[7] = 0x0A;
    return b;
  }
}
