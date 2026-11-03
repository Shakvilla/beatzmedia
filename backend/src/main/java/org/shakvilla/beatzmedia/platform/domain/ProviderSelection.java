package org.shakvilla.beatzmedia.platform.domain;

import java.time.Instant;
import java.util.Objects;

/** Persisted provider selection. Contains operational metadata only, never credentials. */
public record ProviderSelection(
    ProviderCapability capability,
    ProviderCode provider,
    long version,
    Instant updatedAt,
    String updatedBy) {

  public ProviderSelection {
    Objects.requireNonNull(capability, "capability");
    Objects.requireNonNull(provider, "provider");
    Objects.requireNonNull(updatedAt, "updatedAt");
    Objects.requireNonNull(updatedBy, "updatedBy");
    if (provider.capability() != capability) {
      throw new IllegalArgumentException("provider does not support capability");
    }
    if (version < 1) {
      throw new IllegalArgumentException("version must be positive");
    }
  }
}
