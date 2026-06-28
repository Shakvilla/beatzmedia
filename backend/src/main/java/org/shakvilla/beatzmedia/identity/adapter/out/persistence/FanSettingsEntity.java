package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code fan_settings} table. Domain types carry no ORM annotations; this
 * adapter class is the only place Hibernate annotations appear. Identity ADD §5.2 / §7.
 */
@Entity
@Table(name = "fan_settings")
public class FanSettingsEntity {

  @Id
  @Column(name = "account_id", nullable = false)
  public String accountId;

  @Column(name = "theme", nullable = false)
  public String theme;

  @Column(name = "audio_quality", nullable = false)
  public String audioQuality;

  @Column(name = "streaming_quality", nullable = false)
  public String streamingQuality;

  @Column(name = "download_quality", nullable = false)
  public String downloadQuality;

  @Column(name = "crossfade", nullable = false)
  public String crossfade;

  @Column(name = "data_saver", nullable = false)
  public boolean dataSaver;

  /**
   * Stored as a JSONB column. The {@link JdbcTypeCode} annotation instructs Hibernate to send the
   * string value with the correct JDBC type for PostgreSQL's jsonb cast. Format:
   * {"newReleases":bool,"playlistUpdates":bool,"dropsOffers":bool}
   */
  @Column(name = "notif_json", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  public String notifJson;

  @Column(name = "country", nullable = false)
  public String country;

  @Column(name = "phone")
  public String phone;
}
