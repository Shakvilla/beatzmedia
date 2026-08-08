package org.shakvilla.beatzmedia.platform.fakes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.platform.application.port.out.TaxonomyRepository;
import org.shakvilla.beatzmedia.platform.domain.TaxonomyKind;
import org.shakvilla.beatzmedia.platform.domain.TaxonomyTerm;

/**
 * In-memory {@link TaxonomyRepository} for unit tests.
 *
 * <p>Seeded by default with the nine genres, eight podcast categories and five event categories
 * that V972 migrates from the old hardcoded lists, so a test that merely needs "Drill" to be a valid
 * genre does not have to arrange the taxonomy itself.
 *
 * <p>{@code countUsages} is driven by {@link #setUsages} rather than inferred: this fake holds no
 * releases or podcasts, so usage has to be stated by the test that cares about it.
 */
public class FakeTaxonomyRepository implements TaxonomyRepository {

  private final Map<String, TaxonomyTerm> byId = new LinkedHashMap<>();
  private final Map<String, Long> usages = new LinkedHashMap<>();
  private final List<String> repointed = new ArrayList<>();

  /** A repository pre-loaded with the migrated defaults. */
  public static FakeTaxonomyRepository withDefaults() {
    FakeTaxonomyRepository repo = new FakeTaxonomyRepository();
    String[] genres = {
      "Afrobeats", "Hiplife", "Highlife", "Amapiano", "Drill", "Gospel", "R&B", "Reggae", "Jazz"
    };
    int order = 1;
    for (String g : genres) {
      repo.add(TaxonomyKind.GENRE, g, order++);
    }
    String[] podcast = {
      "News & Politics", "Comedy", "Business", "Sports", "Culture", "Tech", "Health", "Storytelling"
    };
    order = 1;
    for (String c : podcast) {
      repo.add(TaxonomyKind.PODCAST_CATEGORY, c, order++);
    }
    String[] events = {"Concert", "Festival", "Club Night", "Listening Party", "Tour"};
    order = 1;
    for (String c : events) {
      repo.add(TaxonomyKind.EVENT_CATEGORY, c, order++);
    }
    return repo;
  }

  /** Adds an active term, deriving id and slug the way the service does. */
  public TaxonomyTerm add(TaxonomyKind kind, String label, int sortOrder) {
    String slug = TaxonomyTerm.slugify(label);
    TaxonomyTerm term =
        new TaxonomyTerm(kind.wireValue() + "-" + slug, kind, slug, label, null, sortOrder, true);
    byId.put(term.id(), term);
    return term;
  }

  /** States how many rows reference a label, so delete-blocking can be exercised. */
  public void setUsages(TaxonomyKind kind, String label, long count) {
    usages.put(kind.wireValue() + ":" + label, count);
  }

  /** Every repoint performed, as {@code "kind:old->new"}, for asserting rename propagation. */
  public List<String> repointed() {
    return List.copyOf(repointed);
  }

  @Override
  public List<TaxonomyTerm> listAll(TaxonomyKind kind) {
    return byId.values().stream()
        .filter(t -> t.kind() == kind)
        .sorted((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()))
        .toList();
  }

  @Override
  public List<TaxonomyTerm> listActive(TaxonomyKind kind) {
    return listAll(kind).stream().filter(TaxonomyTerm::active).toList();
  }

  @Override
  public Optional<TaxonomyTerm> findById(String id) {
    return Optional.ofNullable(byId.get(id));
  }

  @Override
  public Optional<TaxonomyTerm> findByKindAndLabel(TaxonomyKind kind, String label) {
    return listAll(kind).stream().filter(t -> t.label().equals(label)).findFirst();
  }

  @Override
  public Optional<TaxonomyTerm> findByKindAndSlug(TaxonomyKind kind, String slug) {
    return listAll(kind).stream().filter(t -> t.slug().equals(slug)).findFirst();
  }

  @Override
  public void save(TaxonomyTerm term) {
    byId.put(term.id(), term);
  }

  @Override
  public void delete(String id) {
    byId.remove(id);
  }

  @Override
  public long countUsages(TaxonomyKind kind, String label) {
    return usages.getOrDefault(kind.wireValue() + ":" + label, 0L);
  }

  @Override
  public int repointUsages(TaxonomyKind kind, String oldLabel, String newLabel) {
    repointed.add(kind.wireValue() + ":" + oldLabel + "->" + newLabel);
    long moved = usages.remove(kind.wireValue() + ":" + oldLabel) == null ? 0 : 1;
    return (int) moved;
  }
}
