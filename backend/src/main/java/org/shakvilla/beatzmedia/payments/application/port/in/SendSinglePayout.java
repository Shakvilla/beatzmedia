package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalId;

/**
 * Input port: an admin sends a single payout for one withdrawal (LLFR-PAYMENTS-03.4). BLOCKS on KYC —
 * an unverified creator yields a mapped {@code KYC_BLOCKED} (409), never a silent skip (contrast with
 * the weekly run). Exactly-once: a retried send for an already-paid withdrawal is a no-op replay
 * (returns the existing txn) via the {@code uq_payout_per_withdrawal} guard. Auth: finance /
 * super-admin. Emits {@code PayoutSent}.
 */
public interface SendSinglePayout {

  PayoutTxnView send(String adminActorId, WithdrawalId withdrawalId, IdempotencyKey key);
}
