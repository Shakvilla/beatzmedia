package org.shakvilla.beatzmedia.events.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A live event (aggregate root), owning its {@link TicketTier} list. Framework-free. {@code
 * status} is ALWAYS derived from the tiers' live availability — never a stored display string
 * (INV-EVT-2). Events ADD §3.
 */
public final class Event {

  /**
   * Total remaining seats (summed across all tiers) at or below which the event is considered
   * low-stock ("selling-fast"), while at least one seat remains. A documented, testable constant
   * standing in for the ADD's "low-stock threshold" (§3, INV-EVT-2) — not a {@code
   * PlatformSettings} value, since it drives a purely cosmetic read-model label, not money math.
   */
  static final int LOW_STOCK_THRESHOLD = 50;

  private final EventId id;
  private final String title;
  private final String artistName;
  private final String artistId;
  private final List<String> lineup;
  private final String image;
  private final Instant eventAt;
  private final String doorsTime;
  private final String venue;
  private final String city;
  private final String region;
  private final EventCategory category;
  private final String description;
  private final String ageRestriction;
  private final int popularity;
  private final List<TicketTier> tiers;

  public Event(
      EventId id,
      String title,
      String artistName,
      String artistId,
      List<String> lineup,
      String image,
      Instant eventAt,
      String doorsTime,
      String venue,
      String city,
      String region,
      EventCategory category,
      String description,
      String ageRestriction,
      int popularity,
      List<TicketTier> tiers) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    if (artistName == null || artistName.isBlank()) {
      throw new IllegalArgumentException("artistName must not be blank");
    }
    if (image == null || image.isBlank()) {
      throw new IllegalArgumentException("image must not be blank");
    }
    if (venue == null || venue.isBlank()) {
      throw new IllegalArgumentException("venue must not be blank");
    }
    if (city == null || city.isBlank()) {
      throw new IllegalArgumentException("city must not be blank");
    }
    if (category == null) {
      throw new IllegalArgumentException("category must not be null");
    }
    this.id = id;
    this.title = title;
    this.artistName = artistName;
    this.artistId = artistId;
    this.lineup = lineup == null ? List.of() : List.copyOf(lineup);
    this.image = image;
    this.eventAt = eventAt;
    this.doorsTime = doorsTime;
    this.venue = venue;
    this.city = city;
    this.region = region;
    this.category = category;
    this.description = description;
    this.ageRestriction = ageRestriction;
    this.popularity = popularity;
    this.tiers = tiers == null ? List.of() : List.copyOf(tiers);
  }

  public EventId id() {
    return id;
  }

  public String title() {
    return title;
  }

  public String artistName() {
    return artistName;
  }

  public Optional<String> artistId() {
    return Optional.ofNullable(artistId);
  }

  public List<String> lineup() {
    return lineup;
  }

  public String image() {
    return image;
  }

  public Instant eventAt() {
    return eventAt;
  }

  public Optional<String> doorsTime() {
    return Optional.ofNullable(doorsTime);
  }

  public String venue() {
    return venue;
  }

  public String city() {
    return city;
  }

  public Optional<String> region() {
    return Optional.ofNullable(region);
  }

  public EventCategory category() {
    return category;
  }

  public Optional<String> description() {
    return Optional.ofNullable(description);
  }

  public Optional<String> ageRestriction() {
    return Optional.ofNullable(ageRestriction);
  }

  public int popularity() {
    return popularity;
  }

  public List<TicketTier> tiers() {
    return tiers;
  }

  /**
   * INV-EVT-2: derive the event's live availability status from its tiers.
   *
   * <ul>
   *   <li>{@code sold-out} — every tier has {@code sold >= capacity} (or the event has no tiers).
   *   <li>{@code selling-fast} — total remaining seats across all tiers is at or below {@link
   *       #LOW_STOCK_THRESHOLD}, but at least one seat remains.
   *   <li>{@code on-sale} — otherwise.
   * </ul>
   */
  public EventStatus status() {
    if (tiers.isEmpty()) {
      return EventStatus.SOLD_OUT;
    }
    boolean allSoldOut = tiers.stream().allMatch(TicketTier::isSoldOut);
    if (allSoldOut) {
      return EventStatus.SOLD_OUT;
    }
    int totalRemaining = tiers.stream().mapToInt(TicketTier::remaining).sum();
    if (totalRemaining <= LOW_STOCK_THRESHOLD) {
      return EventStatus.SELLING_FAST;
    }
    return EventStatus.ON_SALE;
  }
}
