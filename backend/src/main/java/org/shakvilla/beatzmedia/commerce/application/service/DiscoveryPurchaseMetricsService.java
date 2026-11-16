package org.shakvilla.beatzmedia.commerce.application.service;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.commerce.application.port.in.GetDiscoveryPurchaseMetrics;
import org.shakvilla.beatzmedia.commerce.application.port.out.DiscoveryPurchaseMetricsRepository;

@ApplicationScoped
public class DiscoveryPurchaseMetricsService implements GetDiscoveryPurchaseMetrics {

  private final DiscoveryPurchaseMetricsRepository repository;

  @Inject
  public DiscoveryPurchaseMetricsService(DiscoveryPurchaseMetricsRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public long count(
      String contentKind, String contentId, List<String> memberIds, Instant since, Instant asOf) {
    if (!("single".equals(contentKind) || "album".equals(contentKind)
        || "ep".equals(contentKind) || "podcast".equals(contentKind))
        || contentId == null || contentId.isBlank()
        || memberIds == null || memberIds.size() > 500
        || memberIds.stream().anyMatch(id -> id == null || id.isBlank())
        || since == null || asOf == null || !since.isBefore(asOf)) {
      throw new IllegalArgumentException("Invalid discovery purchase target or window");
    }
    List<String> members = "single".equals(contentKind)
        ? List.of() : memberIds.stream().distinct().toList();
    return repository.count(contentKind, contentId, members, since, asOf);
  }
}
