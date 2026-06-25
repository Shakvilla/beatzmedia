package org.shakvilla.beatzmedia.identity.fakes;

import org.shakvilla.beatzmedia.identity.application.port.out.CredentialHasher;

/**
 * Deterministic fake for {@link CredentialHasher}. Prefixes the raw password with "HASHED:" to
 * make verification trivial without real Argon2. NOT for production use.
 */
public class FakeCredentialHasher implements CredentialHasher {

  @Override
  public String hash(String rawPassword) {
    return "HASHED:" + rawPassword;
  }

  @Override
  public boolean verify(String rawPassword, String encodedHash) {
    return encodedHash.equals("HASHED:" + rawPassword);
  }
}
