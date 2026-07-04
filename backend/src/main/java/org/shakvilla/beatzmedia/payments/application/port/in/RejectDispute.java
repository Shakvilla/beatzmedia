package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.DisputeId;

/**
 * Input port: admin rejects a dispute (LLFR-PAYMENTS-04.3). Transitions {@code open → rejected} with
 * a reason. No money moves and no ownership is revoked. Audited (INV-10). Auth: finance/super-admin.
 */
public interface RejectDispute {

  DisputeView reject(String adminActorId, DisputeId id, String reason);
}
