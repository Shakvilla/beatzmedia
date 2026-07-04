package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.DisputeId;

/**
 * Input port: admin escalates a dispute for further review (LLFR-PAYMENTS-04.3). Transitions
 * {@code open → escalated}. No money moves and no ownership is revoked. Audited (INV-10). Auth:
 * finance/super-admin.
 */
public interface EscalateDispute {

  DisputeView escalate(String adminActorId, DisputeId id);
}
