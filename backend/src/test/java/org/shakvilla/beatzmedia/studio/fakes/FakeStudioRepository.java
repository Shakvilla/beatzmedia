package org.shakvilla.beatzmedia.studio.fakes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.StudioProfile;

/** In-memory fake for {@link StudioRepository} used in unit tests. */
public class FakeStudioRepository implements StudioRepository {

  private final Map<String, StudioProfile> profiles = new LinkedHashMap<>();

  public FakeStudioRepository withProfile(StudioProfile profile) {
    profiles.put(profile.artistId().value(), profile);
    return this;
  }

  @Override
  public Optional<StudioProfile> findProfile(ArtistId artist) {
    return Optional.ofNullable(profiles.get(artist.value()));
  }

  @Override
  public boolean usernameTaken(String username, ArtistId excluding) {
    return profiles.values().stream()
        .anyMatch(
            p -> !p.artistId().equals(excluding)
                && p.username() != null
                && p.username().equalsIgnoreCase(username));
  }

  @Override
  public StudioProfile saveProfile(StudioProfile profile) {
    profiles.put(profile.artistId().value(), profile);
    return profile;
  }
}
