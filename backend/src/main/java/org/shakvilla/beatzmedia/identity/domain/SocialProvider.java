package org.shakvilla.beatzmedia.identity.domain;

/**
 * Third-party identity providers supported for social login. Serialized as lower-case strings on
 * the wire and persisted in the {@code social_identity.provider} CHECK constraint. Identity ADD §3
 * / API-CONTRACT §2.
 */
public enum SocialProvider {
  FACEBOOK("facebook"),
  GOOGLE("google"),
  TWITTER("twitter");

  private final String wireValue;

  SocialProvider(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  /**
   * Parses a lower-case wire value (e.g. {@code "google"}) to a {@link SocialProvider}. Throws
   * {@link SocialTokenInvalidException} if unrecognised — an unknown provider is treated the same
   * as an invalid token so the resource never reveals provider implementation detail.
   */
  public static SocialProvider fromWireValue(String value) {
    if (value == null) {
      throw new SocialTokenInvalidException();
    }
    for (SocialProvider p : values()) {
      if (p.wireValue.equalsIgnoreCase(value)) {
        return p;
      }
    }
    throw new SocialTokenInvalidException();
  }
}
