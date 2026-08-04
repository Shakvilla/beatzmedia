package org.shakvilla.beatzmedia.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.shakvilla.beatzmedia.identity.application.port.in.ManageFanPreferences;
import org.shakvilla.beatzmedia.identity.application.port.out.FanPreferencesRepository;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.FanPreferences;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.TaxonomyRepository;
import org.shakvilla.beatzmedia.platform.domain.TaxonomyKind;
import org.shakvilla.beatzmedia.platform.domain.TaxonomyTerm;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Reads and writes a fan's taste profile.
 *
 * <p>Genres are validated against the ACTIVE genre taxonomy on every write. A client holding a
 * cached list could otherwise record a genre an admin has since deleted or deactivated, and that
 * value would sit in the profile looking real while matching nothing.
 */
@ApplicationScoped
public class FanPreferencesService implements ManageFanPreferences {

  private final FanPreferencesRepository repo;
  private final TaxonomyRepository taxonomy;
  private final Clock clock;

  @Inject
  public FanPreferencesService(
      FanPreferencesRepository repo, TaxonomyRepository taxonomy, Clock clock) {
    this.repo = repo;
    this.taxonomy = taxonomy;
    this.clock = clock;
  }

  @Override
  public FanPreferences get(AccountId accountId) {
    // Absent is a legitimate state (never onboarded), not a 404.
    return repo.find(accountId).orElseGet(() -> FanPreferences.empty(accountId));
  }

  @Override
  @Transactional
  public FanPreferences completeOnboarding(AccountId accountId, List<String> genres) {
    List<String> clean = validate(genres, true);
    FanPreferences saved =
        get(accountId).withGenres(clean).completedAt(clock.now());
    repo.save(saved);
    return saved;
  }

  @Override
  @Transactional
  public FanPreferences updateGenres(AccountId accountId, List<String> genres) {
    // No minimum here: onboarding is the gate, and a fan trimming their list later should not be
    // blocked by it.
    List<String> clean = validate(genres, false);
    FanPreferences saved = get(accountId).withGenres(clean);
    repo.save(saved);
    return saved;
  }

  private List<String> validate(List<String> genres, boolean enforceMinimum) {
    // Preserve the order the fan picked, but drop duplicates — three selections of the same genre
    // must not satisfy a minimum of three.
    List<String> unique =
        new LinkedHashSet<>(genres == null ? List.<String>of() : genres).stream().toList();

    if (enforceMinimum && unique.size() < FanPreferences.MIN_GENRES) {
      throw new ValidationException(
          "Pick at least " + FanPreferences.MIN_GENRES + " genres", "genres");
    }

    Set<String> allowed =
        taxonomy.listActive(TaxonomyKind.GENRE).stream()
            .map(TaxonomyTerm::label)
            .collect(Collectors.toSet());
    for (String genre : unique) {
      if (!allowed.contains(genre)) {
        throw new ValidationException("Unknown genre: " + genre, "genres");
      }
    }
    return unique;
  }
}
