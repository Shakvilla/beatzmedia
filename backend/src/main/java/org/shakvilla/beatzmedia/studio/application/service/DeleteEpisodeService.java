package org.shakvilla.beatzmedia.studio.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.UnauthorizedException;
import org.shakvilla.beatzmedia.studio.application.port.in.DeleteEpisode;
import org.shakvilla.beatzmedia.studio.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeHasOwnersException;
import org.shakvilla.beatzmedia.studio.domain.EpisodeId;
import org.shakvilla.beatzmedia.studio.domain.EpisodeNotFoundException;
import org.shakvilla.beatzmedia.studio.domain.EpisodeStatus;

/**
 * Application service for {@link DeleteEpisode} — LLFR-STUDIO-02.4. A {@code published} episode
 * with at least one owner cannot be deleted (409 {@code EPISODE_PUBLISHED}, OQ-8) — checked via the
 * {@link OwnershipReader} output port (in-process call into commerce's {@code GetOwnedEpisodeIds}
 * input port). {@code draft}/{@code scheduled} episodes, and {@code published} episodes with no
 * owner, delete freely. Studio ADD §4.1 / §9 / §11.
 */
@ApplicationScoped
public class DeleteEpisodeService implements DeleteEpisode {

  private final StudioRepository repo;
  private final OwnershipReader ownershipReader;
  private final IdGenerator ids;
  private final Clock clock;
  private final AuditWriter auditWriter;

  @Inject
  public DeleteEpisodeService(
      StudioRepository repo,
      OwnershipReader ownershipReader,
      IdGenerator ids,
      Clock clock,
      AuditWriter auditWriter) {
    this.repo = repo;
    this.ownershipReader = ownershipReader;
    this.ids = ids;
    this.clock = clock;
    this.auditWriter = auditWriter;
  }

  @Override
  @Transactional
  public void delete(ArtistId artist, EpisodeId id) {
    Episode episode = repo.findEpisode(artist, id).orElseThrow(() -> new EpisodeNotFoundException(id.value()));
    if (!episode.artistId().value().equals(artist.value())) {
      throw new UnauthorizedException("Not your episode");
    }
    if (episode.status() == EpisodeStatus.published && ownershipReader.hasAnyOwner(id.value())) {
      throw new EpisodeHasOwnersException(id.value());
    }

    repo.deleteEpisode(id);

    // INV-10: audit privileged mutation atomically in the same transaction.
    auditWriter.append(new AuditEntry(
        ids.newId(), artist.value(), "DELETE_EPISODE", "Episode", id.value(), AuditType.CATALOG,
        null, clock.now()));
  }
}
