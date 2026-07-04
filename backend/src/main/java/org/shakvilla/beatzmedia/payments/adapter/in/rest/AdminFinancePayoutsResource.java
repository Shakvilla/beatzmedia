package org.shakvilla.beatzmedia.payments.adapter.in.rest;

import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.payments.application.port.in.ListPendingPayouts;
import org.shakvilla.beatzmedia.payments.application.port.in.PayoutBatchView;
import org.shakvilla.beatzmedia.payments.application.port.in.PayoutTxnView;
import org.shakvilla.beatzmedia.payments.application.port.in.PendingPayoutView;
import org.shakvilla.beatzmedia.payments.application.port.in.RunWeeklyPayouts;
import org.shakvilla.beatzmedia.payments.application.port.in.SendSinglePayout;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.MissingIdempotencyKeyException;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalId;

/**
 * REST resource for admin finance payout runs (LLFR-PAYMENTS-03.3 / 03.4), backing {@code GET
 * /v1/admin/finance/payouts} (pending list), {@code POST /v1/admin/finance/payouts/run-weekly} and
 * {@code POST /v1/admin/finance/payouts/:id/send}. Thin: input port → the frontend shape.
 *
 * <p><strong>Auth (RBAC, WU-IDN-4).</strong> {@code finance} / {@code super-admin} only — finance
 * owns payouts/ledger/disputes (PRD §14). The scope-guard is on every method.
 *
 * <p>The run/send endpoints are money POSTs: an {@code Idempotency-Key} header is REQUIRED. A retried
 * run cannot double-pay (per-withdrawal exactly-once guard); a single send blocks on KYC (409).
 */
@Path("/v1/admin/finance/payouts")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"finance", "super-admin"})
public class AdminFinancePayoutsResource {

  private final ListPendingPayouts listPendingPayouts;
  private final RunWeeklyPayouts runWeeklyPayouts;
  private final SendSinglePayout sendSinglePayout;
  private final JsonWebToken jwt;

  @Inject
  public AdminFinancePayoutsResource(
      ListPendingPayouts listPendingPayouts,
      RunWeeklyPayouts runWeeklyPayouts,
      SendSinglePayout sendSinglePayout,
      JsonWebToken jwt) {
    this.listPendingPayouts = listPendingPayouts;
    this.runWeeklyPayouts = runWeeklyPayouts;
    this.sendSinglePayout = sendSinglePayout;
    this.jwt = jwt;
  }

  /** GET /v1/admin/finance/payouts — payable withdrawals (ready | kyc_pending). */
  @GET
  public List<PendingPayoutView> pending() {
    return listPendingPayouts.list();
  }

  /** POST /v1/admin/finance/payouts/run-weekly — pay all ready, KYC-verified withdrawals. */
  @POST
  @Path("/run-weekly")
  public PayoutBatchView runWeekly(@HeaderParam("Idempotency-Key") String idempotencyKey) {
    String key = requireKey(idempotencyKey);
    return runWeeklyPayouts.runWeekly(jwt.getSubject(), new IdempotencyKey(key));
  }

  /** POST /v1/admin/finance/payouts/:id/send — send a single payout (blocks on KYC). */
  @POST
  @Path("/{id}/send")
  public Response send(
      @PathParam("id") String withdrawalId,
      @HeaderParam("Idempotency-Key") String idempotencyKey) {
    String key = requireKey(idempotencyKey);
    PayoutTxnView view =
        sendSinglePayout.send(
            jwt.getSubject(), new WithdrawalId(withdrawalId), new IdempotencyKey(key));
    return Response.ok(view).build();
  }

  private static String requireKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new MissingIdempotencyKeyException();
    }
    return idempotencyKey;
  }
}
