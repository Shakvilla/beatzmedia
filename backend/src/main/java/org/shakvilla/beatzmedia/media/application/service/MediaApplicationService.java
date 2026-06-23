package org.shakvilla.beatzmedia.media.application.service;

import java.io.ByteArrayInputStream;
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
    // Size guard (stream-level; Quarkus http.limits is the first line)
    if (command.sizeBytes() > MAX_SIZE_BYTES) {
      throw new FileTooLargeException(
          "File exceeds maximum allowed size of " + (MAX_SIZE_BYTES / 1024 / 1024) + " MB");
    }

    // Read magic bytes prefix, then reconstruct the full stream
    byte[] header = new byte[MAGIC_PROBE_SIZE];
    int bytesRead;
    try {
      bytesRead = command.body().readNBytes(header, 0, MAGIC_PROBE_SIZE);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read upload stream", e);
    }
    byte[] effectiveHeader =
        (bytesRead < MAGIC_PROBE_SIZE) ? java.util.Arrays.copyOf(header, bytesRead) : header;

    // Magic-byte format validation
    magicByteValidator.validate(command.kind(), effectiveHeader);

    // Reconstruct stream: put the already-read header back at the front
    InputStream fullStream =
        new SequenceInputStream(new ByteArrayInputStream(effectiveHeader), command.body());

    // Use caller-supplied content hash, or compute on the fly
    String contentHash = command.contentHash();
    if (contentHash == null || contentHash.isBlank()) {
      contentHash = computeHash(fullStream);
      // Stream was consumed by hashing — rebuild it
      fullStream =
          new SequenceInputStream(new ByteArrayInputStream(effectiveHeader), command.body());
    }

    // Idempotency: return existing handle if same owner + content already uploaded
    Optional<MediaAsset> existing =
        repository.findByOwnerRefAndContentHash(command.ownerRef(), contentHash);
    if (existing.isPresent()) {
      return existing.get().toHandle();
    }

    // Store original in private originals bucket
    String assetIdStr = idGenerator.newId();
    MediaAssetId assetId = new MediaAssetId(assetIdStr);

    String contentType = resolveContentType(command.kind(), effectiveHeader);
    ObjectKey originalKey =
        objectStore.putOriginal(command.kind(), assetId, fullStream, contentType);

    // Virus scan the stored original
    if (!virusScan.isClean(originalKey)) {
      throw new FileRejectedException("File failed safety scan and was rejected");
    }

    // Persist in UPLOADING state (duration 0; probed async by transcode job)
    MediaAsset asset =
        MediaAsset.createUploading(
            assetId, command.ownerRef(), command.kind(), originalKey, 0, clock.now(), contentHash);
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
    asset.markArtworkReady(asset.getOriginalKey());
    repository.save(asset);
    return asset.toHandle();
  }

  // ---- Transcode result callback (called from the worker, its own @Transactional unit) ----

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

  private String computeHash(InputStream stream) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      try (DigestInputStream dis = new DigestInputStream(stream, md)) {
        byte[] buf = new byte[8192];
        // noinspection StatementWithEmptyBody
        while (dis.read(buf) != -1) {
          // drain stream to compute digest
        }
      }
      return HexFormat.of().formatHex(md.digest());
    } catch (NoSuchAlgorithmException | IOException e) {
      throw new IllegalStateException("Failed to compute content hash", e);
    }
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
}
