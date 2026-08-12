package org.shakvilla.beatzmedia.payments.fakes;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.shakvilla.beatzmedia.payments.application.port.out.PaymentProviderPolicy;
import org.shakvilla.beatzmedia.payments.domain.Provider;

/**
 * In-memory {@link PaymentProviderPolicy}. Every rail enabled by default, so tests that are not
 * about enablement read as they did before GAP-13.
 */
public class FakePaymentProviderPolicy implements PaymentProviderPolicy {

  private final Set<Provider> enabled = EnumSet.allOf(Provider.class);

  @Override
  public boolean isEnabledForCharges(Provider provider) {
    return provider != null && enabled.contains(provider);
  }

  @Override
  public List<Provider> enabledForCharges() {
    return Arrays.stream(Provider.values()).filter(enabled::contains).toList();
  }

  /** Test helper: switch a rail off. */
  public void disable(Provider provider) {
    enabled.remove(provider);
  }

  /** Test helper: switch a rail back on. */
  public void enable(Provider provider) {
    enabled.add(provider);
  }
}
