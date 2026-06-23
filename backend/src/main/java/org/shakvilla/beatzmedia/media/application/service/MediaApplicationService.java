package org.shakvilla.beatzmedia.media.application.service;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.media.application.port.in.IssueDeliveryUrlUseCase;
import org.shakvilla.beatzmedia.media.application.port.in.TranscodeUseCase;
import org.shakvilla.beatzmedia.media.application.port.in.UploadCommand;
import org.shakvilla.beatzmedia.media.application.port.in.UploadOriginalUseCase;
import org.shakvilla.beatzmedia.media.application.port.out.ArtworkProcessorPort;
import org.shakvilla.beatzmedia.media.application.port.out.MediaAssetRepository;
import org.shakvilla.beatzmedia.media.application.port.out.MediaService;
import org.shakvilla.beatzmedia.media.application.port.out.ObjectStorePort;
import org.shakvilla.beatzmedia.media.application.port.out.TranscodeJob;
import org.shakvilla.beatzmedia.media.application.port.out.TranscodeJobPort;
import org.shakvilla.beatzmedia.media.application.port.out.TranscodeResult;
import org.shakvilla.beatzmedia.media.application.port.out.UrlSignerPort;
import org.shakvilla.beatzmedia.media.application.port.out.VirusScanPort;
import org.shakvilla.beatzmedia.media.domain.DeliveryVariant;
import org.shakvilla.beatzmedia.media.domain.FileRejectedException;
import org.shakvilla.beatzmedia.media.domain.FileTooLargeException;
import org.shakvilla.beatzmedia.media.domain.MediaAsset;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.MediaHandle;
import org.shakvilla.beatzmedia.media.domain.MediaKind;
import org.shakvilla.beatzmedia.media.domain.MediaReady;
import org.shakvilla.beatzmedia.media.domain.MediaStatus;
import org.shakvilla.beatzmedia.media.domain.ObjectKey;
import org.shakvilla.beatzmedia.media.domain.SignedUrl;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;

/**
 * Primary application service implementing all media use cases and the {@link MediaService} facade.
 * Enforces INV-3 (preview gate), idempotency on (ownerRef, contentHash), and publishes
 * {@link MediaReady} after transcode completion. ADD §4 / §8.
 *
 * <h3>Streaming design (B3/S2/M-1)</h3>
 * The uploaded body is streamed exactly once through a chain:
 * <pre>
 *   body → [magic probe 12 bytes] → SequenceInputStream(probe || rest)
 *        → CountingLimitingInputStream (enforces MAX_SIZE on actual bytes)
 *        → DigestInputStream (SHA-256)
 *        → S3 PUT via RequestBody.fromInputStream (no heap buffer)
 * </pre>
 * After the S3 PUT completes, the SHA-256 digest is read from the {@link DigestInputStream}.
 * The client-declared {@code sizeBytes} is used as the content-length hint only when it is a
 * positive value ≤ {@link #MAX_SIZE_BYTES}; otherwise the AWS SDK uses chunked transfer encoding.
 */
