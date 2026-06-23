package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.PlatformSettings;

import io.quarkus.narayana.jta.QuarkusTransaction;

/**
 * JPA-backed implementation of {@link PlatformSettingsProvider}. Caches the single settings row
 * with a short TTL (30 s) to avoid per-request DB reads; invalidated on write. ADD §5.2.
 */
@ApplicationScoped
public class PlatformSettingsAdapter implements PlatformSettingsProvider {

  static final short SETTINGS_ID = 1;
  private static final long CACHE_TTL_MS = 30_000L;

  @Inject
  PlatformSettingsRepository repository;

  private final AtomicReference<CachedSettings> cache = new AtomicReference<>();

  @Override
  public PlatformSettings current() {
    CachedSettings cached = cache.get();
    if (cached != null && !cached.isExpired()) {
      return cached.settings();
    }
    // Reload in a fresh transaction so the read sees the latest committed state (a new
    // persistence context — never a stale first-level-cache entity from an earlier session).
    PlatformSettings fresh = QuarkusTransaction.requiringNew().call(this::loadFromDb);
    cache.set(new CachedSettings(fresh, System.currentTimeMillis() + CACHE_TTL_MS));
    return fresh;
  }

  @Override
  @Transactional
  public PlatformSettings save(PlatformSettings updated) {
    PlatformSettingsEntity entity = repository.findById(SETTINGS_ID);
    if (entity == null) {
      entity = new PlatformSettingsEntity();
      entity.setId(SETTINGS_ID);
    }
    mapToEntity(updated, entity);
    repository.persist(entity);
    // Invalidate cache
    cache.set(null);
    return toDomain(entity);
  }

  private PlatformSettings loadFromDb() {
    PlatformSettingsEntity entity = repository.findById(SETTINGS_ID);
    if (entity == null) {
      // DB not seeded yet (e.g. pre-migration); return defaults.
      return PlatformSettings.defaults();
    }
    return toDomain(entity);
  }

  static PlatformSettings toDomain(PlatformSettingsEntity e) {
    Currency currency;
    try {
      currency = Currency.valueOf(e.getDefaultCurrency());
    } catch (IllegalArgumentException ex) {
      currency = Currency.GHS;
    }
    return new PlatformSettings(
        e.getPlatformFeePct(),
        e.getCreatorSharePct(),
        e.getTipFeePct(),
        e.getBundleDiscountPct(),
        e.getPayoutDay(),
        e.getPayoutMinimumMinor(),
        e.getServiceFeeMinor(),
        currency,
        e.isMaintenanceMode());
  }

  static void mapToEntity(PlatformSettings settings, PlatformSettingsEntity entity) {
    entity.setPlatformFeePct(settings.platformFeePct());
    entity.setCreatorSharePct(settings.creatorSharePct());
    entity.setTipFeePct(settings.tipFeePct());
    entity.setBundleDiscountPct(settings.bundleDiscountPct());
    entity.setPayoutDay(settings.payoutDay());
    entity.setPayoutMinimumMinor(settings.payoutMinimumMinor());
    entity.setServiceFeeMinor(settings.serviceFeeMinor());
    entity.setDefaultCurrency(settings.defaultCurrency().name());
    entity.setMaintenanceMode(settings.maintenanceMode());
    entity.setUpdatedAt(Instant.now());
  }

  private record CachedSettings(PlatformSettings settings, long expiresAt) {
    boolean isExpired() {
      return System.currentTimeMillis() > expiresAt;
    }
  }
}
