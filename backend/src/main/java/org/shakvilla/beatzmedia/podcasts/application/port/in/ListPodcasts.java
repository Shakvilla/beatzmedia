package org.shakvilla.beatzmedia.podcasts.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastCategory;

/** Lists podcast shows, optionally filtered by category. LLFR-PODCAST-01.1. ADD §4.1. */
public interface ListPodcasts {

  Page<PodcastView> list(Optional<PodcastCategory> category, PageRequest page);
}
