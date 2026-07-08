package org.shakvilla.beatzmedia.events.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code event} table. Domain types carry no ORM annotations. Events ADD §7 /
 * migration V952.
 */
@Entity
@Table(name = "event")
public class EventEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "artist_name", nullable = false)
  public String artistName;

  @Column(name = "artist_id")
  public String artistId;

  /** JSON array of supporting-act names, e.g. {@code ["Lasmid","Camidoh"]}. */
  @Column(name = "lineup", columnDefinition = "jsonb", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  public String lineupJson;

  @Column(name = "image", nullable = false)
  public String image;

  @Column(name = "event_at", nullable = false)
  public Instant eventAt;

  @Column(name = "doors_time")
  public String doorsTime;

  @Column(name = "venue", nullable = false)
  public String venue;

  @Column(name = "city", nullable = false)
  public String city;

  @Column(name = "region")
  public String region;

  @Column(name = "category", nullable = false)
  public String category;

  @Column(name = "description")
  public String description;

  @Column(name = "age_restriction")
  public String ageRestriction;

  @Column(name = "popularity", nullable = false)
  public int popularity;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
