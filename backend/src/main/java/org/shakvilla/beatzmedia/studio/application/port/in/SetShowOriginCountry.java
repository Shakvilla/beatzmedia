package org.shakvilla.beatzmedia.studio.application.port.in;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.ShowId;

/** Artist-owned country assignment for a podcast show, including existing shows. */
public interface SetShowOriginCountry {

  PodcastShowView set(ArtistId artist, ShowId showId, String originCountry);
}
