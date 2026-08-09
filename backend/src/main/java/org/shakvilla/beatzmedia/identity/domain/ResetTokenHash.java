package org.shakvilla.beatzmedia.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes password-reset tokens for storage and lookup.
 *
 * <p>Only the hash is ever persisted — the plaintext token exists just long enough to be emailed,
 * then is discarded. Issuing and redeeming must therefore agree byte-for-byte on the algorithm, so
 * it lives in one place rather than being written out at both ends.
 */
public final class ResetTokenHash {

  private ResetTokenHash() {}

  /** SHA-256 of {@code token}, lower-case hex. */
  public static String of(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is guaranteed available on every JVM per the platform spec.
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
