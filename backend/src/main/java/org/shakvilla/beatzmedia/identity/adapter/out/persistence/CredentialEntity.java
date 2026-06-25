package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code credential} table. Never serialized, never logged. Identity ADD §5.2.
 */
@Entity
@Table(name = "credential")
public class CredentialEntity {

  @Id
  @Column(name = "account_id", nullable = false)
  public String accountId;

  @Column(name = "password_hash", nullable = false)
  public String passwordHash;

  @Column(name = "algo", nullable = false)
  public String algo;
}
