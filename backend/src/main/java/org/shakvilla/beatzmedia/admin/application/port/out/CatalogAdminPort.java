package org.shakvilla.beatzmedia.admin.application.port.out;

import java.time.Instant;
import java.util.Optional;

/**
 * Output port for admin's cross-module CATALOG MUTATIONS: implemented by an adapter that calls
 * catalog's real {@code PublishRelease} input port in-process — the SAME "genuine cross-module
 * input-port call, admin's own output port" pattern as {@link AccountAdminPort} calling identity's
 * mutation ports (WU-ADM-2). The underlying {@code PublishReleaseService} self-audits every
 * transition (INV-10, {@code AuditType.MODERATION}); the admin-side use cases calling this port
 * must NOT append a second AuditEntry. Admin ADD §4.3.
 */
public interface CatalogAdminPort {

  /** Approves an {@code in_review} release. A present {@code goLiveAt} yields {@code scheduled}. */
  void approve(String actorId, String releaseId, Optional<Instant> goLiveAt);

  /** Takes a {@code live} release down. {@code reason} is required (validated by the caller). */
  void takedown(String actorId, String releaseId, String reason);

  /** Reinstates a {@code takedown} release back to {@code live}. */
  void reinstate(String actorId, String releaseId);
}
