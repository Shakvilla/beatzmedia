package org.shakvilla.beatzmedia.studio.application.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.domain.UnauthorizedException;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.studio.application.port.in.EpisodeView;
import org.shakvilla.beatzmedia.studio.application.port.in.UpdateEpisode;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.EpisodeNotFoundException;
import org.shakvilla.beatzmedia.studio.domain.EpisodePublished;
import org.shakvilla.beatzmedia.studio.domain.EpisodeStatus;
import org.shakvilla.beatzmedia.studio.domain.IllegalEpisodeTransitionException;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;
import org.shakvilla.beatzmedia.studio.domain.ScheduleDateRequiredException;

/**
 * Application service for {@link UpdateEpisode} — LLFR-STUDIO-02.4. PATCH semantics ({@code null}
 * = no change). {@code visibility} drives the lifecycle transition ({@code public|scheduled|draft}
 * — see {@link Episode}'s state machine); every other field is a plain metadata mutation. Fires
 * {@code EpisodePublished} exactly once when the update transitions {@code draft -> published}.
 * Studio ADD §4.1 / §9.
 */
@ApplicationScoped
public class UpdateEpisodeService implements UpdateEpisode {

  private final StudioRepository repo;
  private final IdGenerator ids;
  private final Clock clock;
  private final AuditWriter auditWriter;
  private final Event<EpisodePublished> episodePublishedEvent;

  @Inject
  public UpdateEpisodeService(
      StudioRepository repo,
      IdGenerator ids,
      Clock clock,
      AuditWriter auditWriter,
      Event<EpisodePublished> episodePublishedEvent) {
    this.repo = repo;
    this.ids = ids;
    this.clock = clock;
    this.auditWriter = auditWriter;
    this.episodePublishedEvent = episodePublishedEvent;
  }

  @Override
  @Transactional
  public EpisodeView update(ArtistId artist, EpisodeId id, UpdateEpisodeCommand cmd) {
    Episode episode = repo.findEpisode(artist, id).orElseThrow(() -> new EpisodeNotFoundException(id.value()));
    if (!episode.artistId().value().equals(artist.value())) {
      throw new UnauthorizedException("Not your episode");
    }

    Instant now = clock.now();

    if (cmd.title() != null) {
      episode.updateTitle(cmd.title());
    }
    if (cmd.description() != null) {
      episode.updateDescription(cmd.description());
    }
    if (cmd.earlyAccess() != null) {
      episode.updateEarlyAccess(cmd.earlyAccess());
    }
    if (cmd.premium() != null || cmd.priceCedis() != null) {
      boolean newPremium = cmd.premium() != null ? cmd.premium() : episode.premium();
      long newPriceMinor = cmd.priceCedis() != null
          ? Money.ofCedis(cmd.priceCedis(), episode.currency()).minor()
          : episode.priceMinor();
      episode.updatePremiumPrice(newPremium, newPriceMinor, episode.currency());
    }

    boolean firePublished = applyVisibility(episode, cmd, now);

    Episode saved = repo.saveEpisode(episode);

    // INV-10: audit privileged mutation atomically in the same transaction.
    auditWriter.append(new AuditEntry(
        ids.newId(), artist.value(), "UPDATE_EPISODE", "Episode", id.value(), AuditType.CATALOG,
        null, now));

    if (firePublished) {
      episodePublishedEvent.fire(
          new EpisodePublished(saved.id().value(), saved.showId().value(), artist.value(), now));
    }

    PodcastShow show = repo.findShow(artist, saved.showId()).orElse(null);
    return EpisodeMapper.toView(saved, show != null ? show.title() : null);
  }

  /** Applies the {@code visibility}/{@code date} PATCH fields to the episode's lifecycle. Returns
   * {@code true} if this call transitioned the episode {@code draft -> published} (so the caller
   * must fire {@code EpisodePublished} exactly once). */
  private boolean applyVisibility(Episode episode, UpdateEpisodeCommand cmd, Instant now) {
    if (cmd.visibility() == null) {
      // No explicit visibility change requested — a bare 'date' on an already-scheduled episode is
      // a reschedule.
      if (cmd.date() != null && episode.status() == EpisodeStatus.scheduled) {
        requireFutureDate(cmd.date(), now);
        episode.reschedule(cmd.date());
      }
      return false;
    }

    return switch (cmd.visibility()) {
      case "public" -> {
        if (episode.status() == EpisodeStatus.draft) {
          episode.publishNow(now);
          yield true;
        }
        if (episode.status() == EpisodeStatus.published) {
          yield false; // already public — no-op, not an error
        }
        // scheduled -> public manually is disallowed: INV-7 permits publish only via the scheduler.
        throw new IllegalEpisodeTransitionException(episode.status(), "publish");
      }
      case "scheduled" -> {
        requireFutureDate(cmd.date(), now);
        if (episode.status() == EpisodeStatus.draft) {
          episode.scheduleAt(cmd.date());
        } else if (episode.status() == EpisodeStatus.scheduled) {
          episode.reschedule(cmd.date());
        } else {
          throw new IllegalEpisodeTransitionException(episode.status(), "schedule");
        }
        yield false;
      }
      case "draft" -> {
        if (episode.status() == EpisodeStatus.scheduled) {
          episode.unschedule();
        } else if (episode.status() != EpisodeStatus.draft) {
          throw new IllegalEpisodeTransitionException(episode.status(), "unschedule");
        }
        yield false;
      }
      default -> throw new ValidationException(
          "visibility must be 'public', 'scheduled' or 'draft'", "visibility");
    };
  }

  private static void requireFutureDate(Instant date, Instant now) {
    if (date == null || !date.isAfter(now)) {
      throw new ScheduleDateRequiredException(
          "visibility=scheduled requires a 'date' strictly in the future");
    }
  }
}
