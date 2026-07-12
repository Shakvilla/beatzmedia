package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminPort;
import org.shakvilla.beatzmedia.catalog.application.port.in.PublishRelease;
import org.shakvilla.beatzmedia.catalog.application.port.in.PublishRelease.ReleaseTransition;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;

/**
 * Implements admin's {@link CatalogAdminPort} output port by calling catalog's real {@link
 * PublishRelease} INPUT port in-process — {@code admin} never writes catalog's {@code release}
 * table directly for mutations (the FSM invariants live in {@code catalog.domain.Release}, not
 * duplicated here). Placed alongside {@link CatalogAdminReaderAdapter} in {@code
 * adapter.out.persistence} — this module has no {@code adapter.out.integration} package yet (same
 * placement precedent as {@code AccountAdminPortAdapter}, WU-ADM-2). {@code PublishReleaseService}
 * self-audits every transition; this adapter appends no {@code AuditEntry} of its own.
 */
@ApplicationScoped
public class CatalogAdminPortAdapter implements CatalogAdminPort {

  private final PublishRelease publishRelease;

  @Inject
  public CatalogAdminPortAdapter(PublishRelease publishRelease) {
    this.publishRelease = publishRelease;
  }

  @Override
  public void approve(String actorId, String releaseId, Optional<Instant> goLiveAt) {
    ReleaseTransition action =
        goLiveAt.isPresent() ? ReleaseTransition.APPROVE_SCHEDULED : ReleaseTransition.APPROVE_IMMEDIATE;
    publishRelease.transition(new ReleaseId(releaseId), action, actorId, goLiveAt);
  }

  @Override
  public void takedown(String actorId, String releaseId, String reason) {
    publishRelease.transition(
        new ReleaseId(releaseId), ReleaseTransition.TAKEDOWN, actorId, Optional.empty(), reason);
  }

  @Override
  public void reinstate(String actorId, String releaseId) {
    publishRelease.transition(
        new ReleaseId(releaseId), ReleaseTransition.REINSTATE, actorId, Optional.empty());
  }
}
