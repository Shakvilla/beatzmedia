package org.shakvilla.beatzmedia.platform.application.port.out;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.platform.domain.TaxonomyKind;
import org.shakvilla.beatzmedia.platform.domain.TaxonomyTerm;

/** Outbound port for the admin-managed taxonomy lists. */
public interface TaxonomyRepository {

  /** Every term of a kind, active and inactive, in {@code sort_order}. For the admin console. */
  List<TaxonomyTerm> listAll(TaxonomyKind kind);

  /** Only the active terms of a kind, in {@code sort_order}. For pickers and public reads. */
  List<TaxonomyTerm> listActive(TaxonomyKind kind);

  Optional<TaxonomyTerm> findById(String id);

  /** Used to reject a duplicate before insert, and to resolve a submitted label to a term. */
  Optional<TaxonomyTerm> findByKindAndLabel(TaxonomyKind kind, String label);

  Optional<TaxonomyTerm> findByKindAndSlug(TaxonomyKind kind, String slug);

  void save(TaxonomyTerm term);

  void delete(String id);

  /**
   * How many rows outside this table currently reference {@code label} for this kind — releases for
   * {@code GENRE}, podcasts for {@code PODCAST_CATEGORY}, and so on.
   *
   * <p>This is what makes "block delete if in use" possible. It reads other modules' tables, which
   * the dependency rule forbids for business logic; it is admissible here only because the platform
   * kernel owns the taxonomy that those columns are constrained by, and the query is a pure count
   * with no domain meaning. See the ADR referenced in the module docs.
   */
  long countUsages(TaxonomyKind kind, String label);

  /**
   * Repoints every referencing row from {@code oldLabel} to {@code newLabel} for this kind.
   *
   * <p>Needed because the consuming columns store the label, not the id: without this a rename
   * would orphan every release or podcast carrying the old spelling.
   */
  int repointUsages(TaxonomyKind kind, String oldLabel, String newLabel);
}
