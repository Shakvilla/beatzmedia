package org.shakvilla.beatzmedia.identity.domain;

import java.time.Instant;

/** Persisted, single-use phone verification challenge. Only the keyed code hash is stored. */
public record PhoneVerificationChallenge(
    String id,
    AccountId accountId,
    String phone,
    String codeHash,
    String providerCode,
    PhoneVerificationStatus status,
    int attempts,
    Instant createdAt,
    Instant expiresAt,
    Instant sentAt,
    Instant consumedAt) {

  public PhoneVerificationChallenge sent(Instant now) {
    return new PhoneVerificationChallenge(
        id, accountId, phone, codeHash, providerCode, PhoneVerificationStatus.sent,
        attempts, createdAt, expiresAt, now, consumedAt);
  }

  public PhoneVerificationChallenge failed() {
    return new PhoneVerificationChallenge(
        id, accountId, phone, codeHash, providerCode, PhoneVerificationStatus.failed,
        attempts, createdAt, expiresAt, sentAt, consumedAt);
  }

  public PhoneVerificationChallenge incorrectCode() {
    int nextAttempts = attempts + 1;
    PhoneVerificationStatus nextStatus = nextAttempts >= 5
        ? PhoneVerificationStatus.failed : status;
    return new PhoneVerificationChallenge(
        id, accountId, phone, codeHash, providerCode, nextStatus,
        nextAttempts, createdAt, expiresAt, sentAt, consumedAt);
  }

  public PhoneVerificationChallenge consumed(Instant now) {
    return new PhoneVerificationChallenge(
        id, accountId, phone, codeHash, providerCode, PhoneVerificationStatus.consumed,
        attempts, createdAt, expiresAt, sentAt, now);
  }

  public PhoneVerificationChallenge expired() {
    return new PhoneVerificationChallenge(
        id, accountId, phone, codeHash, providerCode, PhoneVerificationStatus.expired,
        attempts, createdAt, expiresAt, sentAt, consumedAt);
  }
}
