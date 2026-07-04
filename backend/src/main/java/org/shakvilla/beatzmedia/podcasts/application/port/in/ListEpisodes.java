package org.shakvilla.beatzmedia.podcasts.application.port.in;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;

/**
 * Lists a show's episodes decorated with per-caller ownership/early-access state.
 * LLFR-PODCAST-01.3. ADD §4.1.
 */
public interface ListEpisodes {

  List<PodcastEpisodeView> list(PodcastId id, Optional<AccountId> caller);
}
