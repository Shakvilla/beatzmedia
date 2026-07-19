package org.shakvilla.beatzmedia.catalog.domain;

/**
 * Revenue split allocation for a track. Sum of split percents on a track must be ≤ 100 (INV-12).
 * {@code accountId} links a collaborator's confirmed split to their BeatzClik account (WU-CAT-9);
 * null while pending/declined or before accept. Domain value object; no framework imports.
 */
public record SplitEntry(
    String id,
    String trackId,
    String name,
    String email,
    String role,
    int percent,
    SplitConfirmation confirmation,
    String accountId) {

  /** Legacy 7-arg form — no linked account yet (accountId == null). */
  public SplitEntry(String id, String trackId, String name, String email, String role,
      int percent, SplitConfirmation confirmation) {
    this(id, trackId, name, email, role, percent, confirmation, null);
  }
}
