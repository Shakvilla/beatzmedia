package org.shakvilla.beatzmedia.payments.application.port.out;

import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.KycStatus;

/**
 * Output port resolving a creator's KYC verification state (payments ADD §4.2, INV-8). The ADD models
 * this as resolving to the {@code identity} module's KYC read; identity has no KYC surface yet, so the
 * authoritative record is a payments-owned {@code kyc_record} table (V704) behind this port. A future
 * identity KYC WU can back this port without changing the payment services. The withdrawal/payout
 * services gate on {@link KycStatus#isVerified()} — a non-verified creator gets a mapped error, never
 * a 500.
 */
public interface KycProvider {

  /** The KYC status for a creator; {@link KycStatus#NONE} if no record exists. */
  KycStatus statusOf(AccountId creator);
}
