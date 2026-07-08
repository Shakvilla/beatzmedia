package org.shakvilla.beatzmedia.studio.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.domain.Genre;
import org.shakvilla.beatzmedia.studio.application.port.in.SaveStudioProfile;
import org.shakvilla.beatzmedia.studio.application.port.in.SaveStudioProfileCommand;
import org.shakvilla.beatzmedia.studio.application.port.in.StudioProfileView;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.InvalidGenreException;
import org.shakvilla.beatzmedia.studio.domain.StudioProfile;
import org.shakvilla.beatzmedia.studio.domain.UsernameTakenException;

/**
 * Application service for LLFR-STUDIO-01.1 (studio profile save). {@code PUT /studio/profile} is a
 * natural upsert (no {@code Idempotency-Key}): the same request replayed twice yields the same
 * saved state. Studio ADD §4.1 / §9:
 *
 * <ul>
 *   <li>{@code genres ⊆ Genre} — unknown genre → 422 {@code INVALID_GENRE}.
 *   <li>{@code username} globally unique (case-insensitive) — checked here via {@code
 *       usernameTaken(username, excludingSelf)}; the DB unique index on {@code lower(username)}
 *       backstops the race. Either path surfaces as 409 {@code USERNAME_TAKEN} (field {@code
 *       username}) — the pre-check is the primary path (mirrors {@code RegisterFanService}'s
 *       {@code EMAIL_TAKEN} pre-check pattern).
 * </ul>
 */
@ApplicationScoped
public class SaveStudioProfileService implements SaveStudioProfile {

  private final StudioRepository repository;
  private final Clock clock;

  @Inject
  public SaveStudioProfileService(StudioRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  @Transactional
  public StudioProfileView save(ArtistId artist, SaveStudioProfileCommand cmd) {
    validateGenres(cmd.genres());

    String username = cmd.username() == null ? "" : cmd.username().trim();
    if (!username.isBlank() && repository.usernameTaken(username, artist)) {
      throw new UsernameTakenException(username);
    }

    SaveStudioProfileCommand normalized = new SaveStudioProfileCommand(
        cmd.displayName(),
        username,
        cmd.hometown(),
        cmd.genres(),
        cmd.bio(),
        cmd.avatar(),
        cmd.banner(),
        cmd.links(),
        cmd.shows(),
        cmd.featuredTrackId(),
        cmd.bookingEmail(),
        cmd.pressAssets());

    StudioProfile profile = StudioProfileMapper.toDomain(artist, normalized, clock.now());
    StudioProfile saved = repository.saveProfile(profile);
    return StudioProfileMapper.toView(saved);
  }

  private void validateGenres(List<String> genres) {
    if (genres == null) {
      return;
    }
    for (String genre : genres) {
      if (!Genre.isValid(genre)) {
        throw new InvalidGenreException(genre);
      }
    }
  }
}
