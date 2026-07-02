package org.shakvilla.beatzmedia.payments.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when an inbound provider webhook carries a missing or invalid signature (verified over the
 * raw request bytes against {@code BEATZ_PAYMENT_WEBHOOK_SECRET}). Maps to HTTP {@code 401} via
 * {@link ErrorCode#UNAUTHENTICATED}. The message is intentionally generic so the response does not
 * leak why verification failed (security-authz §5 / payments ADD §9). LLFR-PAYMENTS-01.2.
 */
public class WebhookSignatureException extends DomainException {

  public WebhookSignatureException() {
    super(ErrorCode.UNAUTHENTICATED, "invalid webhook signature");
  }
}
