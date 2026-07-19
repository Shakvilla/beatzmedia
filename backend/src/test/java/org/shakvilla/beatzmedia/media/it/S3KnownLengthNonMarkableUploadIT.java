package org.shakvilla.beatzmedia.media.it;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.media.adapter.out.integration.S3ObjectStoreAdapter;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
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
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Integration test for the KNOWN (declared) content-length upload path against the REAL AWS SDK v2
 * client (MinIO), using a <em>non-markable</em> body stream — reproducing the exact production
 * condition of the Studio release-track / podcast-episode upload flows.
 *
 * <p><b>The regression (frontend PR #144 live QA):</b> every track upload returned HTTP 500.
 * {@code StudioReleaseResource.uploadTrack} (and {@code StudioPodcastResource}) open the multipart
 * temp file via {@code java.nio.file.Files.newInputStream(...)}, which does <b>not</b> support
 * mark/reset. The service chain that reaches {@code putOriginal} ({@code SequenceInputStream} →
 * {@code CountingLimitingInputStream} → {@code DigestInputStream}) also reports
 * {@code markSupported() == false}. AWS SDK v2 (2.47.x) computes a flexible checksum for
 * {@code PutObject} by default ({@code requestChecksumCalculation = WHEN_SUPPORTED}), so the sync
 * client reads the payload once to checksum it and then needs to <b>reset</b> the stream to send the
 * body — throwing {@code java.lang.IllegalStateException: Content input stream does not support
 * mark/reset, and was already read once.} when the body is non-markable.
 *
 * <p><b>Why the existing ITs missed it:</b> {@code S3UploadCapIT} feeds a stream that throws
 * mid-read (so the SDK never reaches the reset), and {@code S3UnknownLengthUploadIT} uses a
 * {@code ByteArrayInputStream} (which <em>does</em> support mark/reset) on the {@code -1} spool path.
 * Neither exercises a <em>successful</em> known-length PUT of a non-markable body — the real flow.
 *
 * <p>This test writes a payload to a temp file, streams it back with
 * {@code Files.newInputStream(...)} (non-markable, single-use) and a correct declared length, and
 * asserts the object is stored byte-for-byte end-to-end against a real MinIO — i.e. the fixed
 * adapter routes a non-markable known-length body through the retryable {@code RequestBody.fromFile}
 * spool and stores it intact.
 *
 * <p>Note: on the <em>happy path</em> exercised here the sync client does not force a second read of
 * the body, so this end-to-end test would pass even against the pre-fix adapter. The deterministic
 * regression guard that fails on the pre-fix code — reproducing the exact {@code IllegalStateException:
 * ... does not support mark/reset ...} by re-reading the request body — is the network-free
 * {@code S3ObjectStoreAdapterRetryableBodyTest}. This IT complements it with real-transport
 * byte-for-byte integrity.
 */
@Tag("integration")
@Testcontainers
class S3KnownLengthNonMarkableUploadIT {

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
    S3Configuration s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();

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

    s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_ORIGINALS).build());
    s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_DELIVERY).build());

    adapter = new S3ObjectStoreAdapter(s3Client, presigner, BUCKET_ORIGINALS, BUCKET_DELIVERY);
  }

  @AfterAll
  static void tearDownS3() {
    if (presigner != null) {
      presigner.close();
    }
    if (s3Client != null) {
      s3Client.close();
    }
  }

  /**
   * A normal-size upload with a KNOWN (declared) content length whose body is a non-markable
   * {@code Files.newInputStream} must succeed and store the body intact — no
   * {@code IllegalStateException: ... does not support mark/reset ...}, no truncation. This is the
   * exact shape of the Studio track/episode upload that returned HTTP 500 in live QA.
   */
  @Test
  void known_length_non_markable_body_uploads_intact() throws Exception {
    byte[] payload = new byte[200 * 1024]; // 200 KB, well under the cap
    for (int i = 0; i < payload.length; i++) {
      payload[i] = (byte) (i % 251);
    }

    Path tempFile = Files.createTempFile("beatz-known-len-nonmarkable-", ".wav");
    try {
      Files.write(tempFile, payload);

      MediaAssetId id = new MediaAssetId("known-len-nonmarkable-001");
      ObjectKey key;
      try (InputStream body = Files.newInputStream(tempFile)) {
        // Guard: this is the very property that trips the SDK. If a future JDK made the returned
        // stream markable, the regression this test pins would silently stop being exercised.
        assertFalse(
            body.markSupported(),
            "Files.newInputStream must be non-markable to reproduce the mark/reset regression");
        key =
            adapter.putOriginal(
                MediaKind.AUDIO,
                id,
                body,
                "audio/wav",
                payload.length); // KNOWN, trusted content length (the >= 0 branch)
      }

      assertTrue(adapter.exists(key), "object must exist after a known-length non-markable upload");
      HeadObjectResponse head =
          s3Client.headObject(
              HeadObjectRequest.builder().bucket(key.bucket()).key(key.key()).build());
      assertEquals(
          payload.length,
          head.contentLength(),
          "stored object must be the full body, not truncated");

      byte[] stored =
          s3Client
              .getObjectAsBytes(
                  GetObjectRequest.builder().bucket(key.bucket()).key(key.key()).build())
              .asByteArray();
      assertArrayEquals(payload, stored, "stored bytes must match the uploaded body exactly");
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }
}
