package org.shakvilla.beatzmedia.identity.fakes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
 * In-memory fake for {@link AccountRepository}. Returns deterministic data for unit tests without
 * touching a database. Testing-strategy §2.
 */
public class FakeAccountRepository implements AccountRepository {

  private final List<Account> store = new ArrayList<>();
  private final List<AdminMember> adminStore = new ArrayList<>();
  private final List<FanSettings> settingsStore = new ArrayList<>();
  private final List<SocialIdentity> socialIdentityStore = new ArrayList<>();
  private final List<PasswordResetToken> resetTokenStore = new ArrayList<>();

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

  // --- WU-IDN-2 social login + password reset methods ---

  @Override
  public Optional<Account> findBySocialIdentity(SocialProvider provider, String providerUid) {
    return socialIdentityStore.stream()
        .filter(s -> s.getProvider() == provider && s.getProviderUid().equals(providerUid))
        .findFirst()
        .flatMap(s -> findById(s.getAccountId()));
  }

  @Override
  public SocialIdentity saveSocialIdentity(SocialIdentity identity) {
    socialIdentityStore.removeIf(s -> s.getId().equals(identity.getId()));
    socialIdentityStore.add(identity);
    return identity;
  }

  @Override
  public Optional<PasswordResetToken> findResetTokenByHash(String tokenHash) {
    return resetTokenStore.stream()
        .filter(t -> t.tokenHash().equals(tokenHash))
        .findFirst();
  }

  @Override
  public PasswordResetToken saveResetToken(PasswordResetToken token) {
    resetTokenStore.removeIf(t -> t.tokenHash().equals(token.tokenHash()));
    resetTokenStore.add(token);
    return token;
  }

  @Override
  public void markResetTokenUsed(String tokenHash) {
    resetTokenStore.stream()
        .filter(t -> t.tokenHash().equals(tokenHash))
        .findFirst()
        .ifPresent(PasswordResetToken::markUsed);
  }

  /** Returns all stored social identities (for test assertions). */
  public List<SocialIdentity> allSocialIdentities() {
    return List.copyOf(socialIdentityStore);
  }

  /** Returns all stored password reset tokens (for test assertions). */
  public List<PasswordResetToken> allResetTokens() {
    return List.copyOf(resetTokenStore);
  }

  // --- WU-IDN-3 fan-settings methods ---

  @Override
  public Optional<FanSettings> findSettings(AccountId id) {
    return settingsStore.stream()
        .filter(s -> s.getAccountId().equals(id))
        .findFirst();
  }

  @Override
  public FanSettings saveSettings(FanSettings settings) {
    settingsStore.removeIf(s -> s.getAccountId().equals(settings.getAccountId()));
    settingsStore.add(settings);
    return settings;
  }

  // --- WU-IDN-4 admin-team methods ---

  @Override
  public List<AdminMemberProjection> findAllAdminMembers() {
    return adminStore.stream()
        .sorted(Comparator.comparing(
            m -> m.getLastActiveAt() == null ? java.time.Instant.EPOCH : m.getLastActiveAt(),
            Comparator.reverseOrder()))
        .map(m -> {
          Account account = findById(m.getAccountId()).orElse(null);
          String name = account != null ? account.getName() : m.getAccountId().value();
          String email = account != null ? account.getEmail() : m.getAccountId().value();
          return new AdminMemberProjection(m.getId(), m.getAccountId(), name, email, m.getRole(),
              m.getLastActiveAt());
        })
        .toList();
  }

  @Override
  public Optional<AdminMemberProjection> findAdminMember(String adminMemberId) {
    return adminStore.stream()
        .filter(m -> m.getId().equals(adminMemberId))
        .findFirst()
        .map(m -> {
          Account account = findById(m.getAccountId()).orElse(null);
          String name = account != null ? account.getName() : m.getAccountId().value();
          String email = account != null ? account.getEmail() : m.getAccountId().value();
          return new AdminMemberProjection(m.getId(), m.getAccountId(), name, email, m.getRole(),
              m.getLastActiveAt());
        });
  }

  @Override
  public long countAdminsWithRole(AdminRole role) {
    return adminStore.stream().filter(m -> m.getRole() == role).count();
  }

  @Override
  public AdminMember saveAdminMember(AdminMember member) {
    adminStore.removeIf(m -> m.getId().equals(member.getId()));
    adminStore.add(member);
    return member;
  }

  @Override
  public AdminMember updateAdminMember(AdminMember member) {
    adminStore.removeIf(m -> m.getId().equals(member.getId()));
    adminStore.add(member);
    return member;
  }

  @Override
  public void deleteAdminMember(String adminMemberId) {
    adminStore.removeIf(m -> m.getId().equals(adminMemberId));
  }

  /** Returns all stored accounts (for test assertions). */
  public List<Account> all() {
    return List.copyOf(store);
  }

  /** Seeds an account directly into the fake store. */
  public void seed(Account account) {
    store.add(account);
  }

  /** Seeds an admin member directly into the fake store. */
  public void seedAdminMember(AdminMember member) {
    adminStore.add(member);
  }

  /** Returns all stored admin members (for test assertions). */
  public List<AdminMember> allAdminMembers() {
    return List.copyOf(adminStore);
  }
}
