package org.shakvilla.beatzmedia.studio.domain;

import java.time.Instant;

import org.shakvilla.beatzmedia.platform.domain.Currency;

/**
 * Podcast episode aggregate. Framework-free; no Jakarta/Quarkus/Hibernate imports. Lifecycle via
 * {@link EpisodeStatus} (INV-7); {@code premium ⇒ priceMinor > 0} is enforced on every constructor
 * / price mutation (422 {@link InvalidPriceException}, backstopped by the DB {@code
 * chk_premium_price} constraint). Studio ADD §3 (WU-STU-2).
 *
 * <h3>State machine (strict — INV-7)</h3>
 *
 * <pre>
 *   draft     --publishNow()-->  published   (fires EpisodePublished)
 *   draft     --scheduleAt(at)--> scheduled
 *   scheduled --unschedule()-->  draft
 *   scheduled --goLive(now)-->   published   (SYSTEM-ONLY: the go-live scheduler, INV-7 exactly-once;
 *                                              fires EpisodePublished)
 * </pre>
 *
 * <p>{@code scheduled → published} is reachable ONLY via {@link #goLive(Instant)} (the scheduler) —
 * never via a manual PATCH — so a scheduled episode is never made publicly listed/streamable before
 * its {@code scheduledAt} by any path (INV-7). {@code published} is terminal: no further status
 * transition is permitted.
 */
public final class Episode {

  private final EpisodeId id;
  private ShowId showId;
  private final ArtistId artistId;
  private String title;
  private String description;
  private final String audioKey;
  private String coverUrl;
  private final int durationSec;
  private EpisodeStatus status;
  private boolean premium;
  private long priceMinor;
  private Currency currency;
  private boolean earlyAccess;
  private Instant scheduledAt;
  private Instant publishedAt;
  private long plays;
  private final Instant createdAt;
  private final String idempotencyKey;
  private final String requestHash;

  private Episode(
      EpisodeId id,
      ShowId showId,
      ArtistId artistId,
      String title,
      String description,
      String audioKey,
      String coverUrl,
      int durationSec,
      EpisodeStatus status,
      boolean premium,
      long priceMinor,
      Currency currency,
      boolean earlyAccess,
      Instant scheduledAt,
      Instant publishedAt,
      long plays,
      Instant createdAt,
      String idempotencyKey,
      String requestHash) {
    guardPremiumPrice(premium, priceMinor);
    this.id = id;
    this.showId = showId;
    this.artistId = artistId;
    this.title = title;
    this.description = description;
    this.audioKey = audioKey;
    this.coverUrl = coverUrl;
    this.durationSec = durationSec;
    this.status = status;
    this.premium = premium;
    this.priceMinor = priceMinor;
    this.currency = currency;
    this.earlyAccess = earlyAccess;
    this.scheduledAt = scheduledAt;
    this.publishedAt = publishedAt;
    this.plays = plays;
    this.createdAt = createdAt;
    this.idempotencyKey = idempotencyKey;
    this.requestHash = requestHash;
  }

