package org.shakvilla.beatzmedia.payments.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.shakvilla.beatzmedia.payments.application.port.in.GetLedger;
import org.shakvilla.beatzmedia.payments.application.port.in.LedgerEntryView;
import org.shakvilla.beatzmedia.payments.domain.LedgerType;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * REST resource for the admin finance ledger read (LLFR-PAYMENTS-02.3), backing
 * {@code GET /v1/admin/finance/ledger?type=&q=&page=}. Thin: query params → input port → a page of
 * the frontend {@code LedgerTxn} shape. No business logic here.
 *
 * <p>Auth: finance / super-admin (PRD §14 — finance owns payouts/ledger/disputes).
 */
@Path("/v1/admin/finance/ledger")
@Produces(MediaType.APPLICATION_JSON)
public class AdminFinanceLedgerResource {

  private final GetLedger getLedger;

  @Inject
  public AdminFinanceLedgerResource(GetLedger getLedger) {
    this.getLedger = getLedger;
  }

  /** GET /v1/admin/finance/ledger — paged ledger, newest first. Finance/super-admin only. */
  @GET
  @RolesAllowed({"finance", "super-admin"})
  public Page<LedgerEntryView> list(
      @QueryParam("type") String typeParam,
      @QueryParam("q") String q,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("20") int size) {

    LedgerType type = LedgerType.fromDisplayOrNull(typeParam);
    return getLedger.list(type, q, new PageRequest(page, size));
  }
}
