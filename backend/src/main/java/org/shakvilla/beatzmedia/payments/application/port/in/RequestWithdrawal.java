package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Input port: a creator requests a cash-out (LLFR-PAYMENTS-03.2). KYC-gated (INV-8), floor-gated
 * (≥ {@code PlatformSettings.payoutMinimumMinor}), and balance-backed — the amount is drawn against
 * available balance net of already-reserved withdrawals under a row lock so concurrent requests can't
 * double-spend. Idempotent: same {@link IdempotencyKey} ⇒ the same withdrawal (one reservation).
 * Auth: artist (own studio).
 */
public interface RequestWithdrawal {

  WithdrawalView request(AccountId creator, Command cmd, IdempotencyKey key);

  /** Command: the requested gross amount + the destination method id. */
  record Command(Money amount, PayoutMethodId methodId) {}
}
