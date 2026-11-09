package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for an expiring phone verification challenge. */
@Entity
@Table(name = "phone_verification_challenge")
public class PhoneVerificationChallengeEntity {
  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "account_id", nullable = false)
  public String accountId;

  @Column(name = "phone", nullable = false)
  public String phone;

  @Column(name = "code_hash", nullable = false)
  public String codeHash;

  @Column(name = "provider_code", nullable = false)
  public String providerCode;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "attempts", nullable = false)
  public int attempts;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  public Instant expiresAt;

  @Column(name = "sent_at")
  public Instant sentAt;

  @Column(name = "consumed_at")
  public Instant consumedAt;
}
