package org.shakvilla.beatzmedia.audit.application.port.out;

import java.util.Optional;

/**
 * Output port: resolves an actor id to the display name stored alongside an audit entry.
 *
 * <p><strong>Why the name is stored, not joined.</strong> An audit row records who did something at
 * the moment they did it. A rename, or a deleted account, must not rewrite or erase history — so
 * the name is denormalised onto the row at write time rather than joined at read time.
 *
 * <p><strong>Why a port.</strong> Audit owns no account data and must not read another module's
 * tables; the implementation calls identity's input port. Keeping that behind a port also means the
 * writer degrades to a null name rather than failing when the actor is not a user account at all —
 * a scheduler, a webhook, or a manual database bootstrap.
 */
public interface ActorDirectory {

  /**
   * The actor's display name, or empty when the id belongs to no account.
   *
   * <p>Implementations must not throw for an unknown id: an audit write happens inside the
   * transaction of the mutation it records, and losing the mutation because its actor could not be
   * named would be a far worse outcome than an unnamed row.
   */
  Optional<String> displayName(String actorId);
}
