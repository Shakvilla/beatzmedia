package org.shakvilla.beatzmedia.identity.domain;

import java.time.Instant;

/**
 * Admin-team member entity. Orthogonal to fan/artist role; presence implies {@code
 * account.isAdmin=true}. Framework-free; no ORM annotations. Identity ADD §3.
 *
 * <p>Invariant: the system must never drop below one {@link AdminRole#SUPER_ADMIN} member. Guard is
 * enforced by the use-case services ({@code InviteAdminService}, {@code ChangeAdminRoleService},
 * {@code RemoveAdminService}) before mutating, not here, so domain remains passive value-carrying.
 */
public final class AdminMember {

  private final String id;
  private final AccountId accountId;
  private AdminRole role;
  private Instant lastActiveAt;

  public AdminMember(String id, AccountId accountId, AdminRole role, Instant lastActiveAt) {
    this.id = id;
    this.accountId = accountId;
    this.role = role;
    this.lastActiveAt = lastActiveAt;
  }

  /** Changes the role of this member. Caller must enforce last-super-admin guard before calling. */
  public void changeRole(AdminRole newRole) {
    this.role = newRole;
  }

  /** Records that this member was active at {@code now}. */
  public void recordActivity(Instant now) {
    this.lastActiveAt = now;
  }

  public String getId() {
    return id;
  }

  public AccountId getAccountId() {
    return accountId;
  }

  public AdminRole getRole() {
    return role;
  }

  public Instant getLastActiveAt() {
    return lastActiveAt;
  }
}
