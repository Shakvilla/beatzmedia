package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.PhoneVerificationChallenge;
import org.shakvilla.beatzmedia.identity.domain.PhoneVerificationStatus;

/** Pure mapper between challenge persistence and domain. */
final class PhoneVerificationChallengeMapper {
  private PhoneVerificationChallengeMapper() {}

  static PhoneVerificationChallengeEntity toEntity(PhoneVerificationChallenge challenge) {
    PhoneVerificationChallengeEntity entity = new PhoneVerificationChallengeEntity();
    entity.id = challenge.id();
    entity.accountId = challenge.accountId().value();
    entity.phone = challenge.phone();
    entity.codeHash = challenge.codeHash();
    entity.providerCode = challenge.providerCode();
    entity.status = challenge.status().name();
    entity.attempts = challenge.attempts();
    entity.createdAt = challenge.createdAt();
    entity.expiresAt = challenge.expiresAt();
    entity.sentAt = challenge.sentAt();
    entity.consumedAt = challenge.consumedAt();
    return entity;
  }

  static PhoneVerificationChallenge toDomain(PhoneVerificationChallengeEntity entity) {
    return new PhoneVerificationChallenge(
        entity.id,
        new AccountId(entity.accountId),
        entity.phone,
        entity.codeHash,
        entity.providerCode,
        PhoneVerificationStatus.valueOf(entity.status),
        entity.attempts,
        entity.createdAt,
        entity.expiresAt,
        entity.sentAt,
        entity.consumedAt);
  }
}
