package org.shakvilla.beatzmedia.admin.application.port.out;

import java.util.Optional;

import org.shakvilla.beatzmedia.admin.domain.ModReason;
import org.shakvilla.beatzmedia.admin.domain.ModStatus;
import org.shakvilla.beatzmedia.admin.domain.ModerationCase;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Output port for {@link ModerationCase} persistence (owns {@code moderation_case}; this module's
 * table only). Implemented by a JPA adapter in {@code adapter.out.persistence}. Admin ADD §4.2 /
 * §7.
 */
public interface ModerationCaseRepository {

  /** Paged, filtered list of cases ordered newest first. */
  Page<ModerationCase> list(ModStatus status, ModReason type, PageRequest page);

  /** Loads a single case, or empty if not found. */
  Optional<ModerationCase> findById(String caseId);

  /** Upsert: persists the case's current state (or inserts a newly-opened one). */
  void save(ModerationCase moderationCase);

  /** Whole-queue summary counts, independent of any filter. */
  Summary summary();

  record Summary(long openCount, long escalatedCount) {}
}
