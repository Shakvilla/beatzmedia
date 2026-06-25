package org.shakvilla.beatzmedia.identity.domain;

/**
 * Password credential entity scoped to an {@link Account} aggregate. Always Argon2id; never
 * serialized, never logged. Identity ADD §3.
 */
public final class Credential {

  /** The only supported hashing algorithm. */
  public static final String ALGO_ARGON2ID = "argon2id";

  private final AccountId accountId;
  private final String passwordHash;
  private final String algo;

  public Credential(AccountId accountId, String passwordHash) {
    this.accountId = accountId;
    this.passwordHash = passwordHash;
    this.algo = ALGO_ARGON2ID;
  }

  public Credential(AccountId accountId, String passwordHash, String algo) {
    this.accountId = accountId;
    this.passwordHash = passwordHash;
    this.algo = algo;
  }

  public AccountId getAccountId() {
    return accountId;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getAlgo() {
    return algo;
  }
}
