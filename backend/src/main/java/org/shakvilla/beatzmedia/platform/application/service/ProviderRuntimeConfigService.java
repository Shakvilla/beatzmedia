package org.shakvilla.beatzmedia.platform.application.service;

import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.platform.application.port.in.ProviderRuntimeConfigAccess;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.ProviderRuntimeConfigRepository;
import org.shakvilla.beatzmedia.platform.domain.ProviderCapability;
import org.shakvilla.beatzmedia.platform.domain.ProviderCode;
import org.shakvilla.beatzmedia.platform.domain.ProviderRuntimeConfig;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

@ApplicationScoped
public class ProviderRuntimeConfigService implements ProviderRuntimeConfigAccess {
  private final ProviderRuntimeConfigRepository repository;
  private final Clock clock;

  @Inject
  public ProviderRuntimeConfigService(ProviderRuntimeConfigRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Optional<ProviderRuntimeConfig> get(ProviderCapability capability, ProviderCode provider) {
    validatePair(capability, provider);
    return repository.find(capability, provider);
  }

  @Override
  @Transactional
  public Optional<ProviderRuntimeConfig> save(
      ProviderCapability capability,
      ProviderCode provider,
      Map<String, String> values,
      long expectedVersion,
      String updatedBy) {
    validatePair(capability, provider);
    if (expectedVersion < 0) {
      throw new ValidationException("expectedVersion must not be negative", "expectedVersion");
    }
    if (updatedBy == null || updatedBy.isBlank()) {
      throw new ValidationException("updatedBy is required", "updatedBy");
    }
    if (!repository.encryptionAvailable()) {
      throw new ValidationException(
          "Provider configuration encryption is not configured", "configuration");
    }
    return repository.saveIfVersion(
        capability, provider, Map.copyOf(values), expectedVersion, clock.now(), updatedBy);
  }

  @Override
  public boolean encryptionAvailable() {
    return repository.encryptionAvailable();
  }

  private static void validatePair(ProviderCapability capability, ProviderCode provider) {
    if (capability == null || provider == null || provider.capability() != capability) {
      throw new ValidationException("Provider does not support this capability", "provider");
    }
  }
}
