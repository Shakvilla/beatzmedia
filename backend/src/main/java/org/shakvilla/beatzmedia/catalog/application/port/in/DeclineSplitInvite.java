package org.shakvilla.beatzmedia.catalog.application.port.in;

/** Input port: a collaborator declines their split invite (no account required). WU-CAT-9. */
public interface DeclineSplitInvite {
  void decline(String token);
}
