package org.shakvilla.beatzmedia.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * Studio release aggregate. Owns the list of tracks and computes the list price applying the INV-5
 * bundle discount. Framework-free; no Jakarta/Quarkus/Hibernate imports. Catalog ADD §3.
 *
 * <p>INV-5 bundle discount: {@code listPriceMinor = roundHalfUp(Σ priceMinor × (100 −
 * bundleDiscountPct) / 100)}. For {@code single} type no discount is applied.
 */
public final class Release {

  private final String id;
  private final String artistId;
  private String title;
  private final ReleaseType type;
  private ReleaseStatus status;
  private final Visibility visibility;
  private final Instant scheduledAt;
  private Instant wentLiveAt;
  private final long listPriceMinor;
  private final Instant createdAt;
  private Instant updatedAt;
  private final List<ReleaseTrack> tracks;

  private Release(
      String id,
      String artistId,
      String title,
      ReleaseType type,
      ReleaseStatus status,
      Visibility visibility,
      Instant scheduledAt,
      Instant wentLiveAt,
      long listPriceMinor,
      Instant createdAt,
      Instant updatedAt,
      List<ReleaseTrack> tracks) {
    this.id = id;
    this.artistId = artistId;
    this.title = title;
    this.type = type;
    this.status = status;
    this.visibility = visibility;
    this.scheduledAt = scheduledAt;
    this.wentLiveAt = wentLiveAt;
    this.listPriceMinor = listPriceMinor;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.tracks = List.copyOf(tracks);
  }

  /**
   * Factory for creating a new release submission. Computes list price via INV-5. Status is set to
   * {@code in_review} automatically.
   */
  public static Release create(
      String id,
      String artistId,
      String title,
      ReleaseType type,
      Visibility visibility,
      Instant scheduledAt,
      List<ReleaseTrack> tracks,
      int bundleDiscountPct) {

    long listPrice = computeListPrice(type, tracks, bundleDiscountPct);
    Instant now = Instant.now();
    return new Release(
        id, artistId, title, type, ReleaseStatus.in_review, visibility,
        scheduledAt, null, listPrice, now, now, tracks);
  }

  /** Factory for reconstituting a release from DB storage. */
  public static Release reconstitute(
      String id,
      String artistId,
      String title,
      ReleaseType type,
      ReleaseStatus status,
      Visibility visibility,
      Instant scheduledAt,
      Instant wentLiveAt,
      long listPriceMinor,
      Instant createdAt,
      Instant updatedAt,
      List<ReleaseTrack> tracks) {
    return new Release(
        id, artistId, title, type, status, visibility,
        scheduledAt, wentLiveAt, listPriceMinor, createdAt, updatedAt, tracks);
  }

  private static long computeListPrice(
      ReleaseType type, List<ReleaseTrack> tracks, int bundleDiscountPct) {
    long sum = tracks.stream().mapToLong(ReleaseTrack::priceMinor).sum();
    if (type == ReleaseType.single) {
      return sum; // no discount for singles
    }
    // INV-5: roundHalfUp(Σ priceMinor × (100 - bundleDiscountPct) / 100)
    BigDecimal total = BigDecimal.valueOf(sum)
        .multiply(BigDecimal.valueOf(100 - bundleDiscountPct))
        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    return total.longValue();
  }

  public void updateTitle(String newTitle) {
    this.title = newTitle;
    this.updatedAt = Instant.now();
  }

  public void markUpdated() {
    this.updatedAt = Instant.now();
  }

  public String getId() { return id; }
  public String getArtistId() { return artistId; }
  public String getTitle() { return title; }
  public ReleaseType getType() { return type; }
  public ReleaseStatus getStatus() { return status; }
  public Visibility getVisibility() { return visibility; }
  public Instant getScheduledAt() { return scheduledAt; }
  public Instant getWentLiveAt() { return wentLiveAt; }
  public long getListPriceMinor() { return listPriceMinor; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public List<ReleaseTrack> getTracks() { return tracks; }
}
