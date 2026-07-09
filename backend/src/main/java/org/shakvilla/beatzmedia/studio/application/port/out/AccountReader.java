package org.shakvilla.beatzmedia.studio.application.port.out;

import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/**
 * Output port: resolve the caller's account email for the Studio Settings {@code email} field —
 * the one Category B field that IS genuinely backed by another module (studio.md §16). The adapter
 * calls {@code identity}'s {@code GetCurrentAccount} INPUT port in-process — {@code studio} never
 * reads {@code identity}'s {@code account} table directly. Mirrors {@code OwnershipReader}
 * (WU-STU-2). Studio ADD §4.2 (WU-STU-4 addition — see §16 as-built notes).
 */
public interface AccountReader {

  /** The caller's account email, or {@code ""} if it cannot be resolved. */
  String emailOf(ArtistId artist);
}
