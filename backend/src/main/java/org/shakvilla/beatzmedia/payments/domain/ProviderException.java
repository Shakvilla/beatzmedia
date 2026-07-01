package org.shakvilla.beatzmedia.payments.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when the upstream payment provider rejects or fails a charge initiation. The application
 * service catches this to mark the {@link PaymentIntent} {@code failed}; if it propagates to the
 * boundary it maps to HTTP 502 {@code PROVIDER_ERROR}. Payments ADD §5.2.
 */
public class ProviderException extends DomainException {

  public ProviderException(String message) {
    super(ErrorCode.PROVIDER_ERROR, message);
  }
}
