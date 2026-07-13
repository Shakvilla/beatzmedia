package org.shakvilla.beatzmedia.payments.domain;

/**
 * The structured destination a disbursement is sent to (WU-PAY-7) — the shape Redde's {@code
 * /v1/cashout} needs, replacing the opaque free-text {@code PayoutMethod.detail} for the gateway
 * call. A sealed pair: {@link Momo} (a mobile-money network + wallet number) or {@link Bank} (a Ghana
 * bank code + account). Built from a {@link PayoutMethod} by {@code PayoutMethod.toDestination()},
 * which validates completeness, so the gateway adapter receives an already-valid destination.
 * Framework-free.
 */
public sealed interface PayoutDestination permits PayoutDestination.Momo, PayoutDestination.Bank {

  /** A mobile-money cash-out target: the rail {@code network} (mtn/telecel/airteltigo) + wallet. */
  record Momo(Provider network, String walletNumber) implements PayoutDestination {
    public Momo {
      if (network == null || !network.isMomo()) {
        throw new IllegalArgumentException("payout momo network must be a MoMo rail; got " + network);
      }
      if (walletNumber == null || walletNumber.isBlank()) {
        throw new IllegalArgumentException("payout wallet number must not be blank");
      }
    }
  }

  /** A bank cash-out target: a validated Ghana bank code + the destination account. */
  record Bank(GhanaBankCode bankCode, String bankName, String accountName, String accountNumber)
      implements PayoutDestination {
    public Bank {
      if (bankCode == null) {
        throw new IllegalArgumentException("payout bank code must not be null");
      }
      if (accountNumber == null || accountNumber.isBlank()) {
        throw new IllegalArgumentException("payout account number must not be blank");
      }
      if (accountName == null || accountName.isBlank()) {
        throw new IllegalArgumentException("payout account name must not be blank");
      }
    }
  }
}
