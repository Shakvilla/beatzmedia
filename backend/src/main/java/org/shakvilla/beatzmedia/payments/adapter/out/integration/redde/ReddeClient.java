package org.shakvilla.beatzmedia.payments.adapter.out.integration.redde;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * MicroProfile REST Client for the Redde (Wigal) PSP HTTP API (WU-PAY-6). The base URL is
 * environment-driven ({@code quarkus.rest-client.redde-api.url} — sandbox {@code
 * https://demoapi.reddeonline.com} by default, live {@code https://api.reddeonline.com}). Auth is a
 * static {@code apikey} header per request (the merchant key from Wigal), except {@code /checkout/}
 * where Redde takes the key in the body.
 *
 * <p>This is the only outbound HTTP surface to Redde. The anti-corruption mapping between Redde's
 * wire shapes (lowercase-concatenated fields, {@code OK/PENDING/PROGRESS/PAID/FAILED} statuses) and
 * our domain lives in {@code ReddePaymentGateway}. Cashout ({@code POST /v1/cashout}) is added in
 * WU-PAY-7.
 */
@RegisterRestClient(configKey = "redde-api")
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ReddeClient {

  /** Debit a customer wallet (customer pays merchant). Returns the synchronous first-response. */
  @POST
  @Path("/receive")
  ReddeInitialResponse receive(@HeaderParam("apikey") String apikey, ReddeReceiveRequest body);

  /**
   * Authoritative status of a transaction by Redde's {@code transactionid}. This is the trusted
   * pull-back used to settle charges (ADR-28) — never the callback body alone.
   */
  @GET
  @Path("/status/{transactionid}")
  ReddeStatusResponse status(
      @HeaderParam("apikey") String apikey,
      @HeaderParam("appid") String appid,
      @PathParam("transactionid") String transactionid);

  /** Initiate a hosted checkout (card + MoMo on Redde's page). {@code apikey} is a body field here. */
  @POST
  @Path("/checkout/")
  ReddeCheckoutResponse checkout(ReddeCheckoutRequest body);

  /** Authoritative status of a hosted-checkout transaction by its {@code checkouttransid}. */
  @GET
  @Path("/checkoutstatus/{checkouttransid}")
  ReddeStatusResponse checkoutStatus(
      @HeaderParam("apikey") String apikey,
      @HeaderParam("appid") String appid,
      @PathParam("checkouttransid") String checkouttransid);
}