  /** Factory for a freshly-uploaded episode: always starts {@code draft}, then the application
   * service immediately calls {@link #publishNow(Instant)} or {@link #scheduleAt(Instant)}
   * depending on the requested visibility, before the first persist. */
  public static Episode createDraft(
      EpisodeId id,
      ShowId showId,
      ArtistId artistId,
      String title,
      String description,
      String audioKey,
      String coverUrl,
      int durationSec,
      boolean premium,
      long priceMinor,
      Currency currency,
      boolean earlyAccess,
      Instant now,
      String idempotencyKey,
      String requestHash) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    return new Episode(
        id, showId, artistId, title.trim(), description, audioKey, coverUrl, durationSec,
        EpisodeStatus.draft, premium, priceMinor, currency, earlyAccess, null, null, 0L, now,
        idempotencyKey, requestHash);
  }

  /** Factory for reconstituting an episode from DB storage. */
  public static Episode reconstitute(
      EpisodeId id,
      ShowId showId,
      ArtistId artistId,
      String title,
      String description,
      String audioKey,
      String coverUrl,
      int durationSec,
      EpisodeStatus status,
      boolean premium,
      long priceMinor,
      Currency currency,
      boolean earlyAccess,
      Instant scheduledAt,
      Instant publishedAt,
      long plays,
      Instant createdAt,
      String idempotencyKey,
      String requestHash) {
    return new Episode(
        id, showId, artistId, title, description, audioKey, coverUrl, durationSec, status, premium,
        priceMinor, currency, earlyAccess, scheduledAt, publishedAt, plays, createdAt,
        idempotencyKey, requestHash);
  }

  // ---- Lifecycle transitions (INV-7) ----

  /** {@code draft -> published}, publish-now. */
  public void publishNow(Instant now) {
    if (status != EpisodeStatus.draft) {
      throw new IllegalEpisodeTransitionException(status, "publish");
    }
    this.status = EpisodeStatus.published;
    this.publishedAt = now;
    this.scheduledAt = null;
  }

  /** {@code draft -> scheduled}. {@code at} must be strictly in the future (checked by the caller
   * via {@link ScheduleDateRequiredException} before this is invoked). */
  public void scheduleAt(Instant at) {
    if (status != EpisodeStatus.draft) {
      throw new IllegalEpisodeTransitionException(status, "schedule");
    }
    this.status = EpisodeStatus.scheduled;
    this.scheduledAt = at;
  }

  /** {@code scheduled -> draft} (unschedule, via PATCH). */
  public void unschedule() {
    if (status != EpisodeStatus.scheduled) {
      throw new IllegalEpisodeTransitionException(status, "unschedule");
    }
    this.status = EpisodeStatus.draft;
    this.scheduledAt = null;
  }

  /** {@code scheduled -> scheduled}, with a new {@code scheduledAt} (reschedule via PATCH). {@code
   * at} must be strictly in the future (checked by the caller via {@link
   * ScheduleDateRequiredException} before this is invoked). */
  public void reschedule(Instant at) {
    if (status != EpisodeStatus.scheduled) {
      throw new IllegalEpisodeTransitionException(status, "reschedule");
    }
    this.scheduledAt = at;
  }

  /** {@code scheduled -> published}. SYSTEM-ONLY (the go-live scheduler, INV-7). Guard-idempotent:
   * a repeat call on an already-published episode throws rather than re-firing the event; the
   * caller ({@code EpisodeGoLiveJob}/sweep) only invokes this for rows still {@code status =
   * scheduled} at read time, so a race is the only way this guard is ever hit in practice. */
  public void goLive(Instant now) {
    if (status != EpisodeStatus.scheduled) {
      throw new IllegalEpisodeTransitionException(status, "go-live");
    }
    this.status = EpisodeStatus.published;
    this.publishedAt = now;
    this.scheduledAt = null;
  }

  // ---- Metadata mutation (PATCH) ----

  public void renameShow(ShowId showId) {
    this.showId = showId;
  }

  public void updateTitle(String title) {
    if (title != null && !title.isBlank()) {
      this.title = title.trim();
    }
  }

  public void updateDescription(String description) {
    if (description != null) {
      this.description = description;
    }
  }

  public void updateCoverUrl(String coverUrl) {
    if (coverUrl != null) {
      this.coverUrl = coverUrl;
    }
  }

  public void updatePremiumPrice(boolean premium, long priceMinor, Currency currency) {
    guardPremiumPrice(premium, priceMinor);
    this.premium = premium;
    this.priceMinor = priceMinor;
    this.currency = currency;
  }

  public void updateEarlyAccess(boolean earlyAccess) {
    this.earlyAccess = earlyAccess;
  }

  public void incrementPlays() {
    this.plays = this.plays + 1;
  }

  private static void guardPremiumPrice(boolean premium, long priceMinor) {
    if (premium && priceMinor <= 0) {
      throw new InvalidPriceException("Premium episodes require a price greater than 0");
    }
  }

  // ---- Accessors ----

  public EpisodeId id() {
    return id;
  }

  public ShowId showId() {
    return showId;
  }

  public ArtistId artistId() {
    return artistId;
  }

  public String title() {
    return title;
  }

  public String description() {
    return description;
  }

  public String audioKey() {
    return audioKey;
  }

  public String coverUrl() {
    return coverUrl;
  }

  public int durationSec() {
    return durationSec;
  }

  public EpisodeStatus status() {
    return status;
  }

  public boolean premium() {
    return premium;
  }

  public long priceMinor() {
    return priceMinor;
  }

  public Currency currency() {
    return currency;
  }

  public boolean earlyAccess() {
    return earlyAccess;
  }

  public Instant scheduledAt() {
    return scheduledAt;
  }

  public Instant publishedAt() {
    return publishedAt;
  }

  public long plays() {
    return plays;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }

  public String requestHash() {
    return requestHash;
  }
}
