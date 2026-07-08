package org.shakvilla.beatzmedia.studio.domain;

import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when {@code PUT /studio/profile} attempts to save a {@code username} already held by a
 * different artist. Maps to HTTP 409 {@code USERNAME_TAKEN}. Checked by the application service
 * ({@code usernameTaken(username, excludingSelf)}) and backstopped by the DB unique index on {@code
 * lower(username)} (Studio ADD §9).
 */
public class UsernameTakenException extends ConflictException {

  public UsernameTakenException(String username) {
    super(ErrorCode.USERNAME_TAKEN, "Username already taken: " + username, "username");
  }
}
