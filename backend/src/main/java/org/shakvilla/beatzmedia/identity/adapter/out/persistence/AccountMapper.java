package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AccountStatus;
import org.shakvilla.beatzmedia.identity.domain.Credential;

/**
 * Stateless mapper between {@link Account} domain aggregate and its JPA entities. No framework
 * imports in the domain; all ORM coupling lives here. Identity ADD §5.2.
 */
final class AccountMapper {

  private AccountMapper() {}

  static AccountEntity toAccountEntity(Account account) {
    AccountEntity entity = new AccountEntity();
    entity.id = account.getId().value();
    entity.name = account.getName();
    entity.email = account.getEmail();
    entity.avatar = account.getAvatar();
    entity.isArtist = account.isArtist();
    entity.isAdmin = account.isAdmin();
    entity.status = account.getStatus().name();
    entity.createdAt = account.getCreatedAt();
    entity.updatedAt = account.getUpdatedAt();
    return entity;
  }

  static CredentialEntity toCredentialEntity(Credential credential) {
    CredentialEntity entity = new CredentialEntity();
    entity.accountId = credential.getAccountId().value();
    entity.passwordHash = credential.getPasswordHash();
    entity.algo = credential.getAlgo();
    return entity;
  }

  static Account toDomain(AccountEntity accountEntity, CredentialEntity credentialEntity) {
    Credential credential = null;
    if (credentialEntity != null) {
      credential = new Credential(
          new AccountId(credentialEntity.accountId),
          credentialEntity.passwordHash,
          credentialEntity.algo);
    }
    return Account.reconstitute(
        new AccountId(accountEntity.id),
        accountEntity.name,
        accountEntity.email,
        accountEntity.avatar,
        accountEntity.isArtist,
        accountEntity.isAdmin,
        AccountStatus.valueOf(accountEntity.status),
        accountEntity.createdAt,
        accountEntity.updatedAt,
        credential);
  }
}
