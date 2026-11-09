package org.shakvilla.beatzmedia.identity.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Generic upstream delivery failure; never exposes provider credentials or response content. */
public class PhoneVerificationDeliveryException extends DomainException {
  public PhoneVerificationDeliveryException() {
    super(ErrorCode.PROVIDER_ERROR, "The verification code could not be sent. Please try again.");
  }
}
