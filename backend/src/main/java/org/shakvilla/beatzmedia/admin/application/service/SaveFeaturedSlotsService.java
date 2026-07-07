package org.shakvilla.beatzmedia.admin.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.SaveFeaturedSlots;
import org.shakvilla.beatzmedia.admin.application.port.out.FeaturedSlotRepository;
import org.shakvilla.beatzmedia.admin.domain.DuplicateFeaturedPositionException;
import org.shakvilla.beatzmedia.admin.domain.FeaturedSlot;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for LLFR-ADMIN-06.1 (save featured slots — ordered full-set PUT). Auth:
 * editor, super-admin (enforced by the inbound {@code @RolesAllowed}). Rejects a payload with
 * duplicate ids (assigns new ids when blank) or requesting the same target position twice before
 * any state change. Appends exactly one {@link AuditEntry} (INV-10) of {@code type=editorial}.
 * Admin ADD §4.1 / §9.
 */
@ApplicationScoped
public class SaveFeaturedSlotsService implements SaveFeaturedSlots {

  private final FeaturedSlotRepository featuredSlots;
  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;

  @Inject
  public SaveFeaturedSlotsService(
      FeaturedSlotRepository featuredSlots,
      AuditWriter auditWriter,
      IdGenerator idGenerator,
      Clock clock) {
    this.featuredSlots = featuredSlots;
    this.auditWriter = auditWriter;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public List<FeaturedSlot> save(String actorId, List<FeaturedSlotInput> ordered) {
    List<FeaturedSlot> toPersist = new ArrayList<>();
    Set<String> seenIds = new HashSet<>();
    int position = 1;
    for (FeaturedSlotInput input : ordered) {
      String id = input.id() == null || input.id().isBlank() ? idGenerator.newId() : input.id();
      if (!seenIds.add(id)) {
        throw new DuplicateFeaturedPositionException();
      }
      toPersist.add(new FeaturedSlot(id, position, input.title(), input.note(), input.sponsored()));
      position++;
    }

    List<FeaturedSlot> saved = featuredSlots.replaceAll(toPersist);

    Instant now = clock.now();
    auditWriter.append(new AuditEntry(
        idGenerator.newId(),
        actorId,
        "Reordered featured",
        "FeaturedSlot",
        "home-featured",
        AuditType.EDITORIAL,
        null,
        now));

    return saved;
  }
}
