package org.shakvilla.beatzmedia.platform.application.port.in;

import java.util.List;

import org.shakvilla.beatzmedia.platform.domain.TaxonomyKind;
import org.shakvilla.beatzmedia.platform.domain.TaxonomyTerm;

/**
 * Inbound port for reading and administering the controlled lists.
 *
 * <p>Reads are open to any caller (pickers need them); the four mutations are super-admin only,
 * enforced at the REST boundary.
 */
public interface ManageTaxonomy {

  /** Active terms only — what every picker and public surface should show. */
  List<TaxonomyTerm> listActive(TaxonomyKind kind);

  /** Every term including deactivated ones — the admin console view. */
  List<TaxonomyTerm> listAll(TaxonomyKind kind);

  /**
   * Creates a term. The slug is derived from the label; a duplicate label or slug within the same
   * kind is a conflict, not a second row.
   */
  TaxonomyTerm create(String actorAccountId, CreateTermCommand command);

  /**
   * Renames, reorders, recolours or (de)activates a term. Every field is optional — a null means
   * "leave alone", which is what lets the console send a single-field PATCH.
   *
   * <p>A rename also repoints existing rows, so a corrected spelling does not orphan published
   * content.
   */
  TaxonomyTerm update(String actorAccountId, String id, UpdateTermCommand command);

  /**
   * Removes a term outright. Refused with a conflict when anything still references it — the
   * operator is told the count so they know what to reassign first.
   */
  void delete(String actorAccountId, String id);

  /** How many rows reference this term; surfaced in the console so deletion is never a surprise. */
  long usageCount(String id);

  record CreateTermCommand(TaxonomyKind kind, String label, String colorClass, Integer sortOrder) {}

  record UpdateTermCommand(String label, String colorClass, Integer sortOrder, Boolean active) {}
}
