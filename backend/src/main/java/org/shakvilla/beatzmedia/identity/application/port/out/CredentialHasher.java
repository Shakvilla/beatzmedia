package org.shakvilla.beatzmedia.identity.application.port.out;

/**
 * Output port for password hashing. The only permitted algorithm is Argon2id; adapters must never
 * fall back to a weaker scheme. Identity ADD §4.2.
 */
public interface CredentialHasher {

  /** Hashes the raw password using Argon2id and returns the encoded hash string. */
  String hash(String rawPassword);

  /** Returns true if {@code rawPassword} matches the stored {@code encodedHash}. */
  boolean verify(String rawPassword, String encodedHash);
}
