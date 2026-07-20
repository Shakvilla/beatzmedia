package org.shakvilla.beatzmedia.catalog.application.port.in;

/** Input port: read a split invite by its opaque token (public accept page). WU-CAT-9. */
public interface GetSplitInvite {
  SplitInviteView getByToken(String token);
}
