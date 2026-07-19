package org.shakvilla.beatzmedia.catalog.application.port.in;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;

/** Input port: the owning artist re-sends invites for all still-pending splits. WU-CAT-9. */
public interface ResendSplitInvites {
  void resend(ReleaseId releaseId, ArtistId requestingArtist);
}
