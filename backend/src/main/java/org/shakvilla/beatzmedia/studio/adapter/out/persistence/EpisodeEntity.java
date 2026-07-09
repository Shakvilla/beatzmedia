package org.shakvilla.beatzmedia.studio.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code studio_episode} table. Domain types carry no ORM annotations. Id
 * columns are {@code TEXT} (not native {@code UUID}) — same convention as {@link
 * PodcastShowEntity}. {@code show_id} has no JPA {@code @ManyToOne}; it is a plain FK column,
 * resolved to a domain {@code ShowId} by the mapper (no lazy-loading surprises across the aggregate
 * boundary). Studio ADD §5.2 / §7 (WU-STU-2).
 */
@Entity
@Table(name = "studio_episode")
public class EpisodeEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "show_id", nullable = false)
  public String showId;

  @Column(name = "artist_id", nullable = false)
  public String artistId;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "description")
  public String description;

  @Column(name = "audio_key")
  public String audioKey;

  @Column(name = "cover_url")
  public String coverUrl;

  @Column(name = "duration_sec", nullable = false)
  public int durationSec;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "is_premium", nullable = false)
  public boolean premium;

  @Column(name = "price_minor", nullable = false)
  public long priceMinor;

  @Column(name = "currency", nullable = false)
  public String currency;

  @Column(name = "is_early_access", nullable = false)
  public boolean earlyAccess;

  @Column(name = "scheduled_at")
  public Instant scheduledAt;

  @Column(name = "published_at")
  public Instant publishedAt;

  @Column(name = "plays", nullable = false)
  public long plays;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "idempotency_key")
  public String idempotencyKey;

  @Column(name = "request_hash")
  public String requestHash;
}
