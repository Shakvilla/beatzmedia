package org.shakvilla.beatzmedia.media.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.media.adapter.out.integration.S3ObjectStoreAdapter;
import org.shakvilla.beatzmedia.media.domain.DeliveryVariant;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;
import org.shakvilla.beatzmedia.media.domain.SignedUrl;
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
 * Integration test: Testcontainers MinIO + S3 adapter.
 * Verifies LLFR-MEDIA-01.3:
 *  - upload to private originals bucket
 *  - store a delivery file and presign a GET URL
 *  - the presigned URL is fetchable and expires (expiresAt is in the future on issue)
 *
 * Does NOT require ffmpeg — the transcode path is covered by the real-transcode IT (skipped
 * locally when ffmpeg is absent). This IT tests only the S3/presign path.
 */
@Tag("integration")
@Testcontainers
class MediaDeliveryIT {

  private static final String BUCKET_ORIGINALS = "beatz-media-originals";
  private static final String BUCKET_DELIVERY = "beatz-media-delivery";

  @Container
  static MinIOContainer minio =
      new MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z")
          .withUserName("minioadmin")
          .withPassword("minioadmin");

  private static S3Client s3Client;
  private static S3Presigner presigner;
  private static S3ObjectStoreAdapter adapter;

  @BeforeAll
  static void setUpS3() {
    URI endpoint = URI.create(minio.getS3URL());
    StaticCredentialsProvider creds =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(minio.getUserName(), minio.getPassword()));
    S3Configuration s3Config =
        S3Configuration.builder().pathStyleAccessEnabled(true).build();

    s3Client =
        S3Client.builder()
            .endpointOverride(endpoint)
            .region(Region.US_EAST_1)
            .credentialsProvider(creds)
            .serviceConfiguration(s3Config)
            .build();

    presigner =
        S3Presigner.builder()
            .endpointOverride(endpoint)
            .region(Region.US_EAST_1)
            .credentialsProvider(creds)
            .serviceConfiguration(s3Config)
            .build();

    // Create buckets
    s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_ORIGINALS).build());
    s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_DELIVERY).build());

    adapter = new S3ObjectStoreAdapter(s3Client, presigner, BUCKET_ORIGINALS, BUCKET_DELIVERY);
  }

  /** LLFR-MEDIA-01.1 / ADD §3 — originals bucket stores raw upload. */
  @Test
  void upload_to_originals_bucket_and_exists() {
    MediaAssetId id = new MediaAssetId("it-asset-001");
    byte[] body = dummyWav();

    ObjectKey key = adapter.putOriginal(
        MediaKind.AUDIO, id, new ByteArrayInputStream(body), "audio/wav");

    assertTrue(adapter.exists(key), "Stored original must exist in bucket");
    assertEquals(BUCKET_ORIGINALS, key.bucket());
    assertTrue(key.key().contains(id.value()));
  }

  /** LLFR-MEDIA-01.3 — presign a delivery URL and verify it is fetchable + has expiresAt. */
  @Test
  void presign_delivery_url_is_fetchable() throws Exception {
    MediaAssetId id = new MediaAssetId("it-asset-002");
    byte[] content = "HLS playlist content".getBytes();

    // Store a delivery file
    String relKey = "delivery/" + id.value() + "/hls/playlist.m3u8";
    adapter.putDelivery(id, relKey, new ByteArrayInputStream(content), "application/x-mpegURL");

    ObjectKey deliveryKey = new ObjectKey(BUCKET_DELIVERY, relKey);
    assertTrue(adapter.exists(deliveryKey));

    // Presign a GET URL (30-second TTL)
    SignedUrl signed = adapter.presignGet(deliveryKey, DeliveryVariant.FULL, Duration.ofSeconds(30));

    assertNotNull(signed.url(), "Signed URL must not be null");
    assertNotNull(signed.expiresAt(), "expiresAt must not be null");
    assertTrue(
        signed.expiresAt().isAfter(Instant.now()),
        "expiresAt must be in the future at time of issue");
    assertEquals(DeliveryVariant.FULL, signed.variant());

    // Actually fetch the pre-signed URL to verify it works
    HttpClient http = HttpClient.newHttpClient();
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create(signed.url())).GET().build(),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode(), "Presigned URL must return HTTP 200");
    assertEquals("HLS playlist content", response.body());
  }

  /** LLFR-MEDIA-01.3 — PREVIEW presigned URL targets the preview key. */
  @Test
  void preview_presign_targets_preview_key() throws Exception {
    MediaAssetId id = new MediaAssetId("it-asset-003");
    byte[] content = "preview playlist".getBytes();

    String relKey = "delivery/" + id.value() + "/preview/preview.m3u8";
    adapter.putDelivery(id, relKey, new ByteArrayInputStream(content), "application/x-mpegURL");

    ObjectKey previewKey = new ObjectKey(BUCKET_DELIVERY, relKey);
    SignedUrl signed =
        adapter.presignGet(previewKey, DeliveryVariant.PREVIEW, Duration.ofSeconds(30));

    assertEquals(DeliveryVariant.PREVIEW, signed.variant());
    assertTrue(signed.url().contains("preview"), "URL must reference preview key");

    HttpClient http = HttpClient.newHttpClient();
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create(signed.url())).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    assertEquals("preview playlist", response.body());
  }

  // ---- Helpers ----

  private byte[] dummyWav() {
    byte[] b = new byte[64];
    b[0] = 0x52; b[1] = 0x49; b[2] = 0x46; b[3] = 0x46; // RIFF
    b[8] = 0x57; b[9] = 0x41; b[10] = 0x56; b[11] = 0x45; // WAVE
    return b;
  }
}
