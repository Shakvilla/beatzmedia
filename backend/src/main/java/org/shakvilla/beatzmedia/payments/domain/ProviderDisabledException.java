package org.shakvilla.beatzmedia.payments.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * The requested payment rail is switched off platform-wide (GAP-13). Maps to HTTP 409
 * {@code PROVIDER_DISABLED}.
 *
 * <p>409 rather than 422 because nothing is wrong with the request: the same body would have
 * succeeded before an operator disabled the rail, and will again if they re-enable it. That
 * distinction matters to a client deciding whether to offer a retry or a different method.
 *
 * <p>The message names the rail but never why it is off. An operator's reason for pulling a
 * provider — a PSP outage, a commercial dispute, suspected fraud on that channel — is not something
 * to hand to whoever is at the checkout.
 */
public class ProviderDisabledException extends DomainException {

  public ProviderDisabledException(Provider provider) {
    super(
        ErrorCode.PROVIDER_DISABLED,
        "Payments are not being accepted on " + provider.name() + " right now",
        "provider");
  }
}
