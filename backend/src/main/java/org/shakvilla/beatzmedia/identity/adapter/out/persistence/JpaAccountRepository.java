package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AdminMember;
import org.shakvilla.beatzmedia.identity.domain.AdminRole;
import org.shakvilla.beatzmedia.identity.domain.FanSettings;
import org.shakvilla.beatzmedia.identity.domain.PasswordResetToken;
import org.shakvilla.beatzmedia.identity.domain.SocialIdentity;
import org.shakvilla.beatzmedia.identity.domain.SocialProvider;

/**
 * JPA/Panache-style implementation of {@link AccountRepository}. Persists the account aggregate
 * across {@code account}, {@code credential}, and {@code admin_member} tables. Domain types carry
 * no ORM annotations; this adapter owns all EntityManager calls. Transaction boundary = the
 * use-case service. Identity ADD §5.2 / conventions §6.
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

  // --- WU-IDN-2: social login + password reset methods ---

  @Override
  public Optional<Account> findBySocialIdentity(SocialProvider provider, String providerUid) {
    return em.createQuery(
            "SELECT s FROM SocialIdentityEntity s WHERE s.provider = :provider "
                + "AND s.providerUid = :providerUid",
            SocialIdentityEntity.class)
        .setParameter("provider", provider.wireValue())
        .setParameter("providerUid", providerUid)
        .getResultStream()
        .findFirst()
        .flatMap(link -> findById(new AccountId(link.accountId)));
  }

  @Override
  public SocialIdentity saveSocialIdentity(SocialIdentity identity) {
    SocialIdentityEntity entity = AccountMapper.toSocialIdentityEntity(identity);
    em.merge(entity);
    return identity;
  }

  @Override
  public Optional<PasswordResetToken> findResetTokenByHash(String tokenHash) {
    PasswordResetTokenEntity entity = em.find(PasswordResetTokenEntity.class, tokenHash);
    return Optional.ofNullable(entity).map(AccountMapper::toDomain);
  }

  @Override
  public PasswordResetToken saveResetToken(PasswordResetToken token) {
    PasswordResetTokenEntity entity = AccountMapper.toResetTokenEntity(token);
    em.merge(entity);
    return token;
  }

  @Override
  public void markResetTokenUsed(String tokenHash) {
    em.createQuery(
            "UPDATE PasswordResetTokenEntity t SET t.used = true WHERE t.tokenHash = :tokenHash")
        .setParameter("tokenHash", tokenHash)
        .executeUpdate();
  }

  // --- WU-IDN-3: fan-settings methods ---

  @Override
  public Optional<FanSettings> findSettings(AccountId id) {
    FanSettingsEntity entity = em.find(FanSettingsEntity.class, id.value());
    return Optional.ofNullable(entity).map(FanSettingsMapper::toDomain);
  }

  @Override
  public FanSettings saveSettings(FanSettings settings) {
    FanSettingsEntity entity = FanSettingsMapper.toEntity(settings);
    em.merge(entity);
    return settings;
  }

  // --- WU-IDN-4: admin-team methods ---

  @Override
  public List<AdminMemberProjection> findAllAdminMembers() {
    return em.createQuery(
            "SELECT m, a FROM AdminMemberEntity m JOIN AccountEntity a ON a.id = m.accountId "
                + "ORDER BY m.lastActiveAt DESC NULLS LAST",
            Object[].class)
        .getResultStream()
        .map(row -> {
          AdminMemberEntity m = (AdminMemberEntity) row[0];
          AccountEntity a = (AccountEntity) row[1];
          return toProjection(m, a);
        })
        .toList();
  }

  @Override
  public Optional<AdminMemberProjection> findAdminMember(String adminMemberId) {
    return em.createQuery(
            "SELECT m, a FROM AdminMemberEntity m JOIN AccountEntity a ON a.id = m.accountId "
                + "WHERE m.id = :id",
            Object[].class)
        .setParameter("id", adminMemberId)
        .getResultStream()
        .map(row -> toProjection((AdminMemberEntity) row[0], (AccountEntity) row[1]))
        .findFirst();
  }

  @Override
  public long countAdminsWithRole(AdminRole role) {
    return em.createQuery(
            "SELECT COUNT(m) FROM AdminMemberEntity m WHERE m.role = :role",
            Long.class)
        .setParameter("role", role.wireValue())
        .getSingleResult();
  }

  @Override
  public AdminMember saveAdminMember(AdminMember member) {
    // Persist the admin_member row
    AdminMemberEntity entity = new AdminMemberEntity();
    entity.id = member.getId();
    entity.accountId = member.getAccountId().value();
    entity.role = member.getRole().wireValue();
    entity.lastActiveAt = member.getLastActiveAt();
    em.persist(entity);

    // Flip account.is_admin = true
    em.createQuery(
            "UPDATE AccountEntity a SET a.isAdmin = true WHERE a.id = :accountId")
        .setParameter("accountId", member.getAccountId().value())
        .executeUpdate();

    return member;
  }

  @Override
  public AdminMember updateAdminMember(AdminMember member) {
    AdminMemberEntity entity = em.find(AdminMemberEntity.class, member.getId());
    if (entity == null) {
      throw new IllegalArgumentException("AdminMember not found: " + member.getId());
    }
    entity.role = member.getRole().wireValue();
    entity.lastActiveAt = member.getLastActiveAt();
    em.merge(entity);
    return member;
  }

  @Override
  public void deleteAdminMember(String adminMemberId) {
    AdminMemberEntity entity = em.find(AdminMemberEntity.class, adminMemberId);
    if (entity == null) {
      return; // idempotent
    }
    String accountId = entity.accountId;
    em.remove(entity);

    // Flip account.is_admin = false
    em.createQuery(
            "UPDATE AccountEntity a SET a.isAdmin = false WHERE a.id = :accountId")
        .setParameter("accountId", accountId)
        .executeUpdate();
  }

  // --- Private helpers ---

  private static AdminMemberProjection toProjection(AdminMemberEntity m, AccountEntity a) {
    AdminRole role = AdminRole.fromWireValue(m.role);
    return new AdminMemberProjection(
        m.id,
        new AccountId(a.id),
        a.name,
        a.email,
        role,
        m.lastActiveAt);
  }
}
