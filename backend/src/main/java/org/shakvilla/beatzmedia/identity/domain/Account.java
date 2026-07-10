package org.shakvilla.beatzmedia.identity.domain;

import java.time.Instant;

/**
 * Account aggregate root. Owns identity, role flags, and lifecycle. Mutated only through
 * intention-revealing methods that enforce invariants. No framework imports. Identity ADD §3.
 */
public final class Account {

  private final AccountId id;
  private final String name;
  private final String email;
  private final String avatar;
  private final boolean isArtist;
  private final boolean isAdmin;
  private boolean verified;
  private AccountStatus status;
  private final Instant createdAt;
  private Instant updatedAt;
  private Credential credential;

  private Account(
      AccountId id,
      String name,
      String email,
      String avatar,
      boolean isArtist,
      boolean isAdmin,
      boolean verified,
      AccountStatus status,
      Instant createdAt,
      Instant updatedAt,
      Credential credential) {
    this.id = id;
    this.name = name;
    this.email = email;
    this.avatar = avatar;
    this.isArtist = isArtist;
    this.isAdmin = isAdmin;
    this.verified = verified;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.credential = credential;
  }

  /**
   * Returns a new Account with {@code isArtist=true} and {@code updatedAt} set to {@code now}.
   * The Account is immutable so this returns a copy. Idempotent: callers should check
   * {@code isArtist()} first and skip if already true (no-op success).
   * Identity ADD §3 / LLFR-IDENTITY-02.2.
   */
  public Account upgradeToArtist(Instant now) {
    return new Account(
        id, name, email, avatar, true, isAdmin, verified, status, createdAt, now, credential);
  }

  /**
   * Factory for a new fan account. Sets isArtist=false, isAdmin=false, verified=false,
   * status=active. Identity ADD §3 / LLFR-IDENTITY-01.1.
   */
  public static Account createFan(
      AccountId id, String name, String email, Credential credential, Instant now) {
    return new Account(
        id, name, email, null, false, false, false, AccountStatus.active, now, now, credential);
  }

  /**
   * Factory for a new fan account created via social login (no password credential). Sets
   * isArtist=false, isAdmin=false, verified=false, status=active. Identity ADD §3 /
   * LLFR-IDENTITY-01.3.
   */
  public static Account createSocialFan(AccountId id, String name, String email, String avatar, Instant now) {
    return new Account(
        id, name, email, avatar, false, false, false, AccountStatus.active, now, now, null);
  }

  /**
   * Reconstitution factory used by the persistence adapter to rebuild the aggregate from stored
   * state. Does not enforce creation invariants (they were enforced on first creation).
   */
  public static Account reconstitute(
      AccountId id,
      String name,
      String email,
      String avatar,
      boolean isArtist,
      boolean isAdmin,
      boolean verified,
      AccountStatus status,
      Instant createdAt,
      Instant updatedAt,
      Credential credential) {
    return new Account(
        id, name, email, avatar, isArtist, isAdmin, verified, status, createdAt, updatedAt,
        credential);
  }

  /** Returns true when this account may authenticate (not suspended or banned). */
  public boolean canAuthenticate() {
    return status == AccountStatus.active || status == AccountStatus.pending;
  }

  /** Suspends this account. Caller is responsible for appending an AuditEntry (INV-10). */
  public void suspend(Instant now) {
    this.status = AccountStatus.suspended;
    this.updatedAt = now;
  }

  /**
   * Reactivates a suspended account. Caller is responsible for appending an AuditEntry (INV-10).
   *
   * @throws AccountNotSuspendedException if the account is not currently suspended (409
   *     NOT_SUSPENDED). LLFR-ADMIN-02.4.
   */
  public void reactivate(Instant now) {
    if (this.status != AccountStatus.suspended) {
      throw new AccountNotSuspendedException();
    }
    this.status = AccountStatus.active;
    this.updatedAt = now;
  }

  /**
   * Marks this account as verified (admin badge). Caller is responsible for appending an
   * AuditEntry (INV-10). No "must be an artist" guard — a verified fan account is a harmless
   * no-op-ish state; the frontend simply never surfaces the verify action for fans. Identity ADD
   * §3 / LLFR-ADMIN-02.2.
   *
   * @throws AccountAlreadyVerifiedException if already verified (409 ALREADY_VERIFIED)
   */
  public void verifyArtist(Instant now) {
    if (this.verified) {
      throw new AccountAlreadyVerifiedException();
    }
    this.verified = true;
    this.updatedAt = now;
  }

  public AccountId getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getAvatar() {
    return avatar;
  }

  public boolean isArtist() {
    return isArtist;
  }

  public boolean isAdmin() {
    return isAdmin;
  }

  public boolean isVerified() {
    return verified;
  }

  public AccountStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Credential getCredential() {
    return credential;
  }
}
