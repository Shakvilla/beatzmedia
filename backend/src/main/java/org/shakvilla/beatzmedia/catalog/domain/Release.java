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
  private Visibility visibility;
  private Instant scheduledAt;
  private Instant wentLiveAt;
  private long listPriceMinor;
  private final Instant createdAt;
  private Instant updatedAt;
  private List<ReleaseTrack> tracks;
  private String genre;
  private String description;
  private List<SplitEntry> splits;

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
      List<ReleaseTrack> tracks,
      String genre,
      String description,
      List<SplitEntry> splits) {
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
    this.genre = genre;
    this.description = description;
    this.splits = List.copyOf(splits);
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
        scheduledAt, null, listPrice, now, now, tracks, null, null, List.of());
  }

  /**
   * Factory for creating a new draft release. Status is {@code draft}; tracks start empty and
   * {@code listPriceMinor} is 0 until {@link #submit}. LLFR-CATALOG-02.2 / WU-CAT-5.
   *
   * @param now current instant (supplied by caller via Clock port — never call Instant.now() here)
   */
  public static Release createDraft(
      String id,
      String artistId,
      String title,
      ReleaseType type,
      Visibility visibility,
      Instant scheduledAt,
      String genre,
      String description,
      Instant now) {
    return new Release(
        id, artistId, title, type, ReleaseStatus.draft, visibility,
        scheduledAt, null, 0L, now, now, List.of(), genre, description, List.of());
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
      List<ReleaseTrack> tracks,
      String genre,
      String description) {
    return new Release(
        id, artistId, title, type, status, visibility,
        scheduledAt, wentLiveAt, listPriceMinor, createdAt, updatedAt, tracks, genre, description,
        List.of());
  }

  /** Reconstitute a release including its persisted collaborator splits (WU-CAT-6). */
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
      List<ReleaseTrack> tracks,
      String genre,
      String description,
      List<SplitEntry> splits) {
    return new Release(
        id, artistId, title, type, status, visibility,
        scheduledAt, wentLiveAt, listPriceMinor, createdAt, updatedAt, tracks, genre, description,
        splits);
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
  // Draft create/upload/finalize lifecycle — WU-CAT-5 / LLFR-CATALOG-02.2, 02.4.
  //
  // draft --addTrack/removeTrack/replaceTracks/updateMetadata--> draft (mutation, draft-only)
  // draft --submit--> in_review (INV-5 recompute, INV-12 count checked by the caller)
  //
  // Any track/metadata mutation attempted outside draft throws IllegalTransitionException.
  // -------------------------------------------------------------------------

  private void requireDraft(String op) {
    if (this.status != ReleaseStatus.draft) {
      throw new IllegalTransitionException(this.status, op);
    }
  }

  /** Appends a track to the release. Draft-only. */
  public void addTrack(ReleaseTrack t, Instant now) {
    requireDraft("ADD_TRACK");
    var next = new java.util.ArrayList<>(this.tracks);
    next.add(t);
    this.tracks = List.copyOf(next);
    this.updatedAt = now;
  }

  /** Removes the track with the given id. Draft-only; a no-op if not present. */
  public void removeTrack(String trackId, Instant now) {
    requireDraft("REMOVE_TRACK");
    this.tracks = this.tracks.stream().filter(rt -> !rt.trackId().equals(trackId)).toList();
    this.updatedAt = now;
  }

  /** Replaces the entire ordered track list (positions, per-track prices). Draft-only. */
  public void replaceTracks(List<ReleaseTrack> tracks, Instant now) {
    requireDraft("REPLACE_TRACKS");
    this.tracks = List.copyOf(tracks);
    this.updatedAt = now;
  }

  /**
   * Updates draft metadata (genre/description/visibility/scheduledAt, optionally title).
   * Draft-only — use {@link #updateTitle} for a title-only edit on any status.
   */
  public void updateMetadata(
      String title, String genre, String description, Visibility visibility,
      Instant scheduledAt, Instant now) {
    requireDraft("UPDATE_METADATA");
    if (title != null) {
      this.title = title;
    }
    if (genre != null) {
      this.genre = genre;
    }
    if (description != null) {
      this.description = description;
    }
    if (visibility != null) {
      this.visibility = visibility;
    }
    if (scheduledAt != null) {
      this.scheduledAt = scheduledAt;
    }
    this.updatedAt = now;
  }

  /**
   * Finalizes a draft for review: {@code draft -> in_review}, recomputing {@code listPriceMinor}
   * via INV-5. The caller (application service) validates INV-12 track-count bounds before
   * calling this. Draft-only.
   */
  public void submit(int bundleDiscountPct, Instant now) {
    requireDraft("SUBMIT");
    validateSplitSums();
    this.listPriceMinor = computeListPrice(this.type, this.tracks, bundleDiscountPct);
    this.status = ReleaseStatus.in_review;
    this.updatedAt = now;
  }

  /** INV-12: for each track, Σ(collaborator percent) ≤ 100 (creator holds the remainder). */
  private void validateSplitSums() {
    java.util.Map<String, Integer> byTrack = new java.util.HashMap<>();
    for (SplitEntry s : splits) {
      byTrack.merge(s.trackId(), s.percent(), Integer::sum);
    }
    for (var e : byTrack.entrySet()) {
      if (e.getValue() > 100) {
        throw new SplitOver100Exception(
            "Split percentages for track " + e.getKey() + " sum to " + e.getValue() + " (> 100)");
      }
    }
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
  public String getGenre() { return genre; }
  public String getDescription() { return description; }
  public List<SplitEntry> getSplits() { return splits; }
}
