package org.shakvilla.beatzmedia.payments.adapter.out.config;

import java.util.Arrays;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentProviderPolicy;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;

/**
 * Reads the per-rail flags from the platform's {@code feature_flag} store, fail-closed (GAP-13).
 *
 * <p>Direction is payments → platform through platform's existing outbound port, the same way the
 * PSP toggle is read. Reusing that table also inherits the after-commit cache invalidation fixed in
 * GAP-07 — without it, a rail switched off in the console would keep charging for up to the cache
 * TTL, which is exactly the class of bug this feature must not have.
 */
@ApplicationScoped
public class FeatureFlagPaymentProviderPolicy implements PaymentProviderPolicy {

  private static final Logger LOG = Logger.getLogger(FeatureFlagPaymentProviderPolicy.class);

  private final FeatureFlags flags;

  @Inject
  public FeatureFlagPaymentProviderPolicy(FeatureFlags flags) {
    this.flags = flags;
  }

  @Override
  public boolean isEnabledForCharges(Provider provider) {
    if (provider == null) {
      return false;
    }
    try {
      // `false` when absent is what makes this fail-closed. isEnabled() would answer TRUE for a
      // missing row, so a rail whose flag never got seeded would keep charging.
      return flags.isEnabledOrDefault(keyFor(provider), false);
    } catch (RuntimeException e) {
      // A flag store that cannot be read is not evidence that the rail is open. Refusing the charge
      // is recoverable — the fan retries or picks another method. Charging on a rail nobody could
      // confirm was enabled is not.
      LOG.errorf(e, "payments: could not read the provider flag for %s — refusing charges", provider);
      return false;
    }
  }

  @Override
  public List<Provider> enabledForCharges() {
    return Arrays.stream(Provider.values()).filter(this::isEnabledForCharges).toList();
  }

  /**
   * Maps a rail to its flag key.
   *
   * <p>A {@code switch} over the enum rather than {@code valueOf("PROVIDER_" + name)}: string
   * assembly would compile happily against a {@link Provider} added later with no corresponding
   * key, then throw at the first charge on it. This way a new rail is a compile error here — which
   * is where it should surface.
   */
  static FeatureKey keyFor(Provider provider) {
    return switch (provider) {
      case mtn -> FeatureKey.PROVIDER_MTN;
      case telecel -> FeatureKey.PROVIDER_TELECEL;
      case airteltigo -> FeatureKey.PROVIDER_AIRTELTIGO;
      case card -> FeatureKey.PROVIDER_CARD;
      case bank -> FeatureKey.PROVIDER_BANK;
    };
  }
}
