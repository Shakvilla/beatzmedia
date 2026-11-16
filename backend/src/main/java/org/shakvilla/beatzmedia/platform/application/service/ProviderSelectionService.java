package org.shakvilla.beatzmedia.platform.application.service;

import java.util.Comparator;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.platform.application.port.in.ManageProviderSelections;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.ProviderReadinessProbe;
import org.shakvilla.beatzmedia.platform.application.port.out.ProviderSelectionRepository;
import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ProviderCapability;
import org.shakvilla.beatzmedia.platform.domain.ProviderCode;
import org.shakvilla.beatzmedia.platform.domain.ProviderSelection;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/** Platform use case for fail-closed, optimistic provider selection. */
@ApplicationScoped
public class ProviderSelectionService implements ManageProviderSelections {
  private final ProviderSelectionRepository repository;
  private final ProviderReadinessProbe readinessProbe;
  private final Clock clock;

  @Inject
  public ProviderSelectionService(
      ProviderSelectionRepository repository, ProviderReadinessProbe readinessProbe, Clock clock) {
    this.repository = repository;
    this.readinessProbe = readinessProbe;
    this.clock = clock;
  }

  @Override
  @Transactional
  public List<ProviderSelectionView> list() {
    return repository.listAll().stream()
        .sorted(Comparator.comparing(selection -> selection.capability().wireValue()))
        .map(this::toView)
        .toList();
  }

  @Override
  @Transactional
  public ProviderSelectionView change(
      String actorId, String capabilityValue, String providerValue, long expectedVersion) {
    if (actorId == null || actorId.isBlank()) {
      throw new ValidationException("actorId is required", "actorId");
    }
    if (expectedVersion < 1) {
      throw new ValidationException("expectedVersion must be positive", "expectedVersion");
    }
    ProviderCapability capability = ProviderCapability.fromWireValue(capabilityValue);
    ProviderCode provider = ProviderCode.fromWireValue(capability, providerValue);
    ProviderReadinessProbe.Readiness readiness = readinessProbe.check(capability, provider);
    if (!readiness.ready()) {
      throw new ValidationException(
          "Provider is not ready: " + readiness.reason(), "provider");
    }
    ProviderSelection saved =
        repository
            .updateIfVersion(capability, provider, expectedVersion, clock.now(), actorId)
            .orElseThrow(
                () -> new ConflictException("Provider selection changed; reload and retry"));
    return toView(saved);
  }

  private ProviderSelectionView toView(ProviderSelection selection) {
    ProviderReadinessProbe.Readiness readiness =
        readinessProbe.check(selection.capability(), selection.provider());
    return new ProviderSelectionView(
        selection.capability().wireValue(),
        selection.provider().wireValue(),
        selection.version(),
        readiness.ready(),
        readiness.reason(),
        selection.updatedAt(),
        selection.updatedBy());
  }
}
