package org.shakvilla.beatzmedia.media.adapter.out.integration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.media.application.port.out.ObjectStorePort;
import org.shakvilla.beatzmedia.media.application.port.out.UrlSignerPort;
import org.shakvilla.beatzmedia.media.domain.DeliveryVariant;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;
import org.shakvilla.beatzmedia.media.domain.SignedUrl;
import org.shakvilla.beatzmedia.platform.domain.DomainException;

import software.amazon.awssdk.core.exception.SdkException;
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
 * <p>Streaming note: when the content length is known, {@code putOriginal} streams the body straight
 * to the AWS SDK via {@link RequestBody#fromInputStream} — the body is never buffered into a heap
 * {@code byte[]} (only the 12-byte magic probe is read before the PUT). The AWS SDK v2 <em>sync</em>
 * client cannot PUT a stream of unknown length, so when content-length is {@code -1} (unknown) the
 * body is spooled to a bounded temporary file and streamed from disk via {@link
 * RequestBody#fromFile}. The upload size cap still trips during the spool (the {@code
 * CountingLimitingInputStream} throws mid-read), and {@code Files.copy} uses a small fixed buffer so
 * the JVM heap is never exhausted by large WAV/FLAC files. See B3/S2/M-1 fix notes.
 */
@ApplicationScoped
public class S3ObjectStoreAdapter implements ObjectStorePort, UrlSignerPort {

  private static final Logger LOG = Logger.getLogger(S3ObjectStoreAdapter.class);

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
    try {
      if (contentLength >= 0) {
        // Known, trusted length: stream straight through with no heap buffer.
        s3Client.putObject(request, RequestBody.fromInputStream(body, contentLength));
      } else {
        // Unknown/untrusted length: the service passes -1 when the declared size is absent
        // (<= 0) or exceeds the cap. The AWS SDK v2 sync client cannot PUT a stream of unknown
        // length — RequestBody.fromInputStream(body, -1) throws "Content-length must not be
        // negative". Spool to a bounded temp file to learn the real length, then stream from disk.
        putViaTempSpool(request, body);
      }
    } catch (SdkException e) {
      // The 500MB cap is enforced by the limiting input stream (see MediaApplicationService),
      // which throws FileTooLargeException mid-read. The AWS SDK reads the body inside its own
      // marshalling loop, so it may wrap that domain exception as an SdkClientException. Re-surface
      // the original domain exception so DomainExceptionMapper returns 413 (not 500). Any non-domain
      // SDK failure is rethrown unchanged. Flagged by security review of WU-MED-1 (PR #11).
      throw unwrapDomainException(e);
    }
    return new ObjectKey(bucketOriginals, key);
  }

  /**
   * If a {@link DomainException} (e.g. the size-cap {@link
   * org.shakvilla.beatzmedia.media.domain.FileTooLargeException}) is present anywhere in the cause
   * chain of an SDK failure, return it so the REST layer maps it to the correct status. Otherwise
   * return the original SDK exception unchanged.
   */
  private static RuntimeException unwrapDomainException(SdkException sdkException) {
    for (Throwable t = sdkException; t != null; t = t.getCause()) {
      if (t instanceof DomainException domainException) {
        return domainException;
      }
    }
    return sdkException;
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
   * Spool a body of unknown length to a bounded temporary file, then PUT it from disk with a known
   * content length. Reading the body here runs it through the caller's limiting/digest stream chain
   * (see {@code MediaApplicationService}), so a size-cap breach throws {@link
   * org.shakvilla.beatzmedia.media.domain.FileTooLargeException} during the spool — before any S3
   * PUT — and propagates unchanged (→ HTTP 413). {@code Files.copy} uses a small fixed buffer, so
   * the JVM heap is never exhausted even by a 500MB body. The temp file is always deleted.
   */
  private void putViaTempSpool(PutObjectRequest request, InputStream body) {
    Path spool;
    try {
      spool = Files.createTempFile("beatz-upload-", ".tmp");
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create temp file for unknown-length upload", e);
    }
    try {
      // A FileTooLargeException (RuntimeException) raised by the limiting stream propagates out of
      // Files.copy unchanged; only genuine I/O failures are wrapped.
      Files.copy(body, spool, StandardCopyOption.REPLACE_EXISTING);
      s3Client.putObject(request, RequestBody.fromFile(spool));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to spool unknown-length upload body", e);
    } finally {
      try {
        Files.deleteIfExists(spool);
      } catch (IOException cleanupFailure) {
        // Best-effort cleanup; never mask the primary outcome. Warn (with the path) so a repeated
        // failure to delete a potentially 500MB spool file is visible to operators instead of
        // silently exhausting disk — /tmp is not guaranteed to be reaped automatically.
        LOG.warnf(cleanupFailure, "Failed to delete unknown-length upload spool file %s", spool);
      }
    }
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
