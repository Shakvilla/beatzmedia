package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA entity for {@code fan_preferences} (V973). */
@Entity
@Table(name = "fan_preferences")
public class FanPreferencesEntity {

  @Id
  @Column(name = "account_id")
  public String accountId;

  /** Postgres {@code TEXT[]}; mapped the same way {@code StudioProfileEntity.genres} is. */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Array(length = 40)
  @Column(name = "preferred_genres", columnDefinition = "TEXT[]")
  public String[] preferredGenres;

  /** Null until the fan finishes onboarding. */
  @Column(name = "completed_at")
  public Instant completedAt;
}
