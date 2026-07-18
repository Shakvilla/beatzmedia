package org.shakvilla.beatzmedia.catalog.adapter.in.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.ProvisionArtistProfile;
import org.shakvilla.beatzmedia.catalog.application.port.in.ProvisionArtistProfile.ProvisionCommand;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.identity.domain.ArtistUpgraded;

/**
 * Inbound adapter: catalog's reaction to identity's {@link ArtistUpgraded} domain event. Provisions
 * the {@code artist_profile} shell so a real (non-seed) artist can create releases — the
 * {@code release.artist_id} FK references {@code artist_profile(id)}, so without this row every
 * studio release create/upload for a freshly-upgraded artist would fail. WU-CAT-7 / Catalog ADD §10.
 *
 * <h3>Cross-module seam (hexagonal rule)</h3>
 *
 * Catalog reacts to the identity domain event and never reads or writes an identity table; identity
 * never touches {@code artist_profile}. This is the same event-reaction pattern used by {@code
 * analytics.adapter.in.events.*Observer} and {@code store.adapter.in.events.PurchaseConfirmedSubscriber}.
 *
 * <h3>Transaction semantics</h3>
 *
 * {@code AFTER_SUCCESS} + {@code REQUIRES_NEW}: the profile is created in its own transaction after
 * the account upgrade has committed, so provisioning is fully decoupled from the identity write and
 * a provisioning failure can never roll back the upgrade. The observer runs synchronously during
 * commit completion on the request thread, so the profile row is durable before {@code
 * POST /v1/me/become-artist} returns. Provisioning is idempotent (see {@link ProvisionArtistProfile}).
 */
@ApplicationScoped
public class ArtistUpgradedObserver {

  private final ProvisionArtistProfile provisionArtistProfile;

  @Inject
  public ArtistUpgradedObserver(ProvisionArtistProfile provisionArtistProfile) {
    this.provisionArtistProfile = provisionArtistProfile;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onArtistUpgraded(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) ArtistUpgraded event) {
    provisionArtistProfile.provision(
        new ProvisionCommand(new ArtistId(event.accountId()), event.name(), event.avatar()));
  }
}
