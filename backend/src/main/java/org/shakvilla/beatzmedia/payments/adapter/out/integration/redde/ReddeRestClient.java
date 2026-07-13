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
 * The MicroProfile REST Client for Redde (WU-PAY-6): {@link ReddeClient} plus the JAX-RS HTTP mapping
 * and {@code @RegisterRestClient}. Base URL is {@code quarkus.rest-client.redde-api.url} (sandbox
 * {@code https://demoapi.reddeonline.com} by default, live {@code https://api.reddeonline.com}). Auth
 * is a static {@code apikey} header per request, except {@code /checkout/} where Redde takes the key
 * in the body.
 *
 * <p>Kept separate from the plain {@link ReddeClient} port so a test fake of {@code ReddeClient} is
 * never mistaken for a server resource (see {@link ReddeClient} javadoc). Injected as a
 * {@code ReddeClient} bean via {@link ReddeClientProvider}.
 */
@RegisterRestClient(configKey = "redde-api")
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ReddeRestClient extends ReddeClient {

  @Override
  @POST
  @Path("/receive")
  ReddeInitialResponse receive(@HeaderParam("apikey") String apikey, ReddeReceiveRequest body);

  @Override
  @POST
  @Path("/cashout")
  ReddeInitialResponse cashout(@HeaderParam("apikey") String apikey, ReddeCashoutRequest body);

  @Override
  @GET
  @Path("/status/{transactionid}")
  ReddeStatusResponse status(
      @HeaderParam("apikey") String apikey,
      @HeaderParam("appid") String appid,
      @PathParam("transactionid") String transactionid);

  @Override
  @POST
  @Path("/checkout/")
  ReddeCheckoutResponse checkout(ReddeCheckoutRequest body);

  @Override
  @GET
  @Path("/checkoutstatus/{checkouttransid}")
  ReddeStatusResponse checkoutStatus(
      @HeaderParam("apikey") String apikey,
      @HeaderParam("appid") String appid,
      @PathParam("checkouttransid") String checkouttransid);
}
