package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "provider_runtime_config")
public class ProviderRuntimeConfigEntity {
  @Id
  @Column(name = "config_key", nullable = false, length = 96)
  String configKey;

  @Column(name = "encrypted_values", nullable = false, columnDefinition = "TEXT")
  String encryptedValues;

  @Column(name = "version", nullable = false)
  long version;

  @Column(name = "updated_at", nullable = false)
  Instant updatedAt;

  @Column(name = "updated_by", nullable = false, length = 128)
  String updatedBy;
}
