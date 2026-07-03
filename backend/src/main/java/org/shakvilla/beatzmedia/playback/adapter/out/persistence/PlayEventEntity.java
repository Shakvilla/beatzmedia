package org.shakvilla.beatzmedia.playback.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code play_event} table. Domain types carry no ORM annotations. Playback ADD
 * §7 / V401 migration.
 */
@Entity
@Table(name = "play_event")
public class PlayEventEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  /** Nullable — anonymous plays. Opaque ref; no FK. */
  @Column(name = "account_id")
  public String accountId;

  /** Opaque ref to catalog track; no FK. */
  @Column(name = "track_id", nullable = false)
  public String trackId;

  @Column(name = "at", nullable = false)
  public Instant at;

  /** "full" | "preview" */
  @Column(name = "full_vs_preview", nullable = false)
  public String fullVsPreview;

  /** "player" | "preview" | "autoplay" */
  @Column(name = "source", nullable = false)
  public String source;
}
