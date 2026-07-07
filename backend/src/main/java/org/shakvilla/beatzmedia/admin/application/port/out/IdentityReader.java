package org.shakvilla.beatzmedia.admin.application.port.out;

import java.util.Optional;

/**
 * Output port resolving an opaque {@code requesterRef} (an account id) to a display name for the
 * support-ticket views. Implemented by an adapter that reads the {@code identity} module's {@code
 * account} table in-process (same JVM, no cross-module FK) — {@code admin} never queries identity
 * tables directly from application code. Admin ADD §4.3 (identity reader).
 */
public interface IdentityReader {

  /** Resolves the display name for an account id, or empty if the account no longer exists. */
  Optional<String> displayNameOf(String accountId);
}
