package org.shakvilla.beatzmedia.identity.application.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;

/** Uses a server-held HMAC key so stored six-digit verification codes resist offline guessing. */
@ApplicationScoped
public class PhoneVerificationCodeHasher {
  private final byte[] key;

  @Inject
  public PhoneVerificationCodeHasher(Config config) {
    this(config.getOptionalValue("beatz.phone-verification.hmac-key", String.class).orElse(""));
  }

  public PhoneVerificationCodeHasher(String encoded) {
    try {
      this.key = encoded.isBlank() ? new byte[0] : Base64.getDecoder().decode(encoded.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Phone verification HMAC key must be base64", e);
    }
    if (key.length != 0 && key.length < 32) {
      throw new IllegalStateException("Phone verification HMAC key must decode to at least 32 bytes");
    }
  }

  public String hash(String challengeId, String code) {
    if (key.length < 32) throw new IllegalStateException("Phone verification HMAC key is unavailable");
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      byte[] hash = mac.doFinal((challengeId + ":" + code).getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Phone verification code could not be protected", e);
    }
  }

  public boolean matches(String challengeId, String code, String expectedHash) {
    byte[] actual = hash(challengeId, code).getBytes(StandardCharsets.US_ASCII);
    byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
    return java.security.MessageDigest.isEqual(actual, expected);
  }
}
