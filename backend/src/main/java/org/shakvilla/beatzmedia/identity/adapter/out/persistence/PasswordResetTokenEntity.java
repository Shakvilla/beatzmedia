package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code password_reset_token} table. The primary key is the SHA-256 hash of
 * the opaque plaintext token — the plaintext itself is never persisted. Domain types carry no ORM
 * annotations; this adapter class is the only place Hibernate annotations appear. Identity ADD
 * §5.2 / conventions §6.
 */
@Entity
@Table(name = "password_reset_token")
public class PasswordResetTokenEntity {

  @Id
  @Column(name = "token_hash", nullable = false)
  public String tokenHash;

  @Column(name = "account_id", nullable = false)
  public String accountId;

  @Column(name = "expires_at", nullable = false)
  public Instant expiresAt;

  @Column(name = "used", nullable = false)
  public boolean used;
}
