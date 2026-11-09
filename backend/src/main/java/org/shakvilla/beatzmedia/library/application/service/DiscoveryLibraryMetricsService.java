package org.shakvilla.beatzmedia.library.application.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.library.application.port.in.GetDiscoveryLibraryMetrics;
import org.shakvilla.beatzmedia.library.application.port.out.DiscoveryLibraryMetricsRepository;

@ApplicationScoped
public class DiscoveryLibraryMetricsService implements GetDiscoveryLibraryMetrics {

  private final DiscoveryLibraryMetricsRepository repository;

  @Inject
  public DiscoveryLibraryMetricsService(DiscoveryLibraryMetricsRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public Signals get(String contentKind, String contentId, String creatorId, Instant since, Instant asOf) {
    if (!("single".equals(contentKind) || "album".equals(contentKind)
        || "ep".equals(contentKind) || "podcast".equals(contentKind))
        || contentId == null || contentId.isBlank()
        || creatorId == null || creatorId.isBlank() || since == null || asOf == null
        || !since.isBefore(asOf)) {
      throw new IllegalArgumentException("Invalid discovery library target");
    }
    return repository.get(contentKind, contentId, creatorId, since, asOf);
  }
}
