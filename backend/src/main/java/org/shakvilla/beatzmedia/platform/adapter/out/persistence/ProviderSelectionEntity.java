package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "provider_selection")
public class ProviderSelectionEntity {
  @Id
  @Column(name = "capability", nullable = false, length = 32)
  String capability;

  @Column(name = "provider_code", nullable = false, length = 64)
  String providerCode;

  @Column(name = "version", nullable = false)
  long version;

  @Column(name = "updated_at", nullable = false)
  Instant updatedAt;

  @Column(name = "updated_by", nullable = false, length = 128)
  String updatedBy;
}
