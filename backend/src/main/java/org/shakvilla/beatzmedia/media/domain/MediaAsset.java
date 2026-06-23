package org.shakvilla.beatzmedia.media.domain;

import java.time.Instant;

/**
 * Aggregate root for a binary asset in the media pipeline. Owns the lifecycle state machine
 * (UPLOADING → TRANSCODING → READY / ERROR) and enforces INV-3 (preview gate).
 * Framework-free — no Jakarta/Quarkus/Hibernate imports. ADD §3.
 */
public class MediaAsset {

  private final MediaAssetId id;
  private final OwnerRef ownerRef;
  private final MediaKind kind;
  private MediaStatus status;
  private int durationSec;
  private final ObjectKey originalKey;
  private ObjectKey hlsKey;
  private ObjectKey previewKey;
  private final Instant createdAt;
  private final String contentHash;

  /** Full constructor for reconstitution from persistence (all fields known). */
  public MediaAsset(
      MediaAssetId id,
      OwnerRef ownerRef,
      MediaKind kind,
      MediaStatus status,
      int durationSec,
      ObjectKey originalKey,
      ObjectKey hlsKey,
      ObjectKey previewKey,
      Instant createdAt,
      String contentHash) {
    this.id = id;
    this.ownerRef = ownerRef;
    this.kind = kind;
    this.status = status;
    this.durationSec = durationSec;
    this.originalKey = originalKey;
    this.hlsKey = hlsKey;
    this.previewKey = previewKey;
    this.createdAt = createdAt;
    this.contentHash = contentHash;
  }

  /**
   * Factory — create a brand-new UPLOADING asset (original has been streamed to object store).
   * Duration is 0 until probed; populated in the same transaction.
   */
  public static MediaAsset createUploading(
      MediaAssetId id,
      OwnerRef ownerRef,
      MediaKind kind,
      ObjectKey originalKey,
      int durationSec,
      Instant now,
      String contentHash) {
    return new MediaAsset(
        id, ownerRef, kind, MediaStatus.UPLOADING, durationSec,
        originalKey, null, null, now, contentHash);
  }

  // ---- State-machine transitions ----

  /**
   * Move to TRANSCODING. Legal from UPLOADING or ERROR (retry). Throws if already READY or already
   * TRANSCODING to enforce idempotency at the domain level.
   */
  public void startTranscoding() {
    if (status == MediaStatus.TRANSCODING) {
      return; // idempotent — already in progress
    }
    if (status == MediaStatus.READY) {
      throw new IllegalStateException("Cannot re-transcode a READY asset: " + id.value());
    }
    this.status = MediaStatus.TRANSCODING;
  }

  /**
   * Move to READY after both hls + preview keys are written. Enforces the invariant that READY is
   * only reached when both delivery renditions exist (for AUDIO). ADD §3 / DoD §12.
   */
  public void markReady(ObjectKey hlsKey, ObjectKey previewKey, int durationSec) {
    if (kind == MediaKind.AUDIO) {
      if (hlsKey == null || previewKey == null) {
        throw new IllegalArgumentException(
            "AUDIO asset requires both hlsKey and previewKey to reach READY");
      }
    }
    this.hlsKey = hlsKey;
    this.previewKey = previewKey;
    this.durationSec = durationSec;
    this.status = MediaStatus.READY;
  }

  /** Move artwork to READY with a delivery key. */
  public void markArtworkReady(ObjectKey artKey) {
    if (kind != MediaKind.ARTWORK) {
      throw new IllegalStateException("markArtworkReady called on non-ARTWORK asset");
    }
    this.previewKey = artKey;
    this.status = MediaStatus.READY;
  }

  /** Move to ERROR (transcode/probe failure). */
  public void markError() {
    this.status = MediaStatus.ERROR;
  }

  /**
   * INV-3 enforcement: resolve which {@link ObjectKey} to presign for a given
   * {@link DeliveryVariant}. FULL is only returned when the caller has explicitly asserted
   * ownership (variant == FULL). There is NO code path that returns hlsKey for variant == PREVIEW.
   */
  public ObjectKey resolveDeliveryKey(DeliveryVariant variant) {
    if (status != MediaStatus.READY) {
      throw new IllegalStateException("Asset is not READY: " + id.value() + " status=" + status);
    }
    if (variant == DeliveryVariant.FULL) {
      if (hlsKey == null) {
        throw new IllegalStateException("hlsKey is null for asset " + id.value());
      }
      return hlsKey;
    }
    // PREVIEW: always return the preview key (30s clip)
    if (previewKey == null) {
      throw new IllegalStateException("previewKey is null for asset " + id.value());
    }
    return previewKey;
  }

  // ---- Accessors ----

  public MediaAssetId getId() {
    return id;
  }

  public OwnerRef getOwnerRef() {
    return ownerRef;
  }

  public MediaKind getKind() {
    return kind;
  }

  public MediaStatus getStatus() {
    return status;
  }

  public int getDurationSec() {
    return durationSec;
  }

  public ObjectKey getOriginalKey() {
    return originalKey;
  }

  public ObjectKey getHlsKey() {
    return hlsKey;
  }

  public ObjectKey getPreviewKey() {
    return previewKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getContentHash() {
    return contentHash;
  }

  public MediaHandle toHandle() {
    return new MediaHandle(id, kind, durationSec, status);
  }
}
