package org.shakvilla.beatzmedia.identity.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;

import org.shakvilla.beatzmedia.identity.application.port.out.CredentialHasher;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import de.mkammerer.argon2.Argon2Factory.Argon2Types;

/**
 * Argon2id password hasher. Uses the argon2-jvm library (WU-IDN-1 pom addition). Parameters are
 * conservative for a dev/test context; tune via config for production. Identity ADD §5.2.
 */
@ApplicationScoped
public class Argon2CredentialHasher implements CredentialHasher {

  /** Argon2id instance — thread-safe and stateless. */
  private static final Argon2 ARGON2 = Argon2Factory.create(Argon2Types.ARGON2id);

  /** Memory cost in kibibytes (64 MiB). */
  private static final int MEMORY_KIB = 65_536;

  /** Number of iterations. */
  private static final int ITERATIONS = 3;

  /** Degree of parallelism. */
  private static final int PARALLELISM = 1;

  @Override
  public String hash(String rawPassword) {
    return ARGON2.hash(ITERATIONS, MEMORY_KIB, PARALLELISM, rawPassword.toCharArray());
  }

  @Override
  public boolean verify(String rawPassword, String encodedHash) {
    return ARGON2.verify(encodedHash, rawPassword.toCharArray());
  }
}
