package org.shakvilla.beatzmedia.identity.fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * In-memory fake for {@link AccountRepository}. Returns deterministic data for unit tests without
 * touching a database. Testing-strategy §2.
 */
public class FakeAccountRepository implements AccountRepository {

  private final List<Account> store = new ArrayList<>();

  @Override
  public Optional<Account> findById(AccountId id) {
    return store.stream().filter(a -> a.getId().equals(id)).findFirst();
  }

  @Override
  public Optional<Account> findByEmail(String email) {
    return store.stream()
        .filter(a -> a.getEmail().equalsIgnoreCase(email))
        .findFirst();
  }

  @Override
  public boolean existsByEmail(String email) {
    return store.stream().anyMatch(a -> a.getEmail().equalsIgnoreCase(email));
  }

  @Override
  public Account save(Account account) {
    store.removeIf(a -> a.getId().equals(account.getId()));
    store.add(account);
    return account;
  }

  /** Returns all stored accounts (for test assertions). */
  public List<Account> all() {
    return List.copyOf(store);
  }

  /** Seeds an account directly into the fake store. */
  public void seed(Account account) {
    store.add(account);
  }
}