@ApplicationScoped
public class MediaApplicationService
    implements UploadOriginalUseCase,
        TranscodeUseCase,
        IssueDeliveryUrlUseCase,
        MediaService {

  /** Maximum upload size: 500 MB in bytes. */
  public static final long MAX_SIZE_BYTES = 500L * 1024 * 1024;

  /** Number of bytes to read at the start of the stream for magic-byte detection. */
  private static final int MAGIC_PROBE_SIZE = 12;

  private final MediaAssetRepository repository;
  private final ObjectStorePort objectStore;
  private final UrlSignerPort urlSigner;
  private final TranscodeJobPort transcodeJobPort;
  private final VirusScanPort virusScan;
  private final ArtworkProcessorPort artworkProcessor;
  private final MagicByteValidator magicByteValidator;
  private final IdGenerator idGenerator;
  private final Clock clock;
  private final Event<MediaReady> mediaReadyEvent;
  private final int previewSeconds;

  @Inject
  public MediaApplicationService(
      MediaAssetRepository repository,
      ObjectStorePort objectStore,
      UrlSignerPort urlSigner,
      TranscodeJobPort transcodeJobPort,
      VirusScanPort virusScan,
      ArtworkProcessorPort artworkProcessor,
      MagicByteValidator magicByteValidator,
      IdGenerator idGenerator,
      Clock clock,
      Event<MediaReady> mediaReadyEvent,
      @ConfigProperty(name = "beatz.preview-seconds", defaultValue = "30") int previewSeconds) {
    this.repository = repository;
    this.objectStore = objectStore;
    this.urlSigner = urlSigner;
    this.transcodeJobPort = transcodeJobPort;
    this.virusScan = virusScan;
    this.artworkProcessor = artworkProcessor;
    this.magicByteValidator = magicByteValidator;
    this.idGenerator = idGenerator;
    this.clock = clock;
    this.mediaReadyEvent = mediaReadyEvent;
    this.previewSeconds = previewSeconds;
  }

  // ---- UploadOriginalUseCase ----

  @Override
  @Transactional
  public MediaHandle uploadOriginal(UploadCommand command) {
    // Read magic bytes prefix for format validation
    byte[] header = new byte[MAGIC_PROBE_SIZE];
    int bytesRead;
    try {
      bytesRead = command.body().readNBytes(header, 0, MAGIC_PROBE_SIZE);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read upload stream", e);
    }
    byte[] effectiveHeader =
        (bytesRead < MAGIC_PROBE_SIZE) ? java.util.Arrays.copyOf(header, bytesRead) : header;

    // Magic-byte format validation — must happen before storing
    magicByteValidator.validate(command.kind(), effectiveHeader);

    // Reconstruct full stream: prepend the already-read magic bytes
    InputStream reconstructed =
        new SequenceInputStream(new ByteArrayInputStream(effectiveHeader), command.body());

    // Wrap with a counting/limiting stream that enforces the size cap on ACTUAL bytes,
    // not the client-declared sizeBytes (which is untrusted).
    // This is the primary size enforcement; quarkus.http.limits.max-body-size is the backstop.
    CountingLimitingInputStream countingStream =
        new CountingLimitingInputStream(reconstructed, MAX_SIZE_BYTES);

    // Wrap with a DigestInputStream so we compute the SHA-256 in one pass (no second read)
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
    DigestInputStream digestStream = new DigestInputStream(countingStream, digest);

    // Generate asset id and determine content type before the PUT
    String assetIdStr = idGenerator.newId();
    MediaAssetId assetId = new MediaAssetId(assetIdStr);
    String contentType = resolveContentType(command.kind(), effectiveHeader);

    // Determine content-length hint: use the declared value only if it is trustworthy
    // (positive and within the allowed max). Pass -1 for chunked transfer otherwise.
    long contentLengthHint =
        (command.sizeBytes() > 0 && command.sizeBytes() <= MAX_SIZE_BYTES)
            ? command.sizeBytes()
            : -1L;

    // Stream the body to S3 — ONE pass. The DigestInputStream computes hash while S3 receives.
    // CountingLimitingInputStream aborts with FileTooLargeException if actual bytes exceed max.
    ObjectKey originalKey;
    try {
      originalKey =
          objectStore.putOriginal(command.kind(), assetId, digestStream, contentType, contentLengthHint);
    } catch (FileTooLargeException e) {
      throw e; // already the right domain exception
    }

    // After the single-pass PUT, extract the computed hash from the digest
    String contentHash = command.contentHash();
    if (contentHash == null || contentHash.isBlank()) {
      contentHash = HexFormat.of().formatHex(digest.digest());
    }

    // Virus scan the stored original
    if (!virusScan.isClean(originalKey)) {
      // H-1: purge the (possibly malicious) stored object before throwing
      try {
        objectStore.deleteOriginal(originalKey);
      } catch (Exception deleteEx) {
        // Log but do not suppress the primary rejection — the object may not be deletable
        // in some failure modes; operators should have alerting on orphaned originals.
        // We still throw FileRejectedException so the caller gets the right HTTP 422.
        throw new FileRejectedException(
            "File failed safety scan. Cleanup of stored object may have failed: "
                + deleteEx.getMessage());
      }
      throw new FileRejectedException("File failed safety scan and was rejected");
    }

    // Idempotency: return existing handle if same owner + content already uploaded
    // (checked after hash is computed, so null-hash uploads also benefit)
    final String finalHash = contentHash;
    Optional<MediaAsset> existing =
        repository.findByOwnerRefAndContentHash(command.ownerRef(), finalHash);
    if (existing.isPresent()) {
      // Clean up the duplicate original we just stored
      objectStore.deleteOriginal(originalKey);
      return existing.get().toHandle();
    }

    // Persist in UPLOADING state (duration 0; probed async by transcode job)
    MediaAsset asset =
        MediaAsset.createUploading(
            assetId,
            command.ownerRef(),
            command.kind(),
            originalKey,
            0,
            clock.now(),
            finalHash);
    repository.save(asset);

    // For AUDIO: enqueue transcode job (runs off the request thread)
    if (command.kind() == MediaKind.AUDIO) {
      transcodeJobPort.submit(new TranscodeJob(assetId, originalKey, previewSeconds));
    }

    return asset.toHandle();
  }

  // ---- TranscodeUseCase ----

  @Override
  @Transactional
  public void enqueueTranscode(MediaAssetId assetId) {
    MediaAsset asset = requireAsset(assetId);
    if (asset.getStatus() == MediaStatus.TRANSCODING) {
      return; // idempotent: no-op when already in progress
    }
    asset.startTranscoding();
    repository.save(asset);
    transcodeJobPort.submit(new TranscodeJob(assetId, asset.getOriginalKey(), previewSeconds));
  }

  // ---- IssueDeliveryUrlUseCase ----

  @Override
  public SignedUrl issueSignedUrl(MediaAssetId assetId, DeliveryVariant variant, Duration ttl) {
    MediaAsset asset = requireAsset(assetId);
    // INV-3: resolveDeliveryKey enforces FULL → hlsKey only when variant == FULL; PREVIEW → previewKey
    ObjectKey deliveryKey = asset.resolveDeliveryKey(variant);
    return urlSigner.presignGet(deliveryKey, variant, ttl);
  }

  // ---- MediaService facade ----

  @Override
  public int probeDuration(MediaAssetId assetId) {
    return requireAsset(assetId).getDurationSec();
  }

  @Override
  public void transcodeToHls(MediaAssetId assetId) {
    enqueueTranscode(assetId);
  }

  @Override
  public void generatePreviewClip(MediaAssetId assetId) {
    enqueueTranscode(assetId);
  }

  @Override
  @Transactional
  public MediaHandle processArtwork(MediaAssetId assetId) {
    MediaAsset asset = requireAsset(assetId);
    // Process image variants (validates format, copies to delivery bucket)
    // Returns a key in the DELIVERY bucket (e.g. delivery/{id}/art/cover-1024.jpg)
    ObjectKey deliveryKey = artworkProcessor.processVariants(asset.getOriginalKey(), assetId);
    asset.markArtworkReady(deliveryKey);
    repository.save(asset);
    return asset.toHandle();
  }

  // ---- Transcode lifecycle callbacks (called from the async worker, each own @Transactional) ----

  /**
   * Persists the TRANSCODING status transition. Called by the transcode worker immediately before
   * invoking ffmpeg, in its own transaction. B4: ensures the state is TRANSCODING before READY.
   */
  @Transactional
  public void markTranscoding(MediaAssetId assetId) {
    MediaAsset asset = requireAsset(assetId);
    if (asset.getStatus() == MediaStatus.TRANSCODING) {
      return; // already in progress, idempotent
    }
    asset.startTranscoding();
    repository.save(asset);
  }

  @Transactional
  public void handleTranscodeResult(TranscodeResult result) {
    MediaAsset asset = requireAsset(result.assetId());
    if (result.ok()) {
      asset.markReady(result.hlsKey(), result.previewKey(), result.durationSec());
      repository.save(asset);
      mediaReadyEvent.fire(new MediaReady(asset.getId(), asset.getOwnerRef(), asset.getKind()));
    } else {
      asset.markError();
      repository.save(asset);
    }
  }

  // ---- Helpers ----

  private MediaAsset requireAsset(MediaAssetId assetId) {
    return repository
        .findById(assetId)
        .orElseThrow(() -> new NotFoundException("MediaAsset not found: " + assetId.value()));
  }

  private String resolveContentType(MediaKind kind, byte[] header) {
    if (kind == MediaKind.AUDIO) {
      return switch (magicByteValidator.detectAudioFormat(header)) {
        case WAV -> "audio/wav";
        case FLAC -> "audio/flac";
      };
    } else {
      return switch (magicByteValidator.detectImageFormat(header)) {
        case PNG -> "image/png";
        case JPG -> "image/jpeg";
      };
    }
  }

  // ---- Inner class: counting + limiting InputStream (B3 / M-1) ----

  /**
   * Wraps an {@link InputStream} and counts actual bytes read. If the byte count exceeds
   * {@code maxBytes}, a {@link FileTooLargeException} is thrown immediately — before the full
   * body has been buffered. This enforces the size cap on actual transferred bytes, not the
   * client-declared content-length. The Quarkus {@code http.limits.max-body-size} is the
   * first-line backstop (set generously for WAV/FLAC); this stream is the second-line enforcement
   * within the application layer.
   */
  static final class CountingLimitingInputStream extends FilterInputStream {

    private final long maxBytes;
    private long bytesRead = 0;

    CountingLimitingInputStream(InputStream in, long maxBytes) {
      super(in);
      this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
      int b = super.read();
      if (b != -1) {
        checkLimit(1);
      }
      return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
      int n = super.read(buf, off, len);
      if (n > 0) {
        checkLimit(n);
      }
      return n;
    }

    private void checkLimit(int n) {
      bytesRead += n;
      if (bytesRead > maxBytes) {
        throw new FileTooLargeException(
            "File exceeds maximum allowed size of " + (maxBytes / 1024 / 1024) + " MB");
      }
    }

    long getBytesRead() {
      return bytesRead;
    }
  }
}
