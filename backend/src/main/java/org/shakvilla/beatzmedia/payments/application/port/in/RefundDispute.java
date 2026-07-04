package org.shakvilla.beatzmedia.payments.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.payments.domain.DisputeId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Input port: admin refunds a dispute (LLFR-PAYMENTS-04.2, INV-9). Transitions the dispute
 * {@code open → refunded}, posts a BALANCED ledger reversal of the original sale/tip split (clawback
 * of the creator credit + platform fee), and emits {@code OrderRefunded} so commerce revokes the
 * ownership grants (album/season → all constituent tracks/episodes). Money POST — an idempotency key
 * makes a retried refund exactly one clawback. Audited (INV-10). Auth: finance/super-admin.
 */
public interface RefundDispute {

  DisputeView refund(String adminActorId, DisputeId id, Command cmd, IdempotencyKey key);

  /**
   * Command: an optional partial {@code amount} (empty ⇒ full refund of the dispute amount) and a
   * required {@code reason} (logged on the audit + dispute timeline).
   */
  record Command(Optional<Money> amount, String reason) {}
}
