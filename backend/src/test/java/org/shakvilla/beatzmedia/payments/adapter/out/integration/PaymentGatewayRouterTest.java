package org.shakvilla.beatzmedia.payments.adapter.out.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.fakes.FakeFeatureFlags;

/** Unit tests for {@link PaymentGatewayRouter}: dispatch by {@link FeatureKey#PSP_REDDE}. */
@Tag("unit")
class PaymentGatewayRouterTest {

  private final MarkerGateway sandbox = new MarkerGateway("SANDBOX");
  private final MarkerGateway redde = new MarkerGateway("REDDE");
  private final FakeFeatureFlags flags = new FakeFeatureFlags();
  private final PaymentGatewayRouter router = new PaymentGatewayRouter(sandbox, redde, flags);

  @Test
  void routesToSandboxWhenFlagOff() {
    flags.disable(FeatureKey.PSP_REDDE);
    assertEquals("SANDBOX", router.queryStatus(Provider.mtn, "ref").reason());
    assertTrue(router.verifySignature(Provider.mtn, "s", new byte[0])); // sandbox marker returns true
  }

  @Test
  void routesToReddeWhenFlagOn() {
    flags.enable(FeatureKey.PSP_REDDE);
    assertEquals("REDDE", router.queryStatus(Provider.mtn, "ref").reason());
    assertFalse(router.verifySignature(Provider.mtn, "s", new byte[0])); // redde marker returns false
  }

  @Test
  void supportsDirectChargeDelegatesToActive() {
    flags.enable(FeatureKey.PSP_REDDE);
    assertFalse(router.supportsDirectCharge(Provider.mtn)); // redde marker: false
    flags.disable(FeatureKey.PSP_REDDE);
    assertTrue(router.supportsDirectCharge(Provider.mtn)); // sandbox marker: true
  }

  /** Records which gateway handled a call by tagging its return values with its name. */
  private static final class MarkerGateway implements PaymentGateway {
    private final String name;

    MarkerGateway(String name) {
      this.name = name;
    }

    @Override
    public ChargeHandle initiate(
        Provider provider, OrderRef ref, Money amount, PaymentMethodRef method) {
      return new ChargeHandle(name);
    }

    @Override
    public boolean verifySignature(Provider provider, String signature, byte[] rawBody) {
      return name.equals("SANDBOX");
    }

    @Override
    public ProviderStatus queryStatus(Provider provider, String providerRef) {
      // Smuggle the marker name out through the reason field for the assertion.
      return ProviderStatus.failed(name);
    }

    @Override
    public boolean supportsDirectCharge(Provider provider) {
      return name.equals("SANDBOX");
    }
  }
}
