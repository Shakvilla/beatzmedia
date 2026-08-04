package org.shakvilla.beatzmedia.identity.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.identity.application.port.out.FanPreferencesRepository;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.FanPreferences;

/** JPA adapter for {@code fan_preferences}. */
@ApplicationScoped
public class JpaFanPreferencesRepository implements FanPreferencesRepository {

  private final EntityManager em;

  @Inject
  public JpaFanPreferencesRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public Optional<FanPreferences> find(AccountId accountId) {
    FanPreferencesEntity e = em.find(FanPreferencesEntity.class, accountId.value());
    if (e == null) {
      return Optional.empty();
    }
    List<String> genres =
        e.preferredGenres == null ? List.of() : Arrays.stream(e.preferredGenres).toList();
    return Optional.of(new FanPreferences(accountId, genres, e.completedAt));
  }

  @Override
  public void save(FanPreferences preferences) {
    FanPreferencesEntity e = new FanPreferencesEntity();
    e.accountId = preferences.accountId().value();
    e.preferredGenres = preferences.preferredGenres().toArray(String[]::new);
    e.completedAt = preferences.completedAt();
    // merge, not persist: the row may already exist (a fan editing their genres later), and the
    // service reads before it writes, so the entity can already be managed.
    em.merge(e);
  }
}
