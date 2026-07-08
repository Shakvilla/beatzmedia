package org.shakvilla.beatzmedia.studio.application.port.out;

import java.util.Optional;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.StudioProfile;

/**
 * Output port for {@code studio_profile} persistence — WU-STU-1 scope only. The full ADD §4.2
 * {@code StudioRepository} also declares settings/show/episode methods over tables that don't exist
 * yet in this WU ({@code studio_settings}, {@code studio_podcast_show}, {@code studio_episode});
 * those are added when WU-STU-2/3/4 land their own tables, rather than stubbed here ahead of scope.
 * Studio ADD §4.2.
 */
public interface StudioRepository {

  Optional<StudioProfile> findProfile(ArtistId artist);

  /** {@code true} if {@code username} (case-insensitive) is held by an artist other than {@code excluding}. */
  boolean usernameTaken(String username, ArtistId excluding);

  StudioProfile saveProfile(StudioProfile profile);
}
