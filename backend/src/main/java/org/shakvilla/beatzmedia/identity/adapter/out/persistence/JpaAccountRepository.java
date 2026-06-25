package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * JPA/Panache-style implementation of {@link AccountRepository}. Persists the account aggregate
 * across {@code account} and {@code credential} tables. Domain types carry no ORM annotations; this
 * adapter owns all EntityManager calls. Transaction boundary = the use-case service. Identity ADD
 * §5.2 / conventions §6.
 */
@ApplicationScoped
public class JpaAccountRepository implements AccountRepository {

  private final EntityManager em;

  @Inject
  public JpaAccountRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public Optional<Account> findById(AccountId id) {
    AccountEntity accountEntity = em.find(AccountEntity.class, id.value());
    if (accountEntity == null) {
      return Optional.empty();
    }
    CredentialEntity credentialEntity = em.find(CredentialEntity.class, id.value());
    return Optional.of(AccountMapper.toDomain(accountEntity, credentialEntity));
  }

  @Override
  public Optional<Account> findByEmail(String email) {
    return em.createQuery(
            "SELECT a FROM AccountEntity a WHERE lower(a.email) = lower(:email)",
            AccountEntity.class)
        .setParameter("email", email)
        .getResultStream()
        .findFirst()
        .map(accountEntity -> {
          CredentialEntity credentialEntity = em.find(CredentialEntity.class, accountEntity.id);
          return AccountMapper.toDomain(accountEntity, credentialEntity);
        });
  }

  @Override
  public boolean existsByEmail(String email) {
    Long count = em.createQuery(
            "SELECT COUNT(a) FROM AccountEntity a WHERE lower(a.email) = lower(:email)",
            Long.class)
        .setParameter("email", email)
        .getSingleResult();
    return count > 0;
  }

  @Override
  public Account save(Account account) {
    // Upsert account row
    AccountEntity accountEntity = AccountMapper.toAccountEntity(account);
    em.merge(accountEntity);

    // Upsert credential row if present
    if (account.getCredential() != null) {
      CredentialEntity credentialEntity = AccountMapper.toCredentialEntity(account.getCredential());
      em.merge(credentialEntity);
    }

    return account;
  }
}
