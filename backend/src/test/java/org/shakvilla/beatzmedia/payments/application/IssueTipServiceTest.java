package org.shakvilla.beatzmedia.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.payments.application.port.in.TipView;
import org.shakvilla.beatzmedia.payments.application.service.IssueTipService;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.ChargeAmountExceededException;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.fakes.FakePaymentGateway;
import org.shakvilla.beatzmedia.payments.fakes.FakePaymentRepository;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.platform.fakes.FakePlatformSettingsProvider;

/**
 * Unit tests for {@link IssueTipService} — the payments-side tip entry (LLFR-PAYMENTS-05 / 02.1).
 *
 * <p>Focus of this suite: the money-amount guards. Positivity was already enforced; these prove the
 * platform charge ceiling ({@code PlatformSettings.maxChargeMinor}) is now enforced on the tip path
 * too (a within-{@code long} but absurd tip → {@code CHARGE_AMOUNT_EXCEEDED} 422), closing the gap the
 * javadoc/{@code IssueTip} contract claimed but the code did not check — so every tip caller (podcasts
 * and the direct payments surface) is protected and no oversized charge is ever sent to the provider.
 */
@Tag("unit")
class IssueTipServiceTest {

  private static final AccountId FAN = new AccountId("fan-1");
  private static final AccountId CREATOR = new AccountId("creator-1");
  private static final IdempotencyKey KEY = new IdempotencyKey("tip-key-1");

  private FakePaymentRepository repository;
  private FakePaymentGateway gateway;
  private FakePlatformSettingsProvider settings;
  private FakeAuditWriter audit;
  private IssueTipService service;

  @BeforeEach
  void setUp() {
    repository = new FakePaymentRepository();
    gateway = new FakePaymentGateway();
    settings = new FakePlatformSettingsProvider();
    audit = new FakeAuditWriter();
    service =
        new IssueTipService(
            repository, gateway, FakeIds.sequential("tip"), FakeClock.fixed(), audit, settings);
  }

  private static PaymentMethodRef momo() {
    return new PaymentMethodRef(Provider.mtn, MethodKind.momo, "tok-tip");
  }

  private static Money ghs(long minor) {
    return Money.ofMinor(minor, Currency.GHS);
  }

  @Test
  void tipWithinCeiling_isAccepted_andChargesOnce() {
    TipView view = service.tip(FAN, CREATOR, ghs(1_000), momo(), KEY);
    assertEquals(CREATOR.value(), view.creatorAccountId());
    assertEquals(1, gateway.initiateCalls());
  }

  @Test
  void tipAboveCeiling_isRejected_CHARGE_AMOUNT_EXCEEDED_andNeverCharges() {
    // maxChargeMinor default is 100_000_000 pesewas (₵1,000,000); ₵9,000,000 = 900_000_000 exceeds it.
    long overCeiling = settings.current().maxChargeMinor() + 1;
    ChargeAmountExceededException ex =
        assertThrows(
            ChargeAmountExceededException.class,
            () -> service.tip(FAN, CREATOR, ghs(overCeiling), momo(), KEY));
    assertEquals(
        org.shakvilla.beatzmedia.platform.domain.ErrorCode.CHARGE_AMOUNT_EXCEEDED,
        ex.getErrorCode());
    // No provider charge was initiated and no idempotency lock was even taken for an out-of-bounds tip.
    assertEquals(0, gateway.initiateCalls());
    assertEquals(0, repository.lockCalls());
  }

  @Test
  void nonPositiveTip_isRejected_asValidation() {
    assertThrows(
        ValidationException.class, () -> service.tip(FAN, CREATOR, ghs(0), momo(), KEY));
    assertEquals(0, gateway.initiateCalls());
  }
}
