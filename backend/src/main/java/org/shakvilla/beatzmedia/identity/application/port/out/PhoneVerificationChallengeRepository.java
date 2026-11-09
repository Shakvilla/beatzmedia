package org.shakvilla.beatzmedia.identity.application.port.out;

import java.time.Instant;
import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.PhoneVerificationChallenge;

/** Persistence port for short-lived, single-use phone verification challenges. */
public interface PhoneVerificationChallengeRepository {
  Optional<PhoneVerificationChallenge> findActive(AccountId accountId, Instant now, boolean lock);

  Optional<PhoneVerificationChallenge> findById(String id);

  Optional<PhoneVerificationChallenge> findLatestForAccount(AccountId accountId);

  long countCreatedForAccountSince(AccountId accountId, Instant since);

  long countCreatedForPhoneSince(String phone, Instant since);

  void invalidateActive(AccountId accountId);

  PhoneVerificationChallenge save(PhoneVerificationChallenge challenge);

  void deleteCreatedBefore(Instant before);
}
