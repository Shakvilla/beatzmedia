package org.shakvilla.beatzmedia.media.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code media_asset} table. Domain objects carry no ORM annotations —
 * mapping is done in {@link MediaAssetMapper}. ADD §7 / conventions §6.
 */
@Entity
@Table(name = "media_asset")
public class MediaAssetEntity {

  @Id
  @Column(name = "id", nullable = false, length = 40)
  public String id;

  @Column(name = "owner_ref", nullable = false, length = 80)
  public String ownerRef;

  @Column(name = "kind", nullable = false, length = 16)
  public String kind;

  @Column(name = "status", nullable = false, length = 16)
  public String status;

  @Column(name = "duration_sec")
  public Integer durationSec;

  @Column(name = "original_key", nullable = false, length = 255)
  public String originalKey;

  @Column(name = "hls_key", length = 255)
  public String hlsKey;

  @Column(name = "preview_key", length = 255)
  public String previewKey;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "content_hash", length = 64)
  public String contentHash;
}
