package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

import io.quarkus.narayana.jta.QuarkusTransaction;

/**
 * JPA-backed implementation of {@link FeatureFlags}. Caches all flags in a {@link
 * ConcurrentHashMap} with a 30-second TTL; invalidated on any write. ADD §5.2.
 */
@ApplicationScoped
public class FeatureFlagsAdapter implements FeatureFlags {

  private static final long CACHE_TTL_MS = 30_000L;

  @Inject
  FeatureFlagRepository repository;

  private final Map<String, Boolean> flagCache = new ConcurrentHashMap<>();
  private final AtomicLong cacheExpiresAt = new AtomicLong(0);

  @Override
  public boolean isEnabled(FeatureKey key) {
    ensureCacheLoaded();
    // Default to true for unknown flags (fail-open for non-security features).
    return flagCache.getOrDefault(key.name(), true);
  }

  @Override
  @Transactional
  public void set(FeatureKey key, boolean enabled) {
    FeatureFlagEntity entity = repository.findById(key.name());
    if (entity == null) {
      entity = new FeatureFlagEntity(key.name(), enabled, Instant.now());
      repository.persist(entity);
    } else {
      entity.setEnabled(enabled);
      entity.setUpdatedAt(Instant.now());
    }
    // Invalidate cache
    flagCache.clear();
    cacheExpiresAt.set(0);
  }

  private void ensureCacheLoaded() {
    if (System.currentTimeMillis() < cacheExpiresAt.get()) {
      return;
    }
    // Reload in a fresh transaction so the read sees the latest committed state (a new
    // persistence context — never stale first-level-cache entities from an earlier session).
    Map<String, Boolean> fresh = new ConcurrentHashMap<>();
    QuarkusTransaction.requiringNew()
        .run(() -> repository.listAll().forEach(e -> fresh.put(e.getKey(), e.isEnabled())));
    flagCache.clear();
    flagCache.putAll(fresh);
    cacheExpiresAt.set(System.currentTimeMillis() + CACHE_TTL_MS);
  }
}
