package org.shakvilla.beatzmedia.payments.adapter.in.rest;

import java.math.BigDecimal;
import java.util.Optional;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.payments.application.port.in.DisputeView;
import org.shakvilla.beatzmedia.payments.application.port.in.EscalateDispute;
import org.shakvilla.beatzmedia.payments.application.port.in.GetDispute;
import org.shakvilla.beatzmedia.payments.application.port.in.RefundDispute;
import org.shakvilla.beatzmedia.payments.application.port.in.RejectDispute;
import org.shakvilla.beatzmedia.payments.domain.DisputeId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.MissingIdempotencyKeyException;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * REST resource for admin finance dispute adjudication (LLFR-PAYMENTS-04.1 / 04.2 / 04.3), backing:
 *
 * <ul>
 *   <li>{@code GET /v1/admin/finance/disputes/:id} — dispute detail + timeline (04.1)
 *   <li>{@code POST /v1/admin/finance/disputes/:id/refund} — refund (revoke + clawback, INV-9) (04.2)
 *   <li>{@code POST /v1/admin/finance/disputes/:id/reject} — reject with a reason (04.3)
 *   <li>{@code POST /v1/admin/finance/disputes/:id/escalate} — escalate for review (04.3)
 * </ul>
 *
 * <p>Thin: DTO → command → input port → the frontend {@code Dispute} shape. No business logic here.
 *
 * <p><strong>Auth (RBAC, WU-IDN-4).</strong> {@code finance} / {@code super-admin} only — finance
 * owns payouts/ledger/disputes (PRD §14). There is deliberately NO client/fan-callable money endpoint:
 * a refund is either admin-adjudicated here or forced by a signature-verified provider chargeback.
 *
 * <p>The refund endpoint is a money POST: an {@code Idempotency-Key} header is REQUIRED (a retried
 * refund is exactly one clawback + one revocation). Reject/escalate move no money and do not require
 * a key.
 */
@Path("/v1/admin/finance/disputes")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"finance", "super-admin"})
public class AdminFinanceDisputesResource {

  private final GetDispute getDispute;
  private final RefundDispute refundDispute;
  private final RejectDispute rejectDispute;
  private final EscalateDispute escalateDispute;
  private final JsonWebToken jwt;

  @Inject
  public AdminFinanceDisputesResource(
      GetDispute getDispute,
      RefundDispute refundDispute,
      RejectDispute rejectDispute,
      EscalateDispute escalateDispute,
      JsonWebToken jwt) {
    this.getDispute = getDispute;
    this.refundDispute = refundDispute;
    this.rejectDispute = rejectDispute;
    this.escalateDispute = escalateDispute;
    this.jwt = jwt;
  }

  /** GET /v1/admin/finance/disputes/:id — detail + timeline (04.1). */
  @GET
  @Path("/{id}")
  public DisputeView get(@PathParam("id") String id) {
    return getDispute.get(new DisputeId(id));
  }

  /** POST /v1/admin/finance/disputes/:id/refund — refund (revoke + clawback, INV-9) (04.2). */
  @POST
  @Path("/{id}/refund")
  @Consumes(MediaType.APPLICATION_JSON)
  public DisputeView refund(
      @PathParam("id") String id,
      @HeaderParam("Idempotency-Key") String idempotencyKey,
      RefundRequest body) {
    String key = requireKey(idempotencyKey);
    RefundRequest req = body == null ? new RefundRequest(null, null) : body;
    Optional<Money> amount =
        req.amount() == null || req.amount().amount() == null
            ? Optional.empty()
            : Optional.of(Money.ofCedis(req.amount().amount(), Currency.GHS));
    return refundDispute.refund(
        jwt.getSubject(),
        new DisputeId(id),
        new RefundDispute.Command(amount, req.reason()),
        new IdempotencyKey(key));
  }

  /** POST /v1/admin/finance/disputes/:id/reject — reject with a reason (04.3). */
  @POST
  @Path("/{id}/reject")
  @Consumes(MediaType.APPLICATION_JSON)
  public DisputeView reject(@PathParam("id") String id, RejectRequest body) {
    String reason = body == null ? null : body.reason();
    return rejectDispute.reject(jwt.getSubject(), new DisputeId(id), reason);
  }

  /** POST /v1/admin/finance/disputes/:id/escalate — escalate for review (04.3). */
  @POST
  @Path("/{id}/escalate")
  public DisputeView escalate(@PathParam("id") String id) {
    return escalateDispute.escalate(jwt.getSubject(), new DisputeId(id));
  }

  private static String requireKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new MissingIdempotencyKeyException();
    }
    return idempotencyKey;
  }

  /**
   * Refund request body: optional partial {@code amount} (wire Money {@code { amount, currency }},
   * INV-11) + required {@code reason}. Omitting {@code amount} refunds the full dispute amount.
   */
  public record RefundRequest(MoneyBody amount, String reason) {}

  /** Wire money shape {@code { amount: <decimal cedis>, currency: "GHS" }} (matches the frontend). */
  public record MoneyBody(BigDecimal amount, String currency) {}

  /** Reject request body: the required {@code reason}. */
  public record RejectRequest(@NotBlank String reason) {}
}
