package org.shakvilla.beatzmedia.podcasts.fakes;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.podcasts.application.port.in.TipMethod;
import org.shakvilla.beatzmedia.podcasts.application.port.out.IssueTipUseCase;
import org.shakvilla.beatzmedia.podcasts.application.port.out.TipOutcome;

/**
 * In-memory fake for {@link IssueTipUseCase} used in {@code TipShowService} unit tests. Captures the
 * exact arguments {@code TipShow} forwards to the payments boundary so a test can assert that the
 * recipient creator was resolved server-side (never the fan) and that the amount/method/key pass
 * through unchanged. Returns a canned {@link TipOutcome}.
 */
public class FakeIssueTipUseCase implements IssueTipUseCase {

  public AccountId lastFan;
  public AccountId lastCreator;
  public Money lastAmount;
  public TipMethod lastMethod;
  public String lastIdempotencyKey;
  public int calls;

  private TipOutcome outcome = new TipOutcome("tip-1", "pending");

  public FakeIssueTipUseCase returning(TipOutcome outcome) {
    this.outcome = outcome;
    return this;
  }

  @Override
  public TipOutcome issueTip(
      AccountId fan, AccountId creator, Money amount, TipMethod method, String idempotencyKey) {
    this.calls++;
    this.lastFan = fan;
    this.lastCreator = creator;
    this.lastAmount = amount;
    this.lastMethod = method;
    this.lastIdempotencyKey = idempotencyKey;
    return outcome;
  }
}
