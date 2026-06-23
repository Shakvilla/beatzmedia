package org.shakvilla.beatzmedia.media.it;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.media.adapter.out.integration.FfmpegAudioTranscoderAdapter;
import org.shakvilla.beatzmedia.media.adapter.out.integration.S3ObjectStoreAdapter;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * LLFR-MEDIA-01.2 — real ffmpeg transcode integration test.
 *
 * <p>This test is SKIPPED when ffmpeg is not on PATH (e.g. local developer machines).
 * It runs in CI where ffmpeg is installed (container image or GitHub Actions runner).
 * Uses Testcontainers MinIO as the object store. ADD §11.
 */
@Tag("integration")
@Testcontainers
class RealTranscodeIT {

  private static final String BUCKET_ORIGINALS = "beatz-media-originals";
  private static final String BUCKET_DELIVERY = "beatz-media-delivery";

  @Container
  static MinIOContainer minio =
      new MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z")
          .withUserName("minioadmin")
          .withPassword("minioadmin");

  private static S3Client s3Client;
  private static S3ObjectStoreAdapter objectStore;
  private static FfmpegAudioTranscoderAdapter transcoder;

  @BeforeAll
  static void setUp() {
    // Skip the entire test class if ffmpeg is not on PATH
    assumeTrue(ffmpegOnPath(), "Skipping RealTranscodeIT — ffmpeg not found on PATH (expected on local machines; must run in CI)");

    URI endpoint = URI.create(minio.getS3URL());
    StaticCredentialsProvider creds =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(minio.getUserName(), minio.getPassword()));
    S3Configuration s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();

    s3Client = S3Client.builder()
        .endpointOverride(endpoint)
        .region(Region.US_EAST_1)
        .credentialsProvider(creds)
        .serviceConfiguration(s3Config)
        .build();

    S3Presigner presigner = S3Presigner.builder()
        .endpointOverride(endpoint)
        .region(Region.US_EAST_1)
        .credentialsProvider(creds)
        .serviceConfiguration(s3Config)
        .build();

    s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_ORIGINALS).build());
    s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_DELIVERY).build());

    objectStore = new S3ObjectStoreAdapter(s3Client, presigner, BUCKET_ORIGINALS, BUCKET_DELIVERY);
    transcoder = new FfmpegAudioTranscoderAdapter(s3Client, objectStore, BUCKET_DELIVERY);
  }

  /**
   * LLFR-MEDIA-01.2 AC: Given a WAV upload, when transcode completes, then a full HLS rendition
   * (delivery/{id}/hls/) and a ≤30s preview rendition (delivery/{id}/preview/) both exist.
   */
  @Test
  void transcode_wav_produces_hls_and_preview_renditions() throws Exception {
    MediaAssetId id = new MediaAssetId("rt-asset-001");

    // Upload a real (minimal) WAV to originals
    byte[] wav = minimalSilentWav(5); // 5-second silent WAV
    ObjectKey originalKey = objectStore.putOriginal(
        org.shakvilla.beatzmedia.media.domain.MediaKind.AUDIO,
        id,
        new ByteArrayInputStream(wav),
        "audio/wav",
        wav.length);

    assertTrue(objectStore.exists(originalKey), "Original must exist before transcode");

    // Probe duration
    int durationSec = transcoder.probeDurationSec(originalKey);
    assertTrue(durationSec > 0, "Probed duration must be > 0");

    // Transcode to full HLS
    ObjectKey hlsKey = transcoder.transcodeHls(originalKey, id);
    assertNotNull(hlsKey, "HLS key must not be null");
    assertTrue(objectStore.exists(hlsKey), "HLS playlist must exist in delivery bucket");

    // Clip to ≤30s preview
    ObjectKey previewKey = transcoder.clipPreviewHls(originalKey, id, 30);
    assertNotNull(previewKey, "Preview key must not be null");
    assertTrue(objectStore.exists(previewKey), "Preview playlist must exist in delivery bucket");

    // Verify paths
    assertTrue(hlsKey.key().contains("/hls/"), "HLS key must contain /hls/ path segment");
    assertTrue(previewKey.key().contains("/preview/"), "Preview key must contain /preview/ path segment");
  }

  // ---- Helpers ----

  static boolean ffmpegOnPath() {
    try {
      Process p = new ProcessBuilder("ffmpeg", "-version")
          .redirectErrorStream(true)
          .start();
      return p.waitFor() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Generate a minimal, valid WAV file containing silence for the given duration.
   * 16-bit mono PCM at 44100 Hz.
   */
  static byte[] minimalSilentWav(int durationSeconds) {
    int sampleRate = 44100;
    int channels = 1;
    int bitsPerSample = 16;
    int numSamples = sampleRate * durationSeconds;
    int dataSize = numSamples * channels * (bitsPerSample / 8);
    int chunkSize = 36 + dataSize;

    byte[] wav = new byte[44 + dataSize];
    // RIFF header
    wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
    putInt32LE(wav, 4, chunkSize);
    wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
    // fmt sub-chunk
    wav[12] = 'f'; wav[13] = 'm'; wav[14] = 't'; wav[15] = ' ';
    putInt32LE(wav, 16, 16); // sub-chunk size
    putInt16LE(wav, 20, 1);  // PCM
    putInt16LE(wav, 22, channels);
    putInt32LE(wav, 24, sampleRate);
    putInt32LE(wav, 28, sampleRate * channels * bitsPerSample / 8); // byte rate
    putInt16LE(wav, 32, channels * bitsPerSample / 8); // block align
    putInt16LE(wav, 34, bitsPerSample);
    // data sub-chunk
    wav[36] = 'd'; wav[37] = 'a'; wav[38] = 't'; wav[39] = 'a';
    putInt32LE(wav, 40, dataSize);
    // silence (zeros already)
    return wav;
  }

  private static void putInt32LE(byte[] b, int offset, int value) {
    b[offset]     = (byte) (value & 0xFF);
    b[offset + 1] = (byte) ((value >> 8) & 0xFF);
    b[offset + 2] = (byte) ((value >> 16) & 0xFF);
    b[offset + 3] = (byte) ((value >> 24) & 0xFF);
  }

  private static void putInt16LE(byte[] b, int offset, int value) {
    b[offset]     = (byte) (value & 0xFF);
    b[offset + 1] = (byte) ((value >> 8) & 0xFF);
  }
}
