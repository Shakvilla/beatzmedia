package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;

/**
 * Input port: a creator adds a cash-out destination (LLFR-PAYMENTS-03.1). The first method added
 * becomes the default. Auth: artist (own studio). Framework-free command.
 */
public interface AddPayoutMethod {

  PayoutMethodView add(AccountId creator, Command cmd);

  /** Command: the method label + masked detail + kind (momo/bank). */
  record Command(String label, String detail, MethodKind kind) {}
}
