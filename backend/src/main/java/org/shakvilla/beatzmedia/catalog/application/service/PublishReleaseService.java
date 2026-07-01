package org.shakvilla.beatzmedia.catalog.application.service;

import java.time.Instant;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.catalog.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.catalog.application.port.in.PublishRelease;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ContentTakenDown;
import org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseApproved;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseWentLive;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for {@link PublishRelease}. Orchestrates the release lifecycle FSM
 * (LLFR-CATALOG-02.5): loads the aggregate, enforces INV-12 (no live transition while any split is
 * pending) before delegating to the domain guard methods, persists the result, flips constituent
 * tracks to publicly streamable on any transition that results in {@code live}, appends exactly
 * one {@link AuditEntry} for admin-triggered transitions (INV-10), and fires the documented domain
 * events after the state change is persisted. Catalog ADD §4.1 / §8 / §9.
 */
@ApplicationScoped
public class PublishReleaseService implements PublishRelease {

  private final CatalogRepository repo;
  private final Clock clock;
  private final IdGenerator ids;
  private final AuditWriter auditWriter;
  private final Event<ReleaseApproved> releaseApprovedEvent;
  private final Event<ReleaseWentLive> releaseWentLiveEvent;
  private final Event<ContentTakenDown> contentTakenDownEvent;

  @Inject
  public PublishReleaseService(
      CatalogRepository repo,
      Clock clock,
      IdGenerator ids,
      AuditWriter auditWriter,
      Event<ReleaseApproved> releaseApprovedEvent,
      Event<ReleaseWentLive> releaseWentLiveEvent,
      Event<ContentTakenDown> contentTakenDownEvent) {
    this.repo = repo;
    this.clock = clock;
    this.ids = ids;
    this.auditWriter = auditWriter;
    this.releaseApprovedEvent = releaseApprovedEvent;
    this.releaseWentLiveEvent = releaseWentLiveEvent;
    this.contentTakenDownEvent = contentTakenDownEvent;
  }

  @Override
  @Transactional
  public StudioReleaseView transition(
      ReleaseId id, ReleaseTransition action, String actorId, Optional<Instant> scheduledAt) {
    return transition(id, action, actorId, scheduledAt, null);
  }

  @Override
  @Transactional
  public StudioReleaseView transition(
      ReleaseId id,
      ReleaseTransition action,
      String actorId,
      Optional<Instant> scheduledAt,
      String reason) {
    Release release = repo.findRelease(id).orElseThrow(() -> new ReleaseNotFoundException(id.value()));
    Instant now = clock.now();

    switch (action) {
      case APPROVE_SCHEDULED -> {
        Instant at = scheduledAt.orElseThrow(
            () -> new IllegalArgumentException("scheduledAt is required for APPROVE_SCHEDULED"));
        release.approveScheduled(at, now);
        repo.saveRelease(release);
        auditApprove(release, actorId, now);
        releaseApprovedEvent.fire(new ReleaseApproved(
            release.getId(), release.getArtistId(), ReleaseStatus.scheduled, at, actorId, now));
      }
      case APPROVE_IMMEDIATE -> {
        // INV-12: a release cannot go live while any split is pending.
        guardNoPendingSplits(id);
        release.approveImmediate(now);
        repo.saveRelease(release);
        repo.markReleaseTracksReady(id);
        auditApprove(release, actorId, now);
        releaseApprovedEvent.fire(new ReleaseApproved(
            release.getId(), release.getArtistId(), ReleaseStatus.live, null, actorId, now));
        releaseWentLiveEvent.fire(
            new ReleaseWentLive(release.getId(), release.getArtistId(), now));
      }
      case GO_LIVE -> {
        // System-only (scheduler). INV-12 guard applies here too — a scheduled release with a
        // still-pending split does not go live; it is retried on the next sweep.
        guardNoPendingSplits(id);
        release.goLive(now);
        repo.saveRelease(release);
        repo.markReleaseTracksReady(id);
        releaseWentLiveEvent.fire(
            new ReleaseWentLive(release.getId(), release.getArtistId(), now));
      }
      case TAKEDOWN -> {
        release.takedown(now);
        repo.saveRelease(release);
        auditTakedown(release, actorId, reason, now);
        contentTakenDownEvent.fire(new ContentTakenDown(
            release.getId(), release.getArtistId(), actorId, reason, now));
      }
      case REINSTATE -> {
        release.reinstate(now);
        repo.saveRelease(release);
        repo.markReleaseTracksReady(id);
        auditReinstate(release, actorId, now);
        releaseWentLiveEvent.fire(
            new ReleaseWentLive(release.getId(), release.getArtistId(), now));
      }
      default -> throw new IllegalStateException("Unhandled transition: " + action);
    }

    return toView(release);
  }

  /** INV-12: reject a live-bound transition while any split for this release is pending. */
  private void guardNoPendingSplits(ReleaseId id) {
    if (repo.hasPendingSplits(id)) {
      throw new IllegalTransitionException(
          "Release " + id.value() + " has unconfirmed splits (INV-12); cannot go live");
    }
  }

  private void auditApprove(Release release, String actorId, Instant now) {
    auditWriter.append(new AuditEntry(
        ids.newId(),
        actorId,
        "APPROVE_RELEASE",
        "Release",
        release.getId(),
        AuditType.MODERATION,
        null,
        now));
  }

  private void auditTakedown(Release release, String actorId, String reason, Instant now) {
    auditWriter.append(new AuditEntry(
        ids.newId(),
        actorId,
        "TAKEDOWN_RELEASE",
        "Release",
        release.getId(),
        AuditType.MODERATION,
        reason,
        now));
  }

  private void auditReinstate(Release release, String actorId, Instant now) {
    auditWriter.append(new AuditEntry(
        ids.newId(),
        actorId,
        "REINSTATE_RELEASE",
        "Release",
        release.getId(),
        AuditType.MODERATION,
        null,
        now));
  }

  private StudioReleaseView toView(Release r) {
    String date = r.getCreatedAt() != null ? r.getCreatedAt().toString() : "—";
    return new StudioReleaseView(
        r.getId(),
        r.getTitle(),
        r.getType(),
        r.getStatus(),
        date,
        r.getTracks().size(),
        0L,
        MoneyView.ofMinor(0L),
        MoneyView.ofMinor(r.getListPriceMinor()));
  }
}
