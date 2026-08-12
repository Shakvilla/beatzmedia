package org.shakvilla.beatzmedia.payments.adapter.out.config;

import java.util.Arrays;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.application.port.out.FeatureFlags;

import io.quarkus.runtime.StartupEvent;

/**
 * Refuses to start the application when a payment rail has no flag row (GAP-13).
 *
 * <p><strong>Why a boot failure rather than a log line.</strong> {@link PaymentProviderPolicy} reads
 * these flags fail-closed, so a row that never got seeded means that rail silently declines every
 * charge. Fail-open would have been worse — it would keep charging on a rail an operator believed
 * was off — but neither is discoverable: both look like normal operation from the inside, and the
 * first hint is a customer who cannot pay.
 *
 * <p>Stopping the boot converts an invisible, ongoing failure into a loud one at deploy time, when
 * someone is watching and a rollback is one command away. That trade — refuse to start rather than
 * run wrong — is the whole reason the fail-closed read is safe to have.
 *
 * <p>It checks for the row's <em>existence</em>, not its value. A rail deliberately switched off is
 * a normal state and must not block a deploy.
 */
@ApplicationScoped
public class PaymentProviderFlagsCheck {

  private static final Logger LOG = Logger.getLogger(PaymentProviderFlagsCheck.class);

  private final FeatureFlags flags;

  @Inject
  public PaymentProviderFlagsCheck(FeatureFlags flags) {
    this.flags = flags;
  }

  void onStart(@Observes StartupEvent event) {
    verify();
  }

  /**
   * Package-private so the guard itself can be tested. An {@code @Observes} method is only reachable
   * through a container start, which makes "does this actually fire, and does it fail on a missing
   * row?" impossible to assert directly — and this is the one check standing between a migration
   * that did not apply and every charge on that rail being silently declined.
   */
  void verify() {
    List<Provider> missing =
        Arrays.stream(Provider.values())
            .filter(p -> !flags.exists(FeatureFlagPaymentProviderPolicy.keyFor(p)))
            .toList();

    if (!missing.isEmpty()) {
      String keys = missing.stream().map(p -> FeatureFlagPaymentProviderPolicy.keyFor(p).name())
          .reduce((a, b) -> a + ", " + b).orElse("");
      throw new IllegalStateException(
          "Payment provider flags are missing: " + keys
              + ". These are read fail-closed, so every charge on those rails would be declined "
              + "silently. Check that V978__seed_payment_provider_flags.sql applied.");
    }
    LOG.infof("payments: %d provider flags present", Provider.values().length);
  }
}
