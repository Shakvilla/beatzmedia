package org.shakvilla.beatzmedia.library.application.port.out;

import java.util.Collection;
import java.util.Set;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Output port: of the given candidate track ids, which does the account hold an ACTIVE grant for
 * that also permits downloading? Batched — a library collection load with N owned tracks must
 * issue one permission query, not N.
 *
 * <p>Answered from the buyer's own {@code ownership_grant.downloadable} (captured once, at
 * settlement) — never from the release's current setting, which can differ the moment an artist
 * changes their mind after a sale. The adapter calls commerce's {@code GetTrackDownloadPermission}
 * INPUT port in-process; library never reads commerce's {@code ownership_grant} table directly.
 * Same shape as playback's {@code DownloadPermissionReader}. Library ADD §4.2.
 */
public interface DownloadPermissionReader {

  Set<String> downloadableTrackIds(AccountId account, Collection<String> trackIds);
}
