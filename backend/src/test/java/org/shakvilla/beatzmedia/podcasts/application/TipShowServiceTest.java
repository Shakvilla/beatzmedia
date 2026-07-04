package org.shakvilla.beatzmedia.podcasts.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.podcasts.application.port.in.TipMethod;
import org.shakvilla.beatzmedia.podcasts.application.port.out.TipOutcome;
import org.shakvilla.beatzmedia.podcasts.application.service.TipShowService;
import org.shakvilla.beatzmedia.podcasts.domain.Podcast;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastCategory;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastNotFoundException;
import org.shakvilla.beatzmedia.podcasts.domain.SelfTipNotAllowedException;
import org.shakvilla.beatzmedia.podcasts.domain.TipResult;
import org.shakvilla.beatzmedia.podcasts.domain.TipsDisabledException;
import org.shakvilla.beatzmedia.podcasts.fakes.FakeIssueTipUseCase;
import org.shakvilla.beatzmedia.podcasts.fakes.FakePodcastRepository;

/**
 * Unit tests for {@link TipShowService} — the podcast-side of the tip flow (LLFR-PODCAST-02.1).
 *
 * <p>These prove the guards this module owns before it ever touches the money path: the recipient
 * creator is resolved server-side from the show (never client-supplied), a self-tip is rejected, an
 * unknown show is a mapped 404, a show without tips/creator is {@code TIPS_DISABLED}, and a
 * non-positive amount is {@code VALIDATION}. The actual 90/10 split lives in payments and is proven
 * end-to-end in {@code PodcastTipFlowIT}; here {@code FakeIssueTipUseCase} stands in for that boundary
 * so we can assert exactly what {@code TipShow} forwards.
 */
@Tag("unit")
class TipShowServiceTest {

  private static final Instant CREATED = Instant.parse("2026-06-01T00:00:00Z");
  private static final String CREATOR = "creator-acct";
  private static final String FAN = "fan-acct";

  FakePodcastRepository repository;
  FakeIssueTipUseCase issueTip;
  TipShowService service;

  @BeforeEach
  void setUp() {
    repository = new FakePodcastRepository();
    issueTip = new FakeIssueTipUseCase();
    service = new TipShowService(repository, issueTip);
  }

  private void seedShow(String id, String creator, boolean supportsTips) {
    repository.withShow(
        new Podcast(
            new PodcastId(id), "Show", "Pub", creator, "img.png", PodcastCategory.CULTURE,
            "desc", 10, 90, null, supportsTips, CREATED));
  }

  private static Money cedis(String value) {
    return Money.ofCedis(new java.math.BigDecimal(value), Currency.GHS);
  }

  private static TipMethod momo() {
    return new TipMethod("mtn", "momo", "tok-tip");
  }

  @Test
  void validTip_resolvesCreatorServerSide_andDelegatesToPayments() {
    seedShow("show-1", CREATOR, true);
    issueTip.returning(new TipOutcome("tip-99", "pending"));

    TipResult result =
        service.tip(new PodcastId("show-1"), new AccountId(FAN), cedis("10.00"), momo(), "key-1");

    assertEquals("tip-99", result.tipId());
    assertEquals("pending", result.status());
    assertEquals(1, issueTip.calls);
    // The recipient is the SHOW's creator, resolved server-side — never the fan, never a body field.
    assertEquals(CREATOR, issueTip.lastCreator.value());
    assertEquals(FAN, issueTip.lastFan.value());
    assertEquals(1000, issueTip.lastAmount.minor());
    assertEquals("key-1", issueTip.lastIdempotencyKey);
  }

  @Test
  void selfTip_isRejected_andNeverTouchesPayments() {
    // The fan IS the show's creator → self-tip guard fires (fan == creator).
    seedShow("show-self", FAN, true);

    assertThrows(
        SelfTipNotAllowedException.class,
        () -> service.tip(new PodcastId("show-self"), new AccountId(FAN), cedis("5.00"), momo(),
            "key-self"));
    assertEquals(0, issueTip.calls);
  }

  @Test
  void unknownShow_throwsPodcastNotFound() {
    assertThrows(
        PodcastNotFoundException.class,
        () -> service.tip(new PodcastId("nope"), new AccountId(FAN), cedis("5.00"), momo(), "k"));
    assertEquals(0, issueTip.calls);
  }

  @Test
  void tipsDisabledShow_throwsTipsDisabled() {
    seedShow("show-off", CREATOR, false);
    assertThrows(
        TipsDisabledException.class,
        () -> service.tip(new PodcastId("show-off"), new AccountId(FAN), cedis("5.00"), momo(), "k"));
    assertEquals(0, issueTip.calls);
  }

  @Test
  void showWithoutCreator_throwsTipsDisabled_neverPostsToPhantom() {
    // supportsTips=true but no creator_account_id → cannot post the 90% to a phantom recipient.
    seedShow("show-noone", null, true);
    assertThrows(
        TipsDisabledException.class,
        () -> service.tip(new PodcastId("show-noone"), new AccountId(FAN), cedis("5.00"), momo(),
            "k"));
    assertEquals(0, issueTip.calls);
  }

  @Test
  void nonPositiveAmount_throwsValidation() {
    seedShow("show-1", CREATOR, true);
    assertThrows(
        ValidationException.class,
        () -> service.tip(new PodcastId("show-1"), new AccountId(FAN), cedis("0.00"), momo(), "k"));
    assertEquals(0, issueTip.calls);
  }
}
