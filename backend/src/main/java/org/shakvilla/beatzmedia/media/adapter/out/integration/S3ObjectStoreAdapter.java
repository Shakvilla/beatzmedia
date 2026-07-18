package org.shakvilla.beatzmedia.media.adapter.out.integration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.media.application.port.out.ObjectStorePort;
import org.shakvilla.beatzmedia.media.application.port.out.UrlSignerPort;
import org.shakvilla.beatzmedia.media.domain.DeliveryVariant;
import org.shakvilla.beatzmedia.media.domain.FileTooLargeException;
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
 * <p>Streaming note: {@code putOriginal} streams the body straight to the AWS SDK via {@link
 * RequestBody#fromInputStream} — never buffering it into a heap {@code byte[]} (only the 12-byte
 * magic probe is read before the PUT) — but ONLY when the content length is known <em>and</em> the
 * body supports mark/reset. The AWS SDK v2 <em>sync</em> client may read the payload more than once
 * (SigV4 payload signing, flexible-checksum precompute, or a retry), so it needs a body it can
 * reset. Two cases therefore spool the body to a bounded temporary file and stream it from disk via
 * the re-readable {@link RequestBody#fromFile}: (1) unknown content-length ({@code -1}) — the sync
 * client cannot PUT a stream of unknown length; and (2) a non-markable body of known length
 * (WU-MED-2) — e.g. the Studio upload's {@code Files.newInputStream} over the multipart temp file,
 * which throws {@code IllegalStateException: ... does not support mark/reset ...} the moment the
 * sync client re-reads it. The spool is capped here in the adapter ({@link #MAX_SPOOL_BYTES}) using
 * a small fixed buffer, so neither the JVM heap nor the disk can be exhausted by a large or
 * untrusted body — independently of the caller's own limiting stream. See B3/S2/M-1 and WU-MED-2
 * fix notes.
 */
@ApplicationScoped
public class S3ObjectStoreAdapter implements ObjectStorePort, UrlSignerPort {

  private static final Logger LOG = Logger.getLogger(S3ObjectStoreAdapter.class);

  private static final String ORIGINALS_KEY_PREFIX = "originals/";

  /**
   * Backstop cap (bytes) on the unknown-length spool, enforced inside the adapter so a caller that
   * forgets to wrap the body in the application-layer limiting stream still cannot fill the disk.
   * Mirrors {@code MediaApplicationService.MAX_SIZE_BYTES} (500 MB); when the application limit is
   * present it trips first, making this a pure defense-in-depth backstop.
   */
  private static final long MAX_SPOOL_BYTES = 500L * 1024 * 1024;

  private final S3Client s3Client;
  private final S3Presigner presigner;
  private final String bucketOriginals;
  private final String bucketDelivery;
  private final long maxSpoolBytes;

  @Inject
  public S3ObjectStoreAdapter(
      S3Client s3Client,
      S3Presigner presigner,
      @ConfigProperty(name = "beatz.s3.bucket-originals", defaultValue = "beatz-media-originals")
          String bucketOriginals,
      @ConfigProperty(name = "beatz.s3.bucket-delivery", defaultValue = "beatz-media-delivery")
          String bucketDelivery) {
    this(s3Client, presigner, bucketOriginals, bucketDelivery, MAX_SPOOL_BYTES);
  }

  /** Visible for testing — lets an integration test drive the unknown-length spool cap small. */
  public S3ObjectStoreAdapter(
      S3Client s3Client,
      S3Presigner presigner,
      String bucketOriginals,
      String bucketDelivery,
      long maxSpoolBytes) {
    this.s3Client = s3Client;
    this.presigner = presigner;
    this.bucketOriginals = bucketOriginals;
    this.bucketDelivery = bucketDelivery;
    this.maxSpoolBytes = maxSpoolBytes;
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
      if (contentLength >= 0 && body.markSupported()) {
        // Known, trusted length AND a re-readable body: stream straight through with no spool. The
        // AWS SDK v2 sync client may read the payload more than once (SigV4 payload signing,
        // flexible-checksum precompute, or a retry), so it needs a body it can reset — which a
        // mark/reset-capable stream provides. RequestBody.fromInputStream marks/resets it.
        s3Client.putObject(request, RequestBody.fromInputStream(body, contentLength));
      } else {
        // Spool to a bounded temp file and PUT via the retryable RequestBody.fromFile. Two cases
        // land here:
        //   (a) Unknown/untrusted length: the service passes -1 when the declared size is absent
        //       (<= 0) or exceeds the cap. The sync client cannot PUT a stream of unknown length —
        //       RequestBody.fromInputStream(body, -1) throws "Content-length must not be negative".
        //   (b) Non-markable body of known length (WU-MED-2): the Studio release-track / podcast
        //       upload opens the multipart temp file with Files.newInputStream, which does NOT
        //       support mark/reset. RequestBody.fromInputStream over it throws
        //       "Content input stream does not support mark/reset, and was already read once." the
        //       moment the sync client re-reads the payload → HTTP 500 on every upload.
        // fromFile is re-readable (re-opens the file per read) and never heap-buffers, so it is
        // safe up to the 500MB cap for both cases. The spool is bounded (MAX_SPOOL_BYTES) so an
        // untrusted body can never fill the disk.
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
    // A well-formed Throwable chain is acyclic, but getCause() is overridable, so bound the walk to
    // guarantee termination even on a pathological self-referential cause.
    int maxHops = 64;
    for (Throwable t = sdkException; t != null && maxHops-- > 0; t = t.getCause()) {
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
   * content length. The copy itself enforces {@link #MAX_SPOOL_BYTES}, throwing {@link
   * FileTooLargeException} the moment the cap is exceeded — so the bound holds even if the caller
   * forgot to wrap {@code body} in its own limiting stream. When the caller's limiting stream is
   * present (the normal path via {@code MediaApplicationService}) it trips at the same point and the
   * domain exception propagates unchanged (→ HTTP 413). The temp file is always deleted.
   */
  private void putViaTempSpool(PutObjectRequest request, InputStream body) {
    Path spool;
    try {
      spool = Files.createTempFile("beatz-upload-", ".tmp");
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create temp file for unknown-length upload", e);
    }
    try {
      // A FileTooLargeException (RuntimeException) raised by the cap propagates out of the copy
      // unchanged; only genuine I/O failures are wrapped.
      spoolBounded(body, spool, maxSpoolBytes);
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

  /**
   * Stream {@code in} to {@code target}, aborting with {@link FileTooLargeException} the moment more
   * than {@code maxBytes} have been read. Enforces the upload cap inside the adapter — independently
   * of any caller-supplied limiting stream — so an unknown/untrusted-length body can never fill the
   * disk. The fixed buffer keeps heap usage flat regardless of body size.
   */
  private static void spoolBounded(InputStream in, Path target, long maxBytes) throws IOException {
    byte[] buf = new byte[8192];
    long total = 0;
    try (OutputStream out = Files.newOutputStream(target)) {
      int n;
      while ((n = in.read(buf)) != -1) {
        total += n;
        if (total > maxBytes) {
          throw new FileTooLargeException(
              "File exceeds maximum allowed size of " + (maxBytes / 1024 / 1024) + " MB");
        }
        out.write(buf, 0, n);
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
