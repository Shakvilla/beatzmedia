package org.shakvilla.beatzmedia.studio.application.port.in;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.ShowId;

/** Edits a creator-owned podcast show and refreshes its published fan catalogue entry. */
public interface UpdatePodcastShow {
  PodcastShowView update(ArtistId artist, ShowId id, UpdateShowCommand command);

  record UpdateShowCommand(String title, String category, String description,
      String originCountry) {}
}
