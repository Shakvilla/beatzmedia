package org.shakvilla.beatzmedia.platform.application.port.in;

import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.platform.domain.ProviderCapability;
import org.shakvilla.beatzmedia.platform.domain.ProviderCode;
import org.shakvilla.beatzmedia.platform.domain.ProviderRuntimeConfig;

/** Server-side access to encrypted provider configuration; never expose returned values to clients. */
public interface ProviderRuntimeConfigAccess {
  Optional<ProviderRuntimeConfig> get(ProviderCapability capability, ProviderCode provider);

  Optional<ProviderRuntimeConfig> save(
      ProviderCapability capability,
      ProviderCode provider,
      Map<String, String> values,
      long expectedVersion,
      String updatedBy);

  boolean encryptionAvailable();
}
