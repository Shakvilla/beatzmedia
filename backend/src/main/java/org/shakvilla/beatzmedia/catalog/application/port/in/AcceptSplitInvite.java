package org.shakvilla.beatzmedia.catalog.application.port.in;

/** Input port: a logged-in collaborator accepts their split invite. WU-CAT-9. */
public interface AcceptSplitInvite {
  void accept(String token, String accountId);
}
