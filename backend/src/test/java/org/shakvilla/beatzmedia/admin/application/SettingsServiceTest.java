package org.shakvilla.beatzmedia.admin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.application.port.in.PlatformSettingsInput;
import org.shakvilla.beatzmedia.admin.application.port.in.PlatformSettingsView;
import org.shakvilla.beatzmedia.admin.application.service.GetSettingsService;
import org.shakvilla.beatzmedia.admin.application.service.SaveSettingsService;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.platform.domain.FeatureKey;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeFeatureFlags;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.platform.fakes.FakePlatformSettingsProvider;

/**
 * Unit tests for {@link GetSettingsService} + {@link SaveSettingsService} (LLFR-ADMIN-10.1): the
 * settings→view mapping, the forward-only fee change (audited), flag persistence, and input
 * validation.
 */
@Tag("unit")
class SettingsServiceTest {

  private FakePlatformSettingsProvider settings;
  private FakeFeatureFlags flags;
  private FakeAuditWriter audit;
  private GetSettingsService getService;
  private SaveSettingsService saveService;

  @BeforeEach
  void setUp() {
    settings = new FakePlatformSettingsProvider(); // defaults: platformFeePct 30
    flags = new FakeFeatureFlags();
    audit = new FakeAuditWriter();
    getService = new GetSettingsService(settings, flags);
    saveService =
        new SaveSettingsService(settings, flags, audit, FakeIds.sequential("id"), FakeClock.fixed());
  }

  private PlatformSettingsInput input(int feePct, boolean allFlags) {
    return new PlatformSettingsInput(
        feePct,
        "Friday",
        new BigDecimal("20.00"),
        "GHS",
        false,
        new PlatformSettingsView.Providers(true, true, true, true, true),
        new PlatformSettingsView.Flags(allFlags, allFlags, allFlags, allFlags, allFlags));
  }

  @Test
  void getMapsSettingsAndFlagsToFrontendShape() {
    PlatformSettingsView v = getService.get();
    assertEquals(30, v.platformFeePct());
    assertEquals("Friday", v.payoutDay());
    assertEquals(new BigDecimal("10.00"), v.payoutMinimum()); // defaults payoutMinimumMinor 1000
    assertEquals("GHS", v.defaultCurrency());
    assertTrue(v.providers().momo()); // honest-static true
    assertTrue(v.flags().tipping()); // real flag, seeded true
    assertFalse(v.flags().fanMessaging()); // real flag, seeded false
  }

  @Test
  void saveChangesFeeForwardOnlyAndAuditsWithDelta() {
    PlatformSettingsView v = saveService.save("admin-1", input(25, true));

    assertEquals(25, v.platformFeePct());
    assertEquals(25, settings.current().platformFeePct());
    assertEquals(75, settings.current().creatorSharePct()); // kept complementary
    assertEquals(2000L, settings.current().payoutMinimumMinor()); // ₵20.00
    assertEquals(1, audit.size());
    assertEquals(AuditType.SETTINGS, audit.all().get(0).getType());
    assertEquals("platformFeePct: 30 -> 25", audit.all().get(0).getReason());
  }

  @Test
  void savePersistsAllFiveFlags() {
    saveService.save("admin-1", input(30, false));
    for (FeatureKey key : FeatureKey.values()) {
      assertFalse(flags.isEnabled(key), key + " should be disabled after save");
    }
  }

  @Test
  void saveWithoutFeeChangeStillAuditsWithNullReason() {
    saveService.save("admin-1", input(30, true)); // fee unchanged (defaults 30)
    assertEquals(1, audit.size());
    assertEquals(null, audit.all().get(0).getReason());
  }

  @Test
  void saveWithAbsurdPayoutMinimumIs422NotOverflow() {
    PlatformSettingsInput bad =
        new PlatformSettingsInput(
            30, "Friday", new BigDecimal("1000000000000000000000"), "GHS", false,
            new PlatformSettingsView.Providers(true, true, true, true, true),
            new PlatformSettingsView.Flags(true, true, true, true, false));
    assertThrows(ValidationException.class, () -> saveService.save("admin-1", bad));
  }

  @Test
  void saveWithUnsupportedCurrencyIs422() {
    PlatformSettingsInput bad =
        new PlatformSettingsInput(
            30, "Friday", new BigDecimal("10.00"), "USD", false,
            new PlatformSettingsView.Providers(true, true, true, true, true),
            new PlatformSettingsView.Flags(true, true, true, true, false));
    assertThrows(ValidationException.class, () -> saveService.save("admin-1", bad));
  }
}
