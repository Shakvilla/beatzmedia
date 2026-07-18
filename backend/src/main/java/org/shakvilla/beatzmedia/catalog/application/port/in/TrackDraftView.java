package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;

/**
 * View of a single track within a {@link StudioReleaseDetailView}'s ordered track list, including
 * its collaborator revenue splits (WU-CAT-6). Catalog ADD §4.1 / WU-CAT-5 / WU-CAT-6.
 */
public record TrackDraftView(
    String trackId, String title, int duration, String status, int position, MoneyView price,
    List<SplitView> splits) {

  /** A single collaborator's royalty split of the track (creator is implicit, not listed). */
  public record SplitView(
      String id, String name, String email, String role, int percent, String confirmation) {}
}
