package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;

/**
 * Input port: an admin executes the weekly payout run (LLFR-PAYMENTS-03.3) — pays every payable,
 * KYC-verified withdrawal in one batch. KYC-blocked withdrawals are SKIPPED (left pending) rather
 * than failing the whole run. Idempotent: a retried run for the same key returns the same batch, and
 * per-withdrawal exactly-once ({@code uq_payout_per_withdrawal}) prevents double-pay on any retry.
 * Auth: finance / super-admin. Emits {@code PayoutSent} per txn.
 */
public interface RunWeeklyPayouts {

  PayoutBatchView runWeekly(String adminActorId, IdempotencyKey key);
}
