package org.shakvilla.beatzmedia.identity.domain;

/**
 * Links one third-party provider identity to an {@link Account}. One row per
 * {@code (provider, providerUid)} pair; unique across the table. Identity ADD §3.
 */
public final class SocialIdentity {

  private final String id;
  private final AccountId accountId;
  private final SocialProvider provider;
  private final String providerUid;

  public SocialIdentity(String id, AccountId accountId, SocialProvider provider, String providerUid) {
    this.id = id;
    this.accountId = accountId;
    this.provider = provider;
    this.providerUid = providerUid;
  }

  public String getId() {
    return id;
  }

  public AccountId getAccountId() {
    return accountId;
  }

  public SocialProvider getProvider() {
    return provider;
  }

  public String getProviderUid() {
    return providerUid;
  }
}
