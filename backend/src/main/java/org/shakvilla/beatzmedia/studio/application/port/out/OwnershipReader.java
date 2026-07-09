package org.shakvilla.beatzmedia.studio.application.port.out;

/**
 * Output port: does ANY fan own the given episode (aggregate — not account-scoped)? Backs the
 * delete guard (OQ-8 / 409 {@code EPISODE_PUBLISHED}). The adapter calls commerce's INPUT port
 * ({@code GetOwnedEpisodeIds}) in-process — studio never reads commerce's {@code ownership_grant}
 * table directly. Mirrors the podcasts module's {@code OwnershipReader} output port (WU-POD-1).
 * Studio ADD §4.2 (WU-STU-2 addition — see §13 as-built notes).
 */
public interface OwnershipReader {

  boolean hasAnyOwner(String episodeId);
}
