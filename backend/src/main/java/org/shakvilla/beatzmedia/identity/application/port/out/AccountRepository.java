package org.shakvilla.beatzmedia.identity.application.port.out;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AdminMember;
import org.shakvilla.beatzmedia.identity.domain.AdminRole;

/**
 * Output port for account persistence. WU-IDN-1 declares only the methods it needs; later WUs
 * (WU-IDN-2, WU-IDN-3, WU-IDN-4) extend this port. Identity ADD §4.2.
 */
public interface AccountRepository {

  /** Finds an account by its id, including its credential if present. */
  Optional<Account> findById(AccountId id);

  /** Finds an account by email (case-insensitive). Returns the credential inline. */
  Optional<Account> findByEmail(String email);

  /** Returns true if an account with this email already exists (case-insensitive). */
  boolean existsByEmail(String email);

  /**
   * Persists a new or updated account aggregate (account row + credential row). Returns the saved
   * aggregate.
   */
  Account save(Account account);

  // --- WU-IDN-4 additions (admin team) ---

  /**
   * Returns all admin members with their linked account name + email for list display. Ordered by
   * last_active_at DESC (most recently active first).
   */
  List<AdminMemberProjection> findAllAdminMembers();

  /**
   * Finds a single admin member by its admin_member PK id, joined with the linked account row.
   * Returns empty if no such admin member exists.
   */
  Optional<AdminMemberProjection> findAdminMember(String adminMemberId);

  /**
   * Returns the number of admin members currently holding the given role. Used for the last-
   * super-admin guard before demoting or removing.
   */
  long countAdminsWithRole(AdminRole role);

  /** Persists a new {@link AdminMember} and flips the linked account's {@code is_admin=true}. */
  AdminMember saveAdminMember(AdminMember member);

  /**
   * Persists an updated {@link AdminMember} (role change or last-active-at update). Does NOT flip
   * the account flag (already set on creation).
   */
  AdminMember updateAdminMember(AdminMember member);

  /**
   * Removes the admin_member row and flips the linked account's {@code is_admin=false}. Caller
   * must have already verified the last-super-admin guard.
   */
  void deleteAdminMember(String adminMemberId);

  /**
   * Projection carrying the admin_member fields together with the linked account's name + email,
   * needed for list and single-member reads without loading the full Account aggregate.
   */
  record AdminMemberProjection(
      String id,
      AccountId accountId,
      String name,
      String email,
      AdminRole role,
      java.time.Instant lastActiveAt) {}
}
