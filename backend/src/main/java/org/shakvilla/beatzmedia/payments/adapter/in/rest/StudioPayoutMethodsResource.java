package org.shakvilla.beatzmedia.payments.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.payments.application.port.in.AddPayoutMethod;
import org.shakvilla.beatzmedia.payments.application.port.in.PayoutMethodView;
import org.shakvilla.beatzmedia.payments.application.port.in.RemovePayoutMethod;
import org.shakvilla.beatzmedia.payments.application.port.in.SetDefaultPayoutMethod;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.MethodKind;
import org.shakvilla.beatzmedia.payments.domain.PayoutMethodId;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * REST resource for creator payout methods (LLFR-PAYMENTS-03.1), backing {@code POST/DELETE
 * /v1/studio/payout-methods} and {@code PATCH /v1/studio/payout-methods/:id/default}. Thin: JWT
 * subject → {@link AccountId} → input port. Ownership-scoped in the service. Auth: {@code artist}
 * (own studio).
 */
@Path("/v1/studio/payout-methods")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("artist")
public class StudioPayoutMethodsResource {

  private final AddPayoutMethod addPayoutMethod;
  private final RemovePayoutMethod removePayoutMethod;
  private final SetDefaultPayoutMethod setDefaultPayoutMethod;
  private final JsonWebToken jwt;

  @Inject
  public StudioPayoutMethodsResource(
      AddPayoutMethod addPayoutMethod,
      RemovePayoutMethod removePayoutMethod,
      SetDefaultPayoutMethod setDefaultPayoutMethod,
      JsonWebToken jwt) {
    this.addPayoutMethod = addPayoutMethod;
    this.removePayoutMethod = removePayoutMethod;
    this.setDefaultPayoutMethod = setDefaultPayoutMethod;
    this.jwt = jwt;
  }

  /** POST /v1/studio/payout-methods — add a cash-out destination. */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response add(AddBody body) {
    if (body == null) {
      throw new ValidationException("request body is required");
    }
    AccountId creator = new AccountId(jwt.getSubject());
    MethodKind kind = parseKind(body.kind());
    PayoutMethodView view =
        addPayoutMethod.add(
            creator, new AddPayoutMethod.Command(body.label(), body.detail(), kind));
    return Response.status(Response.Status.CREATED).entity(view).build();
  }

  /** DELETE /v1/studio/payout-methods/:id — remove a cash-out destination (own only). */
  @DELETE
  @Path("/{id}")
  public Response remove(@PathParam("id") String id) {
    AccountId creator = new AccountId(jwt.getSubject());
    removePayoutMethod.remove(creator, new PayoutMethodId(id));
    return Response.noContent().build();
  }

  /** PATCH /v1/studio/payout-methods/:id/default — make this method the default. */
  @PATCH
  @Path("/{id}/default")
  public PayoutMethodView setDefault(@PathParam("id") String id) {
    AccountId creator = new AccountId(jwt.getSubject());
    return setDefaultPayoutMethod.setDefault(creator, new PayoutMethodId(id));
  }

  private static MethodKind parseKind(String value) {
    try {
      return MethodKind.fromWire(value);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("unsupported payout method kind: " + value, "kind");
    }
  }

  /** Request body for adding a method: {@code { label, detail, kind }}. */
  public record AddBody(String label, String detail, String kind) {}
}
