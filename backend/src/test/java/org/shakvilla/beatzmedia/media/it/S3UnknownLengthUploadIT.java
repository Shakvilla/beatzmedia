package org.shakvilla.beatzmedia.media.it;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.media.adapter.out.integration.S3ObjectStoreAdapter;
import org.shakvilla.beatzmedia.media.domain.FileTooLargeException;
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
 * Integration test for the unknown/untrusted content-length upload path against the REAL AWS SDK v2
 * client (MinIO).
 *
 * <p>{@code MediaApplicationService.uploadOriginal} passes {@code contentLengthHint = -1L} whenever
 * the client-declared {@code sizeBytes} is {@code <= 0} or {@code > MAX_SIZE_BYTES}. The AWS SDK v2
 * sync client cannot PUT a stream of unknown length — {@code RequestBody.fromInputStream(body, -1L)}
 * throws {@code IllegalArgumentException: Content-length must not be negative} immediately (validated
 * in {@code software.amazon.awssdk.core.sync.RequestBody.<init>}). A naive adapter therefore fails
 * every such upload with HTTP 500, and an over-cap upload via this path never even reaches the
 * {@code CountingLimitingInputStream} (the exception is thrown before the body is read), so it
 * returns 500 instead of the intended 413.
 *
 * <p>This test exercises the {@code -1} path end to end through the real adapter:
 *
 * <ul>
 *   <li>a normal-size body must upload successfully and be stored byte-for-byte (no truncation);
 *   <li>an over-cap body (a stream that throws {@link FileTooLargeException} mid-read, exactly as
 *       {@code CountingLimitingInputStream} does once the cap is exceeded) must surface {@link
 *       FileTooLargeException} (→ HTTP 413), never a raw SDK / {@code IllegalArgumentException}
 *       (→ HTTP 500).
 * </ul>
 */
@Tag("integration")
@Testcontainers
class S3UnknownLengthUploadIT {

  private static final String BUCKET_ORIGINALS = "beatz-media-originals";
  private static final String BUCKET_DELIVERY = "beatz-media-delivery";

  /** Sentinel for the unknown/untrusted content-length path (mirrors the service's hint). */
  private static final long UNKNOWN_LENGTH = -1L;

  /** Bytes the fake over-cap stream serves before it throws — small, so the test stays fast. */
  private static final int BYTES_BEFORE_LIMIT = 64 * 1024;

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
    // Release the SDK HTTP/native resources held by the class-scoped clients.
    if (presigner != null) {
      presigner.close();
    }
    if (s3Client != null) {
      s3Client.close();
    }
  }

  /**
   * A normal-size upload with an unknown ({@code -1}) content length must succeed and store the body
   * intact — no negative-length error, no truncation.
   */
  @Test
  void unknown_length_normal_body_uploads_intact() {
    MediaAssetId id = new MediaAssetId("unknown-len-001");
    byte[] payload = new byte[200 * 1024]; // 200 KB, well under the cap
    for (int i = 0; i < payload.length; i++) {
      payload[i] = (byte) (i % 251);
    }

    ObjectKey key =
        adapter.putOriginal(
            MediaKind.AUDIO,
            id,
            new ByteArrayInputStream(payload),
            "audio/wav",
            UNKNOWN_LENGTH);

    assertTrue(adapter.exists(key), "object must exist after an unknown-length upload");
    HeadObjectResponse head =
        s3Client.headObject(
            HeadObjectRequest.builder().bucket(key.bucket()).key(key.key()).build());
    assertEquals(
        payload.length,
        head.contentLength(),
        "stored object must be the full body, not truncated to a capped hint");

    // Read the object back and compare byte-for-byte — a corruption bug that preserves the size
    // would still pass the content-length check above.
    byte[] stored =
        s3Client
            .getObjectAsBytes(
                GetObjectRequest.builder().bucket(key.bucket()).key(key.key()).build())
            .asByteArray();
    assertArrayEquals(payload, stored, "stored bytes must match the uploaded body exactly");
  }

  /**
   * An over-cap upload via the unknown ({@code -1}) content-length path must surface {@link
   * FileTooLargeException} (→ 413), not an {@code IllegalArgumentException} / wrapped SDK exception
   * (→ 500). The size cap must still trip even when no content length is declared.
   */
  @Test
  void unknown_length_oversize_body_surfaces_413() {
    MediaAssetId id = new MediaAssetId("unknown-len-002");

    assertThrows(
        FileTooLargeException.class,
        () ->
            adapter.putOriginal(
                MediaKind.AUDIO,
                id,
                new ThrowAtLimitInputStream(BYTES_BEFORE_LIMIT),
                "audio/wav",
                UNKNOWN_LENGTH));
  }

  /**
   * Defense-in-depth: even when the body is NOT pre-wrapped in the application-layer limiting stream,
   * the adapter's own spool cap must abort an oversized unknown-length body with {@link
   * FileTooLargeException}, so a missed upstream wrapper can never fill the disk. Uses a small cap and
   * a plain (unwrapped) stream larger than it, so only the adapter-internal bound can raise.
   */
  @Test
  void unknown_length_adapter_enforces_its_own_spool_cap() {
    long tinyCap = 128 * 1024; // 128 KB
    S3ObjectStoreAdapter cappedAdapter =
        new S3ObjectStoreAdapter(s3Client, presigner, BUCKET_ORIGINALS, BUCKET_DELIVERY, tinyCap);
    byte[] rawBody = new byte[(int) tinyCap * 2]; // 256 KB, unwrapped — exceeds the adapter cap

    assertThrows(
        FileTooLargeException.class,
        () ->
            cappedAdapter.putOriginal(
                MediaKind.AUDIO,
                new MediaAssetId("unknown-len-003"),
                new ByteArrayInputStream(rawBody),
                "audio/wav",
                UNKNOWN_LENGTH));
  }

  /**
   * Stand-in for {@code MediaApplicationService.CountingLimitingInputStream} once the cap is
   * exceeded: serves {@code limit} zero-bytes, then throws {@link FileTooLargeException} from {@code
   * read()}. It reproduces the behaviour relevant to this test — the same domain exception raised
   * from {@code read()} mid-transfer — not the exact byte threshold (the real stream throws once
   * {@code bytesRead > maxBytes}); the precise threshold is irrelevant to what these tests assert.
   */
  private static final class ThrowAtLimitInputStream extends InputStream {

    private final long limit;
    private long served = 0;

    ThrowAtLimitInputStream(long limit) {
      this.limit = limit;
    }

    @Override
    public int read() throws IOException {
      checkLimit();
      served++;
      return 0;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
      checkLimit();
      int n = (int) Math.min(len, limit - served);
      if (n <= 0) {
        throw new FileTooLargeException("File exceeds maximum allowed size");
      }
      java.util.Arrays.fill(buf, off, off + n, (byte) 0);
      served += n;
      return n;
    }

    private void checkLimit() {
      if (served >= limit) {
        throw new FileTooLargeException("File exceeds maximum allowed size");
      }
    }
  }
}
