package org.shakvilla.beatzmedia.identity.application.port.out;

import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Output port for account persistence. WU-IDN-1 declares only the methods it needs; later WUs
 * (WU-IDN-2, WU-IDN-3, WU-IDN-4) will extend this port. Identity ADD §4.2.
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
}
