package org.shakvilla.beatzmedia.admin.application.service;

import org.shakvilla.beatzmedia.admin.application.port.in.ModerationCaseView;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader;
import org.shakvilla.beatzmedia.admin.domain.ModerationCase;

/**
 * Maps a {@link ModerationCase} to its {@link ModerationCaseView} response, resolving {@code
 * targetRef} into a human-readable {@code item} label. Only the {@code "release:<id>"} target
 * kind is resolvable in this WU (the only source that creates cases — {@code FlagCatalogItem});
 * any other/unresolvable {@code targetRef} falls back to the raw opaque ref (same "fallback to
 * the raw id" precedent as {@code SupportTicketMapper}).
 */
final class ModerationCaseMapper {

  private static final String RELEASE_TARGET_PREFIX = "release:";

  private ModerationCaseMapper() {}

  static ModerationCaseView toView(ModerationCase mc, CatalogAdminReader catalogAdminReader) {
    return new ModerationCaseView(
        mc.getId(),
        resolveItemLabel(mc.getTargetRef(), catalogAdminReader),
        mc.getReporter(),
        mc.getReason().wireValue(),
        mc.getCreatedAt(),
        mc.getSeverity().wireValue(),
        mc.getStatus().wireValue(),
        mc.isEscalated());
  }

  private static String resolveItemLabel(String targetRef, CatalogAdminReader catalogAdminReader) {
    if (targetRef != null && targetRef.startsWith(RELEASE_TARGET_PREFIX)) {
      String releaseId = targetRef.substring(RELEASE_TARGET_PREFIX.length());
      return catalogAdminReader.detail(releaseId)
          .map(r -> "Release · \"" + r.title() + "\" by " + r.artistName())
          .orElse(targetRef);
    }
    return targetRef;
  }
}
