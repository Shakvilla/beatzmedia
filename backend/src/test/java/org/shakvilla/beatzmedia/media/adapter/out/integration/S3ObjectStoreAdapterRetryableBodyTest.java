package org.shakvilla.beatzmedia.media.adapter.out.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * Regression guard for the Studio release-track / podcast-episode upload 500 (frontend PR #144 live
 * QA): {@code putOriginal} handed the AWS SDK v2 <em>sync</em> client a body built from a
 * <b>non-markable</b> {@code java.nio.file.Files.newInputStream(...)} stream via {@code
 * RequestBody.fromInputStream}. The sync client may read the payload more than once — for SigV4
 * payload signing, flexible-checksum precompute, or a retry — and on the <b>second</b> read a
 * non-resettable body throws {@code java.lang.IllegalStateException: Content input stream does not
 * support mark/reset, and was already read once.} → HTTP&nbsp;500 on every upload.
 *
 * <p>This test pins the exact contract the sync client requires: the {@link RequestBody} the adapter
 * builds must be <b>re-readable</b>. It stands a fake {@link S3Client} in place of the network
 * transport whose {@code putObject} reads the request body's {@link ContentStreamProvider}
 * <b>twice</b> — precisely what the SDK does under retry/second-read. Against the pre-fix adapter the
 * second {@code newStream()} throws the mark/reset {@link IllegalStateException}; against the fixed
 * adapter (retryable {@code RequestBody.fromFile} via a bounded spool) both reads succeed and return
 * the body byte-for-byte.
 *
 * <p>Deterministic and network-free — it complements {@code S3KnownLengthNonMarkableUploadIT}, which
 * exercises the same non-markable body end-to-end against a real MinIO. Neither pre-existing S3 IT
 * caught this: {@code S3UploadCapIT}'s body throws mid-read (the SDK never reaches the second read),
 * and {@code S3UnknownLengthUploadIT} uses a mark/reset-capable {@code ByteArrayInputStream}.
 */
class S3ObjectStoreAdapterRetryableBodyTest {

  private static final String BUCKET_ORIGINALS = "beatz-media-originals";
  private static final String BUCKET_DELIVERY = "beatz-media-delivery";

  /**
   * A known (declared) content length whose body is a non-markable {@code Files.newInputStream} must
   * be uploaded via a re-readable request body: reading it twice (as the sync client may) must yield
   * the full payload both times, never {@code IllegalStateException: ... does not support mark/reset
   * ...}. This is the exact shape of the Studio track/episode upload that returned HTTP 500.
   */
  @Test
  void known_length_non_markable_body_is_uploaded_via_a_re_readable_request_body() throws Exception {
    byte[] payload = new byte[128 * 1024]; // 128 KB
    for (int i = 0; i < payload.length; i++) {
      payload[i] = (byte) (i % 251);
    }

    Path tempFile = Files.createTempFile("beatz-retryable-body-", ".wav");
    Files.write(tempFile, payload);

    TwiceReadingS3Client fakeS3 = new TwiceReadingS3Client();
    S3ObjectStoreAdapter adapter =
        new S3ObjectStoreAdapter(fakeS3, /* presigner */ null, BUCKET_ORIGINALS, BUCKET_DELIVERY);

    try (InputStream body = Files.newInputStream(tempFile)) {
      // The very property that trips the sync client — guard it so a future JDK change can't silently
      // turn this stream markable and stop exercising the regression.
      assertFalse(
          body.markSupported(),
          "Files.newInputStream must be non-markable to reproduce the mark/reset regression");

      ObjectKey key =
          adapter.putOriginal(
              MediaKind.AUDIO,
              new MediaAssetId("retryable-body-001"),
              body,
              "audio/wav",
              payload.length); // KNOWN, trusted content length — the >= 0 branch

      assertNotNull(key, "putOriginal must return the stored object key");
    } finally {
      Files.deleteIfExists(tempFile);
    }

    // The fake read the body twice (initial send + one retry). Both reads must reproduce the payload
    // exactly — proving the adapter handed the SDK a retryable, re-readable body.
    assertEquals(1, fakeS3.putObjectInvocations, "the fake must have received exactly one PUT");
    assertArrayEquals(payload, fakeS3.firstRead, "first read of the request body must be the payload");
    assertArrayEquals(
        payload, fakeS3.secondRead, "second read (retry) of the request body must be the payload");
  }

  /**
   * A known-length body that already supports mark/reset (e.g. a {@link
   * java.io.ByteArrayInputStream}) takes the fast stream-through path ({@code
   * RequestBody.fromInputStream}, no spool) — and must still be re-readable, since the sync client
   * may reset it for signing/retry. Guards the {@code contentLength >= 0 && markSupported()} branch.
   */
  @Test
  void known_length_markable_body_streams_through_and_stays_re_readable() {
    byte[] payload = new byte[64 * 1024];
    for (int i = 0; i < payload.length; i++) {
      payload[i] = (byte) (i % 251);
    }

    TwiceReadingS3Client fakeS3 = new TwiceReadingS3Client();
    S3ObjectStoreAdapter adapter =
        new S3ObjectStoreAdapter(fakeS3, /* presigner */ null, BUCKET_ORIGINALS, BUCKET_DELIVERY);

    java.io.ByteArrayInputStream body = new java.io.ByteArrayInputStream(payload);
    assertTrue(
        body.markSupported(), "ByteArrayInputStream must support mark/reset for the fast path");

    ObjectKey key =
        adapter.putOriginal(
            MediaKind.AUDIO, new MediaAssetId("markable-body-001"), body, "audio/wav", payload.length);

    assertNotNull(key, "putOriginal must return the stored object key");
    assertEquals(1, fakeS3.putObjectInvocations, "the fake must have received exactly one PUT");
    assertArrayEquals(payload, fakeS3.firstRead, "first read of the request body must be the payload");
    assertArrayEquals(
        payload, fakeS3.secondRead, "second read (retry) of the request body must be the payload");
  }

  /**
   * Stand-in for the AWS SDK sync transport that reads the request body's {@link
   * ContentStreamProvider} <b>twice</b> — as the real client does for payload signing / a retry. A
   * non-resettable body makes the second {@code newStream()} throw {@code IllegalStateException},
   * exactly as it does in production. {@link S3Client} operations are default methods, so overriding
   * {@code putObject(PutObjectRequest, RequestBody)} alone suffices.
   */
  private static final class TwiceReadingS3Client implements S3Client {

    private int putObjectInvocations;
    private byte[] firstRead;
    private byte[] secondRead;

    @Override
    public PutObjectResponse putObject(PutObjectRequest request, RequestBody requestBody) {
      putObjectInvocations++;
      ContentStreamProvider provider = requestBody.contentStreamProvider();
      try (InputStream first = provider.newStream()) {
        firstRead = first.readAllBytes();
        // Second read simulates the SDK re-supplying the body (retry / signing). A retryable body
        // (fromFile) re-opens cleanly; a fromInputStream(non-markable) body throws here.
        try (InputStream second = provider.newStream()) {
          secondRead = second.readAllBytes();
        }
      } catch (IOException e) {
        throw new RuntimeException("fake S3 client failed reading the request body", e);
      }
      return PutObjectResponse.builder().build();
    }

    @Override
    public String serviceName() {
      return "s3";
    }

    @Override
    public void close() {
      // no-op
    }
  }
}
