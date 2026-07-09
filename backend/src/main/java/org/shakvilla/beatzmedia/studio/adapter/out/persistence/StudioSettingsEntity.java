package org.shakvilla.beatzmedia.studio.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code studio_settings} table. Domain types carry no ORM annotations. {@code
 * artist_id} is TEXT (not native UUID), same convention as {@code StudioProfileEntity} (see V958
 * migration note). Studio ADD §5.2 / §7 / §16.
 */
@Entity
@Table(name = "studio_settings")
public class StudioSettingsEntity {

  @Id
  @Column(name = "artist_id", nullable = false)
  public String artistId;

  /** JSON object {@code {sales,tips,followers,payouts,weeklySummary,comments,marketing}}. */
  @Column(name = "notifications", columnDefinition = "jsonb", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  public String notificationsJson;

  /** JSON object {@code {trackPrice,releaseVisibility,autoExplicit,allowOffers}}. */
  @Column(name = "defaults", columnDefinition = "jsonb", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  public String defaultsJson;

  /** JSON object {@code {autoWithdraw,autoWithdrawThreshold,taxId}}. */
  @Column(name = "payouts", columnDefinition = "jsonb", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  public String payoutsJson;

  /** JSON object {@code {discoverable,showRealName,acceptBookings,allowDms}}. */
  @Column(name = "privacy", columnDefinition = "jsonb", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  public String privacyJson;

  /** JSON array of {@code {id,name,email,role}}. */
  @Column(name = "team", columnDefinition = "jsonb", nullable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  public String teamJson;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
