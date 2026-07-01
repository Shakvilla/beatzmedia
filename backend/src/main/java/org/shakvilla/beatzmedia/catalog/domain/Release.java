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
  private Instant scheduledAt;
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
   *
   * @param now current instant (supplied by caller via Clock port — never call Instant.now() here)
   */
  public static Release create(
      String id,
      String artistId,
      String title,
      ReleaseType type,
      Visibility visibility,
      Instant scheduledAt,
      List<ReleaseTrack> tracks,
      int bundleDiscountPct,
      Instant now) {

    long listPrice = computeListPrice(type, tracks, bundleDiscountPct);
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

  public void updateTitle(String newTitle, Instant now) {
    this.title = newTitle;
    this.updatedAt = now;
  }

  public void markUpdated(Instant now) {
    this.updatedAt = now;
  }

  // -------------------------------------------------------------------------
  // Release lifecycle FSM — LLFR-CATALOG-02.5 / catalog ADD §3, §8.
  //
  // draft --submit--> in_review
  // in_review --approve(future date)--> scheduled
  // in_review --approve(immediate)--> live
  // scheduled --go-live (scheduler, INV-7)--> live
  // live --takedown--> takedown
  // takedown --reinstate--> live
  //
  // Any other edge throws IllegalTransitionException (409 ILLEGAL_TRANSITION).
  // -------------------------------------------------------------------------

  /**
   * Admin approves an {@code in_review} release for a future go-live instant. Legal only from
   * {@code in_review}. The release becomes {@code scheduled}; it must not be publicly
   * readable/streamable before {@code scheduledAt} (INV-7).
   *
   * @throws IllegalTransitionException if not currently {@code in_review}
   * @throws IllegalArgumentException if {@code scheduledAt} is not strictly in the future of {@code now}
   */
  public void approveScheduled(Instant scheduledAt, Instant now) {
    if (this.status != ReleaseStatus.in_review) {
      throw new IllegalTransitionException(this.status, "APPROVE_SCHEDULED");
    }
    if (scheduledAt == null || !scheduledAt.isAfter(now)) {
      throw new IllegalArgumentException("scheduledAt must be strictly in the future");
    }
    this.status = ReleaseStatus.scheduled;
    this.scheduledAt = scheduledAt;
    this.updatedAt = now;
  }

  /**
   * Admin approves an {@code in_review} release for immediate publication. Legal only from
   * {@code in_review}. The release becomes {@code live} and {@code wentLiveAt} is stamped once.
   *
   * @throws IllegalTransitionException if not currently {@code in_review}
   */
  public void approveImmediate(Instant now) {
    if (this.status != ReleaseStatus.in_review) {
      throw new IllegalTransitionException(this.status, "APPROVE_IMMEDIATE");
    }
    this.status = ReleaseStatus.live;
    this.wentLiveAt = now;
    this.updatedAt = now;
  }

  /**
   * System/scheduler transition: a {@code scheduled} release whose {@code scheduledAt} has passed
   * becomes {@code live}. Legal only from {@code scheduled}. Idempotent by construction at the
   * call site: once {@code wentLiveAt} is set the release is no longer {@code scheduled}, so a
   * repeat call throws {@link IllegalTransitionException} rather than re-firing side effects
   * (INV-7 exactly-once).
   *
   * @throws IllegalTransitionException if not currently {@code scheduled}
   */
  public void goLive(Instant now) {
    if (this.status != ReleaseStatus.scheduled) {
      throw new IllegalTransitionException(this.status, "GO_LIVE");
    }
    this.status = ReleaseStatus.live;
    this.wentLiveAt = now;
    this.updatedAt = now;
  }

  /**
   * Admin takes a {@code live} release down. Legal only from {@code live}.
   *
   * @throws IllegalTransitionException if not currently {@code live}
   */
  public void takedown(Instant now) {
    if (this.status != ReleaseStatus.live) {
      throw new IllegalTransitionException(this.status, "TAKEDOWN");
    }
    this.status = ReleaseStatus.takedown;
    this.updatedAt = now;
  }

  /**
   * Admin reinstates a {@code takedown} release back to {@code live}. Legal only from
   * {@code takedown}.
   *
   * @throws IllegalTransitionException if not currently {@code takedown}
   */
  public void reinstate(Instant now) {
    if (this.status != ReleaseStatus.takedown) {
      throw new IllegalTransitionException(this.status, "REINSTATE");
    }
    this.status = ReleaseStatus.live;
    this.updatedAt = now;
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
