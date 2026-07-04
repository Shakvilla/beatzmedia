package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;

/**
 * Input port: a creator removes a cash-out destination (LLFR-PAYMENTS-03.1). Only the owning creator
 * may remove their own method (ownership-scoped). Auth: artist (own studio).
 */
public interface RemovePayoutMethod {

  void remove(AccountId creator, PayoutMethodId id);
}
