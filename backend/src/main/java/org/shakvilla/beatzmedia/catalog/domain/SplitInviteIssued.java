package org.shakvilla.beatzmedia.catalog.domain;

import java.util.List;

/**
 * Domain event fired by catalog when a collaborator's split invite is issued on release submit
 * (WU-CAT-9). Observed by the notifications module, which emails {@code acceptUrl} to {@code email}.
 * Framework-free; the sole cross-module channel (hexagonal rule — no table reads).
 */
public record SplitInviteIssued(
    String email,
    String acceptUrl,
    String artistName,
    String releaseTitle,
    List<TrackShare> trackShares) {

  /** One collaborator share line for the invite email/accept page. */
  public record TrackShare(String trackTitle, String role, int percent) {}
}
