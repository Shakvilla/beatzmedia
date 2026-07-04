package org.shakvilla.beatzmedia.payments.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a payout method cannot be removed because it is still referenced by a withdrawal
 * request (even an already-{@code paid} one). The {@code withdrawal_request.method_id} FK (V704) is
 * {@code ON DELETE RESTRICT}, so a raw delete would surface an opaque Postgres FK violation → an
 * unmapped 500; this domain exception makes the failure a deterministic, mapped 409 {@code
 * PAYOUT_METHOD_IN_USE} instead (uniform error envelope, no unmapped 500 — code-review F-NEW-1).
 * Payments ADD §8.
 */
public class PayoutMethodInUseException extends DomainException {

  public PayoutMethodInUseException(PayoutMethodId id) {
    super(
        ErrorCode.PAYOUT_METHOD_IN_USE,
        "payout method " + id + " is referenced by a withdrawal and cannot be removed");
  }
}
