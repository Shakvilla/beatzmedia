package org.shakvilla.beatzmedia.identity.domain;

import java.time.Instant;
import java.util.List;

/**
 * A fan's taste profile, captured at onboarding.
 *
 * <p>Separate from {@link FanSettings}, which holds app preferences (theme, audio quality,
 * notifications). This is what the fan likes, not how they want the app to behave.
 *
 * <p>{@code preferredGenres} stores genre <em>labels</em>, the same way {@code release.genre} does,
 * so an admin renaming a genre repoints these alongside everything else.
 *
 * <p>{@code completedAt} records that onboarding happened. It is deliberately not derived from
 * {@code preferredGenres.size() >= 3}: if an admin later deletes a genre this fan picked, they must
 * not be dragged back through the gate.
 */
public record FanPreferences(
    AccountId accountId, List<String> preferredGenres, Instant completedAt) {

  /** Minimum genres a fan must pick to finish onboarding. */
  public static final int MIN_GENRES = 3;

  public FanPreferences {
    if (accountId == null) {
      throw new IllegalArgumentException("FanPreferences.accountId must not be null");
    }
    preferredGenres = preferredGenres == null ? List.of() : List.copyOf(preferredGenres);
  }

  /** An empty, not-yet-onboarded profile — what a brand-new account starts with. */
  public static FanPreferences empty(AccountId accountId) {
    return new FanPreferences(accountId, List.of(), null);
  }

  public boolean isOnboarded() {
    return completedAt != null;
  }

  public FanPreferences withGenres(List<String> genres) {
    return new FanPreferences(accountId, genres, completedAt);
  }

  public FanPreferences completedAt(Instant when) {
    return new FanPreferences(accountId, preferredGenres, when);
  }
}
