package org.shakvilla.beatzmedia.platform.domain;

import java.time.Instant;
import java.util.Map;

/** Decrypted provider settings used only inside server-side adapter flows. */
public final class ProviderRuntimeConfig {
  private final ProviderCapability capability;
  private final ProviderCode provider;
  private final Map<String, String> values;
  private final long version;
  private final Instant updatedAt;
  private final String updatedBy;

  public ProviderRuntimeConfig(
      ProviderCapability capability,
      ProviderCode provider,
      Map<String, String> values,
      long version,
      Instant updatedAt,
      String updatedBy) {
    this.capability = capability;
    this.provider = provider;
    this.values = Map.copyOf(values);
    this.version = version;
    this.updatedAt = updatedAt;
    this.updatedBy = updatedBy;
  }

  public ProviderCapability capability() { return capability; }
  public ProviderCode provider() { return provider; }
  public Map<String, String> values() { return values; }
  public long version() { return version; }
  public Instant updatedAt() { return updatedAt; }
  public String updatedBy() { return updatedBy; }

  /** Avoid leaking decrypted values through accidental log statements. */
  @Override
  public String toString() {
    return "ProviderRuntimeConfig[capability=" + capability.wireValue()
        + ", provider=" + provider.wireValue() + ", version=" + version + "]";
  }
}
