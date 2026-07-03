package org.shakvilla.beatzmedia.payments.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.payments.application.port.in.GetPayouts;
import org.shakvilla.beatzmedia.payments.application.port.in.PayoutsView;
import org.shakvilla.beatzmedia.payments.domain.AccountId;

/**
 * REST resource for the creator studio payouts read (LLFR-PAYMENTS-02.2), backing
 * {@code GET /v1/studio/payouts}. Thin: JWT subject → {@link AccountId} → input port → the frontend
 * {@code Payouts} shape. No business logic here. Auth: {@code artist} (own studio).
 *
 * <p>Scope note: payout methods + withdrawals are WU-PAY-4; the returned {@code methods} list is
 * empty and there are no cash-out transactions yet. Royalty figures are ₵0 (OQ-4, ADR-20).
 */
@Path("/v1/studio/payouts")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("artist")
public class StudioPayoutsResource {

  private final GetPayouts getPayouts;
  private final JsonWebToken jwt;

  @Inject
  public StudioPayoutsResource(GetPayouts getPayouts, JsonWebToken jwt) {
    this.getPayouts = getPayouts;
    this.jwt = jwt;
  }

  /** GET /v1/studio/payouts — the authenticated creator's own balance + ledger + methods. */
  @GET
  public PayoutsView get() {
    return getPayouts.get(new AccountId(jwt.getSubject()));
  }
}
