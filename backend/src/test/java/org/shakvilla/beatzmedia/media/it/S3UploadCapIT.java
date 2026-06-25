package org.shakvilla.beatzmedia.media.it;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
 * Integration test guarding the 500MB upload cap against the REAL AWS SDK v2 client (MinIO).
 *
 * <p>Flagged by the security review of WU-MED-1 (PR #11): the unit test for the upload cap uses a
 * {@code FakeObjectStore} that reads the stream directly, so a {@link FileTooLargeException} thrown
 * mid-stream propagates cleanly. But against the real S3 client, {@code putOriginal} hands the
 * stream to {@code RequestBody.fromInputStream}, and the SDK reads it inside its own marshalling
 * loop. When the stream throws mid-PUT, the SDK wraps the cause as {@code SdkClientException}, so a
 * naive adapter would leak an unwrapped SDK exception — {@code DomainExceptionMapper} would then see
 * a non-{@code DomainException} and return HTTP 500 instead of 413.
 *
 * <p>This test streams an over-cap body (a stream that throws {@link FileTooLargeException} mid-read,
 * exactly as {@code MediaApplicationService.CountingLimitingInputStream} does once the cap is
 * exceeded) through the real adapter and asserts the caller still observes {@link
 * FileTooLargeException} (→ HTTP 413), never a raw SDK exception (→ HTTP 500).
 */
@Tag("integration")
@Testcontainers
class S3UploadCapIT {

  private static final String BUCKET_ORIGINALS = "beatz-media-originals";
  private static final String BUCKET_DELIVERY = "beatz-media-delivery";

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
   * Over-cap upload with a known (declared) content length must surface {@link
   * FileTooLargeException} (→ 413), not a wrapped SDK exception (→ 500).
   */
  @Test
  void oversize_upload_with_declared_length_surfaces_413() {
    MediaAssetId id = new MediaAssetId("cap-asset-001");
    // Declare a length larger than what the stream serves so the SDK keeps reading and hits the
    // mid-stream FileTooLargeException — mirroring an upload whose actual bytes exceed the cap.
    long declaredLength = BYTES_BEFORE_LIMIT * 4L;

    assertThrows(
        FileTooLargeException.class,
        () ->
            adapter.putOriginal(
                MediaKind.AUDIO,
                id,
                new ThrowAtLimitInputStream(BYTES_BEFORE_LIMIT),
                "audio/wav",
                declaredLength));
  }

  /**
   * Stand-in for {@code MediaApplicationService.CountingLimitingInputStream} once the cap is
   * exceeded: serves {@code limit} zero-bytes, then throws {@link FileTooLargeException} on the next
   * read — the same domain exception, from the same {@code read()} method, that the production
   * limiting stream throws.
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
        // already at the limit — next byte trips the cap
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
