package org.shakvilla.beatzmedia.studio.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code studio_profile} table. Domain types carry no ORM annotations. {@code
 * artist_id} is TEXT (not native UUID) matching the codebase-wide convention for primary keys
 * populated by the platform {@code IdGenerator} (UUIDv7-as-string) — see V958 migration note.
 * Studio ADD §5.2 / §7.
 */
@Entity
@Table(name = "studio_profile")
public class StudioProfileEntity {

  @Id
  @Column(name = "artist_id", nullable = false)
  public String artistId;

  @Column(name = "username", nullable = false)
  public String username;

  @Column(name = "display_name", nullable = false)
  public String displayName;

  @Column(name = "hometown")
  public String hometown;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Array(length = 20)
  @Column(name = "genres", columnDefinition = "TEXT[]")
  public String[] genres;

  @Column(name = "bio")
  public String bio;

  @Column(name = "avatar_url")
  public String avatarUrl;

  @Column(name = "banner_url")
  public String bannerUrl;

  /** JSON object {@code {instagram,twitter,youtube,website}} — Studio ADD §3 {@code ProfileLinks}. */
  @Column(name = "links", columnDefinition = "jsonb", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  public String linksJson;

  /** JSON array of {@code {id,venue,date,city}} — Studio ADD §3 {@code ShowAppearance}. */
  @Column(name = "shows", columnDefinition = "jsonb", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  public String showsJson;

  @Column(name = "featured_track_id")
  public String featuredTrackId;

  @Column(name = "booking_email")
  public String bookingEmail;

  /** JSON array of {@code {id,name,url}} — Studio ADD §3 {@code PressAsset}. */
  @Column(name = "press_assets", columnDefinition = "jsonb", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  public String pressAssetsJson;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
