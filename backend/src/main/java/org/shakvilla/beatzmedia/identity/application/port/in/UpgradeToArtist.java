package org.shakvilla.beatzmedia.identity.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port: upgrade the calling fan account to artist. Identity ADD §4.1 / LLFR-IDENTITY-02.2.
 *
 * <ul>
 *   <li>Trigger: POST /v1/me/become-artist
 *   <li>Authz: any authenticated fan
 *   <li>Idempotent: if {@code isArtist} is already {@code true}, returns the current account view
 *       as a no-op success.
 *   <li>Gated by {@code FeatureKey.ARTIST_SIGNUPS}; if disabled → throws
 *       {@link org.shakvilla.beatzmedia.platform.domain.FeatureDisabledException} (403).
 *   <li>On success: persists {@code isArtist=true} and publishes {@link
 *       org.shakvilla.beatzmedia.identity.domain.ArtistUpgraded} CDI event. The catalog module
 *       reacts to this event to create the empty {@code artist_profile} shell; identity does NOT
 *       write the catalog table (hexagonal rule, ADD §2 / §10).
 * </ul>
 */
public interface UpgradeToArtist {

  /**
   * Upgrades the account identified by {@code accountId} to artist. Idempotent: if already an
   * artist, returns current state without re-persisting or re-publishing the event.
   *
   * @param accountId the authenticated caller's account id
   * @return an {@link AccountView} reflecting the post-upgrade state
   * @throws org.shakvilla.beatzmedia.platform.domain.FeatureDisabledException when
   *     {@code ARTIST_SIGNUPS} flag is off
   * @throws org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException when the account does
   *     not exist
   */
  AccountView upgrade(AccountId accountId);
}
