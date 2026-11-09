package org.shakvilla.beatzmedia.identity.application.port.in;

import java.time.Instant;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Starts SMS verification for a phone number owned by the authenticated account. */
public interface StartPhoneVerification {
  StartPhoneVerificationView start(AccountId accountId, String phone);

  record StartPhoneVerificationView(
      String maskedPhone, boolean alreadyVerified, Instant expiresAt, Instant resendAvailableAt) {}
}
