package org.shakvilla.beatzmedia.podcasts.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;

/**
 * Issues a signed, time-boxed audio delivery URL for an episode. The sole INV-3 enforcement point
 * for podcast episode streaming: owned/free → full rendition; for-sale (premium/early-access) +
 * NOT owned → the 30s preview rendition ONLY — never selectable by the client. Mirrors playback's
 * {@code GetStreamUrl} (WU-PLY-1). LLFR-PODCAST-01.3. ADD §4.1 / §8.
 */
public interface GetEpisodeStreamUrl {

  StreamUrlResult getStreamUrl(EpisodeId episode, Optional<AccountId> caller);
}
