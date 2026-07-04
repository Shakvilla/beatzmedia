package org.shakvilla.beatzmedia.podcasts.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.podcasts.application.port.in.TipMethod;
import org.shakvilla.beatzmedia.podcasts.application.port.in.TipShow;
import org.shakvilla.beatzmedia.podcasts.application.port.out.IssueTipUseCase;
import org.shakvilla.beatzmedia.podcasts.application.port.out.PodcastRepository;
import org.shakvilla.beatzmedia.podcasts.application.port.out.TipOutcome;
import org.shakvilla.beatzmedia.podcasts.domain.Podcast;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastNotFoundException;
import org.shakvilla.beatzmedia.podcasts.domain.SelfTipNotAllowedException;
import org.shakvilla.beatzmedia.podcasts.domain.TipResult;
import org.shakvilla.beatzmedia.podcasts.domain.TipsDisabledException;

/**
 * Application service for {@link TipShow} (LLFR-PODCAST-02.1). Podcasts ADD §4.1 / §8(b).
 *
 * <p>This service owns ONLY the podcast-side concerns of a tip: it resolves the show and its owning
 * creator server-side, runs the tippability + self-tip guards, then delegates the actual money
 * movement to the payments module through the {@link IssueTipUseCase} output port. It never
 * re-implements the 90/10 split, the ledger, idempotency, or the audit — those live entirely in the
 * WU-PAY-3 tip pipeline ({@code IssueTip → TipSettlementSubscriber → TipLedgerPoster}). The split is
 * posted on settlement (INV-1/INV-4/INV-6) from {@code PlatformSettings.tipFeePct} (OQ-2 default 10%).
 *
 * <p><strong>Server-side guards (never client-trusted):</strong>
 *
 * <ul>
 *   <li>the {@code fan} is the authenticated caller (JWT subject), passed in by the resource;
 *   <li>the recipient creator is resolved from the persisted {@code podcast.creator_account_id} — a
 *       client-supplied recipient id is structurally impossible on this path;
 *   <li>the show must exist ({@code NOT_FOUND}) — every failure is a MAPPED 4xx, never a 500;
 *   <li>the show must accept tips and have an owning creator ({@code TIPS_DISABLED}) — a show with no
 *       {@code creator_account_id} would post money to a phantom recipient, so it is rejected;
 *   <li>a fan cannot tip a show they own/created ({@code fan == creator} → {@code SELF_TIP_NOT_ALLOWED},
 *       422) — this prevents a self-directed 90/10 round-trip and the associated fee leakage;
 *   <li>the amount must be positive minor units ({@code VALIDATION}) — payments re-asserts this and
 *       enforces the charge ceiling, but we fail fast here before touching the money path.
 * </ul>
 */
@ApplicationScoped
@Transactional
public class TipShowService implements TipShow {

  private final PodcastRepository repository;
  private final IssueTipUseCase issueTip;

  @Inject
  public TipShowService(PodcastRepository repository, IssueTipUseCase issueTip) {
    this.repository = repository;
    this.issueTip = issueTip;
  }

  @Override
  public TipResult tip(
      PodcastId id, AccountId fan, Money amount, TipMethod method, String idempotencyKey) {
    if (fan == null) {
      throw new IllegalArgumentException("fan must not be null");
    }
    if (amount == null || !amount.isPositive()) {
      throw new ValidationException("tip amount must be positive", "amount");
    }

    Podcast show =
        repository.findShow(id).orElseThrow(() -> new PodcastNotFoundException(id.value()));

    // A show is tippable only if the flag is on AND it has an owning creator to receive the 90%.
    // Both a disabled flag and a missing creator map to the same fan-visible signal (TIPS_DISABLED)
    // rather than posting money to a phantom recipient.
    if (!show.supportsTips() || show.creatorAccountId().isEmpty()) {
      throw new TipsDisabledException(id.value());
    }

    AccountId creator = new AccountId(show.creatorAccountId().get());

    // Self-tip guard: the server-resolved creator must differ from the authenticated fan.
    if (creator.value().equals(fan.value())) {
      throw new SelfTipNotAllowedException();
    }

    TipOutcome outcome = issueTip.issueTip(fan, creator, amount, method, idempotencyKey);
    return new TipResult(outcome.tipId(), outcome.status());
  }
}
