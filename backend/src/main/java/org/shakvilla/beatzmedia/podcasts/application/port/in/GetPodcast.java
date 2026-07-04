package org.shakvilla.beatzmedia.podcasts.application.port.in;

import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;

/** Returns a single show. LLFR-PODCAST-01.2. ADD §4.1. */
public interface GetPodcast {

  PodcastView get(PodcastId id);
}
