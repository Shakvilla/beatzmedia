package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code feature_flag} table, keyed by {@code FeatureKey} name. ADD §7.
 */
@Entity
@Table(name = "feature_flag")
public class FeatureFlagEntity {

  @Id
  @Column(name = "key")
  private String key;

  @Column(name = "is_enabled", nullable = false)
  private boolean enabled;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // Default constructor required by JPA
  public FeatureFlagEntity() {}

  public FeatureFlagEntity(String key, boolean enabled, Instant updatedAt) {
    this.key = key;
    this.enabled = enabled;
    this.updatedAt = updatedAt;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
