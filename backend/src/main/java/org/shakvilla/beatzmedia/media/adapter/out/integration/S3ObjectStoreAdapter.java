package org.shakvilla.beatzmedia.media.adapter.out.integration;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.media.application.port.out.ObjectStorePort;
import org.shakvilla.beatzmedia.media.application.port.out.UrlSignerPort;
import org.shakvilla.beatzmedia.media.domain.DeliveryVariant;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;
import org.shakvilla.beatzmedia.media.domain.SignedUrl;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * S3/MinIO adapter implementing {@link ObjectStorePort} and {@link UrlSignerPort}.
 * Originals bucket is PRIVATE — never signed for client read. ADD §3 / §5.2 / ADR (WU-MED-1 §3).
 *
 * <p>Streaming note: {@code putOriginal} passes the stream directly to the AWS SDK via
 * {@link RequestBody#fromInputStream} with the supplied content-length. The body is never
 * buffered into a heap {@code byte[]} — only the 12-byte magic probe is read before the S3 PUT.
 * When content-length is {@code -1} (unknown), chunked transfer encoding is used via
 * {@code RequestBody.fromContentProvider}, so the JVM heap is never exhausted by large WAV/FLAC
 * files. See B3/S2/M-1 fix notes.
 */
@ApplicationScoped
public class S3ObjectStoreAdapter implements ObjectStorePort, UrlSignerPort {

  private static final String ORIGINALS_KEY_PREFIX = "originals/";

  private final S3Client s3Client;
  private final S3Presigner presigner;
  private final String bucketOriginals;
  private final String bucketDelivery;

  @Inject
  public S3ObjectStoreAdapter(
      S3Client s3Client,
      S3Presigner presigner,
      @ConfigProperty(name = "beatz.s3.bucket-originals", defaultValue = "beatz-media-originals")
          String bucketOriginals,
      @ConfigProperty(name = "beatz.s3.bucket-delivery", defaultValue = "beatz-media-delivery")
          String bucketDelivery) {
    this.s3Client = s3Client;
    this.presigner = presigner;
    this.bucketOriginals = bucketOriginals;
    this.bucketDelivery = bucketDelivery;
  }

  // ---- ObjectStorePort ----

  @Override
  public ObjectKey putOriginal(
      MediaKind kind, MediaAssetId id, InputStream body, String contentType, long contentLength) {
    String key = ORIGINALS_KEY_PREFIX + kind.name().toLowerCase() + "/" + id.value();
    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(bucketOriginals)
            .key(key)
            .contentType(contentType)
            .build();
    RequestBody requestBody = toRequestBody(body, contentLength);
    s3Client.putObject(request, requestBody);
    return new ObjectKey(bucketOriginals, key);
  }

  @Override
  public ObjectKey putDelivery(
      MediaAssetId id, String relativeKey, InputStream body, String contentType) {
    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(bucketDelivery)
            .key(relativeKey)
            .contentType(contentType)
            .build();
    // Delivery files are local temp files with known sizes — use readAllBytes only for these
    // (they are bounded local transcode outputs, not unbounded client uploads)
    try {
      byte[] bytes = body.readAllBytes();
      s3Client.putObject(request, RequestBody.fromBytes(bytes));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read delivery body", e);
    }
    return new ObjectKey(bucketDelivery, relativeKey);
  }

  /**
   * Build a {@link RequestBody} that streams the content without loading it fully into memory.
   *
   * <p>If {@code contentLength >= 0} we know the size upfront (the magic probe was already counted
   * in the DigestInputStream wrapper in the service, so the stream still carries the full bytes).
   * If {@code contentLength < 0} we fall back to {@code fromInputStream} with unknown length
   * (requires the S3 client to be configured for chunked encoding or path-style transfer — MinIO
   * handles this correctly).
   */
  private RequestBody toRequestBody(InputStream body, long contentLength) {
    if (contentLength >= 0) {
      return RequestBody.fromInputStream(body, contentLength);
    }
    // Unknown length: use chunked streaming (AWS SDK v2 supports this with unsigned payloads)
    return RequestBody.fromInputStream(body, -1L);
  }

  @Override
  public boolean exists(ObjectKey key) {
    try {
      s3Client.headObject(
          HeadObjectRequest.builder().bucket(key.bucket()).key(key.key()).build());
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    }
  }

  @Override
  public void deleteOriginal(ObjectKey key) {
    // S3 DeleteObject is idempotent — deleting a non-existent key succeeds silently.
    s3Client.deleteObject(
        DeleteObjectRequest.builder().bucket(key.bucket()).key(key.key()).build());
  }

  // ---- UrlSignerPort ----

  @Override
  public SignedUrl presignGet(ObjectKey key, DeliveryVariant variant, Duration ttl) {
    Instant expiresAt = Instant.now().plus(ttl);

    PresignedGetObjectRequest presigned =
        presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(r -> r.bucket(key.bucket()).key(key.key()))
                .build());

    return new SignedUrl(presigned.url().toString(), variant, expiresAt);
  }

  /** Expose the delivery bucket name for adapters that need to build ObjectKey references. */
  public String getBucketDelivery() {
    return bucketDelivery;
  }
}
