package org.shakvilla.beatzmedia.payments.adapter.out.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;
import org.shakvilla.beatzmedia.platform.fakes.FakeFeatureFlags;

/**
 * The boot guard that refuses to start when a payment rail has no flag row (GAP-13).
 *
 * <p><strong>Why this is worth testing.</strong> {@code PaymentProviderPolicy} reads these flags
 * fail-closed, which is only safe because this check exists: without it, a migration that did not
 * apply would leave a rail silently declining every charge, indistinguishable from normal operation
 * until a customer complains. The guard is the entire counterweight, and it previously had no test
 * at all — so nothing would have noticed if it stopped firing.
 *
 * <p>Lives in the adapter's own package so it can call the package-private {@code verify()} and
 * {@code keyFor()} without widening either for testing's sake — the same placement
 * {@code StorePriceSourceTest} and the media adapter tests already use.
 */
@Tag("unit")
class PaymentProviderFlagsCheckTest {

  private final FakeFeatureFlags flags = new FakeFeatureFlags();
  private final PaymentProviderFlagsCheck check = new PaymentProviderFlagsCheck(flags);

  @Test
  void startsWhenEveryRailHasARow() {
    assertDoesNotThrow(check::verify);
  }

  @Test
  void refusesToStartWhenARailHasNoRow() {
    flags.remove(FeatureKey.PROVIDER_MTN);

    IllegalStateException e = assertThrows(IllegalStateException.class, check::verify);

    assertTrue(e.getMessage().contains("PROVIDER_MTN"), "the message must name the missing rail");
    assertTrue(
        e.getMessage().contains("V978"),
        "the message must point at the migration, since that is what an operator has to check");
  }

  @Test
  void namesEveryMissingRailNotJustTheFirst() {
    flags.remove(FeatureKey.PROVIDER_MTN);
    flags.remove(FeatureKey.PROVIDER_CARD);

    String message = assertThrows(IllegalStateException.class, check::verify).getMessage();

    assertTrue(message.contains("PROVIDER_MTN"), message);
    assertTrue(message.contains("PROVIDER_CARD"), message);
  }

  /**
   * A rail an operator has deliberately switched off is a normal state. Blocking a deploy on it
   * would mean the kill switch cannot survive a restart — which would make the feature useless.
   */
  @Test
  void aDeliberatelyDisabledRailDoesNotBlockStartup() {
    for (Provider p : Provider.values()) {
      flags.disable(FeatureFlagPaymentProviderPolicy.keyFor(p));
    }

    assertDoesNotThrow(check::verify);
  }

  /**
   * The guard must cover whatever {@link Provider} currently holds, not a list written down once.
   * Adding a rail without seeding its flag is precisely the mistake this exists to catch, so the
   * check is driven off the enum and this test removes each key in turn to prove none is skipped.
   */
  @Test
  void everyRailInTheEnumIsChecked() {
    for (Provider p : Provider.values()) {
      FakeFeatureFlags oneMissing = new FakeFeatureFlags();
      oneMissing.remove(FeatureFlagPaymentProviderPolicy.keyFor(p));

      IllegalStateException e = assertThrows(
          IllegalStateException.class,
          new PaymentProviderFlagsCheck(oneMissing)::verify,
          "a missing row for " + p + " must stop the boot");
      assertTrue(e.getMessage().contains(FeatureFlagPaymentProviderPolicy.keyFor(p).name()));
    }
  }
}
