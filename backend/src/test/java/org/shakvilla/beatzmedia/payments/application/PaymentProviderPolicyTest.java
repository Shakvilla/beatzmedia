package org.shakvilla.beatzmedia.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.adapter.out.config.FeatureFlagPaymentProviderPolicy;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;
import org.shakvilla.beatzmedia.platform.fakes.FakeFeatureFlags;

/**
 * GAP-13 — the per-rail policy, and specifically that it is <strong>fail-closed</strong>.
 *
 * <p>That property is the entire reason this port exists rather than calling
 * {@code FeatureFlags.isEnabled} directly: that method answers {@code true} for a key with no row
 * ("fail-open for non-security features"), which is right for {@code PODCASTS} and catastrophic for
 * a payment rail — a provider whose flag never got seeded would keep taking money while the console
 * showed it as off.
 */
@Tag("unit")
class PaymentProviderPolicyTest {

  private FakeFeatureFlags flags;
  private FeatureFlagPaymentProviderPolicy policy;

  @BeforeEach
  void setUp() {
    flags = new FakeFeatureFlags();
    policy = new FeatureFlagPaymentProviderPolicy(flags);
  }

  @Test
  void anEnabledRailAcceptsCharges() {
    assertTrue(policy.isEnabledForCharges(Provider.mtn));
  }

  @Test
  void aDisabledRailRefusesCharges() {
    flags.disable(FeatureKey.PROVIDER_MTN);

    assertFalse(policy.isEnabledForCharges(Provider.mtn));
    assertTrue(policy.isEnabledForCharges(Provider.telecel), "only the named rail is affected");
  }

  /**
   * The case the port exists for. A key with <em>no row</em> — a migration that did not run, a row
   * deleted by hand — must read as disabled. Asserting this against {@code isEnabled} would pass
   * while the opposite was true.
   */
  @Test
  void aRailWithNoFlagRowAtAllRefusesCharges() {
    flags.remove(FeatureKey.PROVIDER_CARD);

    assertFalse(
        policy.isEnabledForCharges(Provider.card),
        "a missing row must not read as enabled — that is money moving on an unconfirmed rail");
  }

  /** Contrast, stated explicitly so the difference is not mistaken for an accident. */
  @Test
  void theUnderlyingFlagStoreStillFailsOpenForProductFlags() {
    flags.remove(FeatureKey.PODCASTS);

    assertTrue(
        flags.isEnabled(FeatureKey.PODCASTS),
        "product flags keep their fail-open default; only the payment rails differ");
  }

  @Test
  void aNullProviderRefusesRatherThanThrowing() {
    assertFalse(policy.isEnabledForCharges(null));
  }

  @Test
  void enabledForChargesListsOnlyLiveRails() {
    flags.disable(FeatureKey.PROVIDER_BANK);
    flags.remove(FeatureKey.PROVIDER_AIRTELTIGO);

    List<Provider> enabled = policy.enabledForCharges();

    assertEquals(List.of(Provider.mtn, Provider.telecel, Provider.card), enabled);
  }

  /**
   * A flag store that throws is not evidence the rail is open. Refusing is recoverable — the fan
   * retries or picks another method; charging on a rail nobody could confirm is not.
   */
  @Test
  void aFlagStoreThatThrowsRefusesCharges() {
    FeatureFlagPaymentProviderPolicy throwing =
        new FeatureFlagPaymentProviderPolicy(new FakeFeatureFlags() {
          @Override
          public boolean isEnabledOrDefault(FeatureKey key, boolean whenAbsent) {
            throw new IllegalStateException("flag store unavailable");
          }
        });

    assertFalse(throwing.isEnabledForCharges(Provider.mtn));
  }
}
