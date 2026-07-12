package org.shakvilla.beatzmedia.payments.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.shakvilla.beatzmedia.payments.application.port.in.FinanceOverviewView;
import org.shakvilla.beatzmedia.payments.application.port.in.GetFinanceOverview;
import org.shakvilla.beatzmedia.payments.domain.FinanceRange;

/**
 * REST resource for the admin finance overview (LLFR-ADMIN-05.1), backing {@code GET
 * /v1/admin/finance?range=24h|7d|30d}. Thin: parse the range → input port → the frontend {@code
 * Finance} shape ({@code { kpis, pendingPayouts, providerMix, disputes }}). No business logic here.
 *
 * <p>This completes the admin finance <em>surface</em> (WU-ADM-5). The action endpoints under {@code
 * /v1/admin/finance/*} — payout runs (03.3/03.4), ledger (02.3), dispute adjudication (04.*) — were
 * already delivered by WU-PAY-3/4/5; this adds only the read-only overview KPIs.
 *
 * <p><strong>Auth (RBAC, WU-IDN-4).</strong> {@code finance} / {@code super-admin} only — finance owns
 * payouts/ledger/disputes (PRD §14). Read-only; nothing audited (INV-10 applies to mutations).
 */
@Path("/v1/admin/finance")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"finance", "super-admin"})
@Tag(name = "admin-finance")
@SecurityRequirement(name = "BearerAuth")
public class AdminFinanceOverviewResource {

  private final GetFinanceOverview getFinanceOverview;

  @Inject
  public AdminFinanceOverviewResource(GetFinanceOverview getFinanceOverview) {
    this.getFinanceOverview = getFinanceOverview;
  }

  /** GET /v1/admin/finance?range=24h|7d|30d — LLFR-ADMIN-05.1. */
  @GET
  @Operation(summary = "Admin finance overview KPIs")
  @APIResponse(responseCode = "200", description = "Finance KPIs, pending payouts, provider mix, disputes")
  @APIResponse(responseCode = "422", description = "Unrecognised range value (INVALID_RANGE)")
  public FinanceOverviewView overview(
      @Parameter(description = "24h|7d|30d (default 7d)") @QueryParam("range") String range) {
    return getFinanceOverview.overview(FinanceRange.fromWire(range));
  }
}
