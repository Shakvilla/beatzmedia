package org.shakvilla.beatzmedia.admin.application.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.CreateCuratedPlaylist;
import org.shakvilla.beatzmedia.admin.application.port.out.CuratedPlaylistRepository;
import org.shakvilla.beatzmedia.admin.domain.CuratedPlaylist;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for LLFR-ADMIN-06.1 (create curated playlist). Auth: editor, super-admin
 * (enforced by the inbound {@code @RolesAllowed}). Blank-name validation happens in the {@link
 * CuratedPlaylist} constructor (422 before any persistence). Appends exactly one {@link
 * AuditEntry} (INV-10) of {@code type=editorial}. Admin ADD §4.1 / §9.
 */
@ApplicationScoped
public class CreateCuratedPlaylistService implements CreateCuratedPlaylist {

  private final CuratedPlaylistRepository curatedPlaylists;
  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;

  @Inject
  public CreateCuratedPlaylistService(
      CuratedPlaylistRepository curatedPlaylists,
      AuditWriter auditWriter,
      IdGenerator idGenerator,
      Clock clock) {
    this.curatedPlaylists = curatedPlaylists;
    this.auditWriter = auditWriter;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public CuratedPlaylist create(String actorId, CuratedPlaylistInput input) {
    CuratedPlaylist playlist = new CuratedPlaylist(idGenerator.newId(), input.name());
    CuratedPlaylist saved = curatedPlaylists.save(playlist);

    Instant now = clock.now();
    auditWriter.append(new AuditEntry(
        idGenerator.newId(),
        actorId,
        "Created playlist",
        "CuratedPlaylist",
        saved.getId(),
        AuditType.EDITORIAL,
        null,
        now));

    return saved;
  }
}
