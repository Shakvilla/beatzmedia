package org.shakvilla.beatzmedia.admin.domain;

/**
 * Who authored a {@link SupportMessage}: the requesting user, or the support agent (admin).
 * Wire values are lowercase, matching {@code SupportMessage.from} in {@code
 * Frontend/src/lib/admin-data.ts}. Admin ADD §3.
 */
public enum MessageFrom {
  USER("user"),
  AGENT("agent");

  private final String wireValue;

  MessageFrom(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }
}
