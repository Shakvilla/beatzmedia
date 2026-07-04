package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;

/**
 * Input port: a creator sets one of their methods as the default (LLFR-PAYMENTS-03.1). Clears the
 * prior default in the same transaction (exactly one default per account). Auth: artist (own studio).
 */
public interface SetDefaultPayoutMethod {

  PayoutMethodView setDefault(AccountId creator, PayoutMethodId id);
}
