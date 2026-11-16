package org.shakvilla.beatzmedia.engagement.application.service;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.engagement.application.port.in.GetDiscoveryEngagementMetrics;
import org.shakvilla.beatzmedia.engagement.application.port.out.EngagementRepository;

@ApplicationScoped
public class DiscoveryEngagementMetricsService implements GetDiscoveryEngagementMetrics {

  private final EngagementRepository repository;

  @Inject
  public DiscoveryEngagementMetricsService(EngagementRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public List<ContentMetrics> get(List<ContentRef> content, Instant asOf) {
    if (content == null || content.isEmpty()) return List.of();
    if (content.size() > 100) throw new IllegalArgumentException("At most 100 content refs are allowed");
    if (asOf == null) throw new IllegalArgumentException("asOf is required");
    for (ContentRef ref : content) {
      if (ref == null || ref.id() == null || ref.id().isBlank()
          || !("track".equals(ref.kind()) || "podcast-episode".equals(ref.kind()))) {
        throw new IllegalArgumentException("Invalid engagement content ref");
      }
    }
    return repository.discoveryMetrics(content, asOf);
  }

  @Override
  @Transactional
  public List<ContentRef> candidates(Instant asOf, int perWindowLimit) {
    if (asOf == null || perWindowLimit < 1 || perWindowLimit > 50) {
      throw new IllegalArgumentException("Candidate window limit must be 1..50");
    }
    return repository.discoveryCandidates(asOf, perWindowLimit);
  }

  @Override
  @Transactional
  public GroupMetrics group(List<ContentRef> members, Instant asOf) {
    if (members == null || members.isEmpty() || members.size() > 100 || asOf == null) {
      throw new IllegalArgumentException("Group members must contain 1..100 content refs");
    }
    for (ContentRef ref : members) {
      if (ref == null || ref.id() == null || ref.id().isBlank()
          || !("track".equals(ref.kind()) || "podcast-episode".equals(ref.kind()))) {
        throw new IllegalArgumentException("Invalid group member");
      }
    }
    return repository.discoveryGroupMetrics(members.stream().distinct().toList(), asOf);
  }
}
