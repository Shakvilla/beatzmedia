package org.shakvilla.beatzmedia.commerce.domain;

import java.time.Instant;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * OwnershipGrant aggregate root — the authoritative record that an account owns a track or episode,
 * created ONLY on confirmed settlement (INV-1) and revoked on refund (INV-9). Commerce ADD §3.
 *
 * <p>Exactly one of {@code trackId} / {@code episodeId} is set (a grant targets a single ownable
 * unit). Album/season purchases expand upstream to one grant per constituent track/episode (INV-2).
 * A grant is <em>active</em> while {@code revokedAt IS NULL}. Domain-layer; no framework imports.
 */
public final class OwnershipGrant {

  private final String id;
  private final AccountId accountId;
  private final String trackId;
  private final String episodeId;
  private final OrderId sourceOrderId;
  private final Instant grantedAt;
  private Instant revokedAt;
  private final boolean downloadable;

  public OwnershipGrant(
      String id,
      AccountId accountId,
      String trackId,
      String episodeId,
      OrderId sourceOrderId,
      Instant grantedAt,
      Instant revokedAt,
      boolean downloadable) {
    boolean hasTrack = trackId != null && !trackId.isBlank();
    boolean hasEpisode = episodeId != null && !episodeId.isBlank();
    if (hasTrack == hasEpisode) {
      throw new IllegalArgumentException("exactly one of trackId/episodeId must be set");
    }
    this.id = id;
    this.accountId = accountId;
    this.trackId = hasTrack ? trackId : null;
    this.episodeId = hasEpisode ? episodeId : null;
    this.sourceOrderId = sourceOrderId;
    this.grantedAt = grantedAt;
    this.revokedAt = revokedAt;
    this.downloadable = downloadable;
  }

  /**
   * Grant ownership of a track (INV-1/INV-2). {@code downloadable} is the owning release's download
   * permission AS IT STOOD at settlement — captured once here and never updated afterwards, even if
   * the artist later changes the release's choice (PRD OQ-8: an artist changing their mind is less
   * adversarial than a takedown, and a takedown already preserves owners' downloads).
   */
  public static OwnershipGrant forTrack(
      String id,
      AccountId accountId,
      String trackId,
      OrderId sourceOrderId,
      Instant grantedAt,
      boolean downloadable) {
    return new OwnershipGrant(
        id, accountId, trackId, null, sourceOrderId, grantedAt, null, downloadable);
  }

  /**
   * Grant ownership of a premium episode (INV-1/INV-2). Always {@code downloadable = false}:
   * downloads are a music-release feature and podcasts are out of scope for it. This is the
   * fail-closed direction — nothing becomes downloadable that no artist ever agreed to.
   */
  public static OwnershipGrant forEpisode(
      String id, AccountId accountId, String episodeId, OrderId sourceOrderId, Instant grantedAt) {
    return new OwnershipGrant(
        id, accountId, null, episodeId, sourceOrderId, grantedAt, null, false);
  }

  /** Revoke this grant (INV-9), setting {@code revokedAt}. Idempotent — re-revoking is a no-op. */
  public void revoke(Instant at) {
    if (revokedAt == null) {
      this.revokedAt = at;
    }
  }

  public boolean isActive() {
    return revokedAt == null;
  }

  public String getId() {
    return id;
  }

  public AccountId getAccountId() {
    return accountId;
  }

  public String getTrackId() {
    return trackId;
  }

  public String getEpisodeId() {
    return episodeId;
  }

  public OrderId getSourceOrderId() {
    return sourceOrderId;
  }

  public Instant getGrantedAt() {
    return grantedAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  /**
   * Whether the buyer may download this grant's audio — the release's permission as it stood at
   * settlement (captured once, never re-read from the release afterwards). Always {@code false} for
   * an episode grant (downloads are a music-release feature).
   */
  public boolean isDownloadable() {
    return downloadable;
  }
}
