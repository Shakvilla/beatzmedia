package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
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

  @Inject
  TransactionSynchronizationRegistry txRegistry;

  private final Map<String, Boolean> flagCache = new ConcurrentHashMap<>();
  private final AtomicLong cacheExpiresAt = new AtomicLong(0);

  @Override
  public boolean isEnabled(FeatureKey key) {
    // Default to true for unknown flags (fail-open for non-security features).
    return isEnabledOrDefault(key, true);
  }

  @Override
  public boolean isEnabledOrDefault(FeatureKey key, boolean whenAbsent) {
    ensureCacheLoaded();
    // The cache holds exactly the rows that exist, so a miss here means "no row" — which is the
    // distinction payment rails need and isEnabled() cannot express (GAP-13).
    return flagCache.getOrDefault(key.name(), whenAbsent);
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
    invalidateAfterCommit();
  }

  /**
   * Drops the cache once the surrounding transaction commits — never before.
   *
   * <p>This used to clear the cache inline, which poisoned it for a full TTL. {@code
   * ensureCacheLoaded} reloads in a {@code requiringNew} transaction, so a reload triggered between
   * the inline clear and the commit could not see the pending write: it read the OLD rows and cached
   * them for another 30 seconds. The reload did not even need a concurrent request to trigger it —
   * {@code SaveSettingsService} re-reads the flags to build its own response, so a single settings
   * save reliably returned the values it had just replaced, and kept serving them.
   *
   * <p>Invalidating after completion also fixes the rollback case: a failed save previously cleared
   * the cache anyway, forcing a pointless reload of state that had not changed.
   *
   * <p>If there is no active transaction the clear happens immediately — {@code set} is
   * {@code @Transactional}, so in practice there always is, but a direct call must still behave.
   */
  private void invalidateAfterCommit() {
    if (txRegistry == null || txRegistry.getTransactionStatus() != Status.STATUS_ACTIVE) {
      clearCache();
      return;
    }
    txRegistry.registerInterposedSynchronization(new Synchronization() {
      @Override
      public void beforeCompletion() {
        // Nothing: clearing here would reintroduce the very race this fixes.
      }

      @Override
      public void afterCompletion(int status) {
        // Cleared on rollback too: the cache is a plain reload trigger, not a write-behind buffer,
        // so an extra reload after a failed save is harmless and keeps the logic single-branch.
        clearCache();
      }
    });
  }

  private void clearCache() {
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
