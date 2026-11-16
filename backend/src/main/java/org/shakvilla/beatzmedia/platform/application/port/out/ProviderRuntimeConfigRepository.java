package org.shakvilla.beatzmedia.platform.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.platform.domain.ProviderCapability;
import org.shakvilla.beatzmedia.platform.domain.ProviderCode;
import org.shakvilla.beatzmedia.platform.domain.ProviderRuntimeConfig;

/** Persistence port for versioned, encrypted provider runtime configuration. */
public interface ProviderRuntimeConfigRepository {
  Optional<ProviderRuntimeConfig> find(ProviderCapability capability, ProviderCode provider);

  Optional<ProviderRuntimeConfig> saveIfVersion(
      ProviderCapability capability,
      ProviderCode provider,
      Map<String, String> values,
      long expectedVersion,
      Instant updatedAt,
      String updatedBy);

  boolean encryptionAvailable();
}
