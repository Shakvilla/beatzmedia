package org.shakvilla.beatzmedia.platform.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.in.ManageTaxonomy;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.application.port.out.TaxonomyRepository;
import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;
import org.shakvilla.beatzmedia.platform.domain.TaxonomyKind;
import org.shakvilla.beatzmedia.platform.domain.TaxonomyTerm;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Administers the controlled lists — genres and the podcast/event/browse category taxonomies.
 *
 * <p><strong>Why the label matters so much here.</strong> The consuming columns
 * ({@code release.genre}, {@code podcast.category}, {@code event.category}) store the term's
 * <em>label</em>, not its id. That makes two operations non-trivial:
 *
 * <ul>
 *   <li><strong>rename</strong> must repoint those rows in the same transaction, or every release
 *       carrying the old spelling is orphaned;
 *   <li><strong>delete</strong> must be refused while anything still references the label, because
 *       there is no foreign key to refuse it for us.
 * </ul>
 *
 * <p>Every mutation appends exactly one {@link AuditEntry} (INV-10) in the same transaction.
 */
@ApplicationScoped
public class TaxonomyService implements ManageTaxonomy {

  private final TaxonomyRepository repo;
  private final AuditWriter auditWriter;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public TaxonomyService(
      TaxonomyRepository repo, AuditWriter auditWriter, IdGenerator ids, Clock clock) {
    this.repo = repo;
    this.auditWriter = auditWriter;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  public List<TaxonomyTerm> listActive(TaxonomyKind kind) {
    return repo.listActive(kind);
  }

  @Override
  public List<TaxonomyTerm> listAll(TaxonomyKind kind) {
    return repo.listAll(kind);
  }

  @Override
  @Transactional
  public TaxonomyTerm create(String actorAccountId, CreateTermCommand command) {
    String label = requireLabel(command.label());
    String slug = TaxonomyTerm.slugify(label);
    if (slug.isEmpty()) {
      // e.g. a label of "!!!" — everything is stripped and we would store a blank key.
      throw new ValidationException("Label must contain at least one letter or digit", "label");
    }

    // Both uniqueness checks matter: two different labels can slugify to the same key
    // ("Hip Life" and "Hip-Life" both give "hip-life"), and a duplicate label makes the
    // consuming columns ambiguous.
    repo.findByKindAndLabel(command.kind(), label)
        .ifPresent(
            existing -> {
              throw new ConflictException("A " + kindLabel(command.kind()) + " named \"" + label
                  + "\" already exists");
            });
    repo.findByKindAndSlug(command.kind(), slug)
        .ifPresent(
            existing -> {
              throw new ConflictException("\"" + label + "\" collides with the existing "
                  + kindLabel(command.kind()) + " \"" + existing.label() + "\"");
            });

    int sortOrder =
        command.sortOrder() != null ? command.sortOrder() : nextSortOrder(command.kind());

    TaxonomyTerm term =
        new TaxonomyTerm(
            ids.newId(), command.kind(), slug, label, command.colorClass(), sortOrder, true);
    repo.save(term);
    audit(actorAccountId, term, "CREATE_TAXONOMY_TERM");
    return term;
  }

  @Override
  @Transactional
  public TaxonomyTerm update(String actorAccountId, String id, UpdateTermCommand command) {
    TaxonomyTerm current = load(id);
    TaxonomyTerm updated = current;

    if (command.label() != null) {
      String newLabel = requireLabel(command.label());
      if (!newLabel.equals(current.label())) {
        repo.findByKindAndLabel(current.kind(), newLabel)
            .ifPresent(
                other -> {
                  throw new ConflictException("A " + kindLabel(current.kind()) + " named \""
                      + newLabel + "\" already exists");
                });
        // Repoint BEFORE saving the new label so that a failure here rolls the rename back with
        // it — a half-applied rename would leave rows pointing at a label that no longer exists.
        repo.repointUsages(current.kind(), current.label(), newLabel);
        updated = updated.withLabel(newLabel);
      }
    }
    if (command.colorClass() != null) {
      updated = updated.withColorClass(command.colorClass());
    }
    if (command.sortOrder() != null) {
      updated = updated.withSortOrder(command.sortOrder());
    }
    if (command.active() != null) {
      updated = updated.withActive(command.active());
    }

    if (updated.equals(current)) {
      return current; // nothing changed; no write, no audit row
    }
    repo.save(updated);
    audit(actorAccountId, updated, "UPDATE_TAXONOMY_TERM");
    return updated;
  }

  @Override
  @Transactional
  public void delete(String actorAccountId, String id) {
    TaxonomyTerm term = load(id);
    long usages = repo.countUsages(term.kind(), term.label());
    if (usages > 0) {
      // Refused rather than cascaded: silently nulling the genre on published releases is exactly
      // the kind of invisible data mutation this codebase has been removing.
      throw new ConflictException(
          "\"" + term.label() + "\" is still used by " + usages + " item"
              + (usages == 1 ? "" : "s")
              + ". Reassign them first, or deactivate the term to hide it from new content.");
    }
    repo.delete(id);
    audit(actorAccountId, term, "DELETE_TAXONOMY_TERM");
  }

  @Override
  public long usageCount(String id) {
    TaxonomyTerm term = load(id);
    return repo.countUsages(term.kind(), term.label());
  }

  private TaxonomyTerm load(String id) {
    return repo.findById(id)
        .orElseThrow(() -> new NotFoundException("Taxonomy term not found: " + id));
  }

  private static String requireLabel(String raw) {
    String label = raw == null ? "" : raw.trim();
    if (label.isBlank()) {
      throw new ValidationException("Label must not be blank", "label");
    }
    if (label.length() > 60) {
      throw new ValidationException("Label must not exceed 60 characters", "label");
    }
    return label;
  }

  /** Append to the end of the list, so a new term never silently reorders the existing ones. */
  private int nextSortOrder(TaxonomyKind kind) {
    return repo.listAll(kind).stream().mapToInt(TaxonomyTerm::sortOrder).max().orElse(0) + 1;
  }

  private static String kindLabel(TaxonomyKind kind) {
    return switch (kind) {
      case GENRE -> "genre";
      case PODCAST_CATEGORY -> "podcast category";
      case EVENT_CATEGORY -> "event category";
      case BROWSE_CATEGORY -> "browse category";
    };
  }

  private void audit(String actorAccountId, TaxonomyTerm term, String action) {
    auditWriter.append(
        new AuditEntry(
            ids.newId(),
            actorAccountId,
            action,
            "TaxonomyTerm",
            term.id(),
            AuditType.SETTINGS,
            term.kind().wireValue() + ":" + term.label(),
            clock.now()));
  }
}
