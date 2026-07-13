package org.shakvilla.beatzmedia.payments.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PayoutDestination} validation (WU-PAY-7). */
@Tag("unit")
class PayoutDestinationTest {

  @Test
  void momo_requires_a_momo_network_and_a_wallet() {
    assertDoesNotThrow(() -> new PayoutDestination.Momo(Provider.mtn, "0244009210"));
    assertThrows(
        IllegalArgumentException.class, () -> new PayoutDestination.Momo(Provider.card, "0244"));
    assertThrows(
        IllegalArgumentException.class, () -> new PayoutDestination.Momo(Provider.bank, "0244"));
    assertThrows(IllegalArgumentException.class, () -> new PayoutDestination.Momo(null, "0244"));
    assertThrows(
        IllegalArgumentException.class, () -> new PayoutDestination.Momo(Provider.mtn, "  "));
  }

  @Test
  void bank_requires_code_name_and_account() {
    assertDoesNotThrow(
        () -> new PayoutDestination.Bank(GhanaBankCode.GCB, "GCB Bank", "Ama Owner", "1234567890"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PayoutDestination.Bank(null, "GCB Bank", "Ama", "123"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PayoutDestination.Bank(GhanaBankCode.GCB, "GCB Bank", "Ama", "  "));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PayoutDestination.Bank(GhanaBankCode.GCB, "GCB Bank", "  ", "123"));
  }
}
