package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

/** Read model for the collaborator accept page (WU-CAT-9). */
public record SplitInviteView(String status, String artistName, String releaseTitle,
    List<TrackShareView> tracks) {
  public record TrackShareView(String trackTitle, String role, int percent) {}
}
