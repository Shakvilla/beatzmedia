package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;

/** Encrypts provider credentials before they are persisted. */
@ApplicationScoped
public class ProviderRuntimeConfigCipher {
  private static final String PREFIX = "v1:";
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final SecureRandom RANDOM = new SecureRandom();
  private final byte[] key;

  @Inject
  public ProviderRuntimeConfigCipher(Config config) {
    String encoded = config.getOptionalValue("beatz.provider-config.encryption-key", String.class).orElse("");
    byte[] decoded;
    try {
      decoded = encoded.isBlank() ? new byte[0] : Base64.getDecoder().decode(encoded.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Provider configuration encryption key must be base64", e);
    }
    if (decoded.length != 0 && decoded.length != 32) {
      throw new IllegalStateException("Provider configuration encryption key must decode to 32 bytes");
    }
    this.key = decoded;
  }

  public boolean available() {
    return key.length == 32;
  }

  public String encrypt(String plaintext) {
    if (!available()) throw new IllegalStateException("Provider configuration encryption is unavailable");
    try {
      byte[] nonce = new byte[NONCE_BYTES];
      RANDOM.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      ByteBuffer result = ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted);
      return PREFIX + Base64.getEncoder().encodeToString(result.array());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not encrypt provider configuration", e);
    }
  }

  public String decrypt(String value) {
    if (!available()) throw new IllegalStateException("Provider configuration encryption is unavailable");
    if (value == null || !value.startsWith(PREFIX)) {
      throw new IllegalStateException("Unsupported provider configuration ciphertext version");
    }
    try {
      byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
      if (payload.length <= NONCE_BYTES) throw new IllegalStateException("Invalid provider configuration ciphertext");
      ByteBuffer buffer = ByteBuffer.wrap(payload);
      byte[] nonce = new byte[NONCE_BYTES];
      buffer.get(nonce);
      byte[] encrypted = new byte[buffer.remaining()];
      buffer.get(encrypted);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("Could not decrypt provider configuration", e);
    }
  }
}
