package org.shakvilla.beatzmedia.identity.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.FanPreferences;

/** Inbound port for a fan's taste profile and their onboarding state. */
public interface ManageFanPreferences {

  /** Never 404s — a fan who has never onboarded reads back an empty, not-completed profile. */
  FanPreferences get(AccountId accountId);

  /**
   * Records the genres a fan picked and marks onboarding complete.
   *
   * @throws org.shakvilla.beatzmedia.platform.domain.ValidationException if fewer than {@link
   *     FanPreferences#MIN_GENRES} genres are given, or any of them is not an active genre in the
   *     taxonomy — a client sending a stale genre list must not silently record a genre that no
   *     longer exists.
   */
  FanPreferences completeOnboarding(AccountId accountId, List<String> genres);

  /** Updates the taste profile without touching onboarding state (settings screen, later). */
  FanPreferences updateGenres(AccountId accountId, List<String> genres);
}
