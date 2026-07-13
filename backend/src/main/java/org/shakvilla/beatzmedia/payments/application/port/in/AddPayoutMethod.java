package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;

/**
 * Input port: a creator adds a cash-out destination (LLFR-PAYMENTS-03.1). The first method added
 * becomes the default. Auth: artist (own studio). Framework-free command.
 */
public interface AddPayoutMethod {

  PayoutMethodView add(AccountId creator, Command cmd);

  /**
   * Command: the method label + masked detail + kind (momo/bank), plus the structured destination
   * fields Redde's cashout needs (WU-PAY-7). For {@code momo}: {@code network} (mtn/telecel/airteltigo)
   * + {@code walletNumber}. For {@code bank}: {@code bankCode} + {@code bankName} + {@code accountName}
   * + {@code accountNumber}. The unused subset for a given kind is {@code null}; the service validates
   * the required subset and rejects an incomplete command with a {@code 422}.
   */
  record Command(
      String label,
      String detail,
      MethodKind kind,
      String network,
      String walletNumber,
      String bankName,
      String bankCode,
      String accountName,
      String accountNumber) {}
}
