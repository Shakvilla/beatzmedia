package org.shakvilla.beatzmedia.payments.adapter.out.integration.redde;

/**
 * Plain port for the Redde (Wigal) PSP HTTP API (WU-PAY-6) — the type {@code ReddePaymentGateway}
 * depends on and tests fake. It carries <strong>no</strong> JAX-RS annotations on purpose: the actual
 * HTTP mapping + {@code @RegisterRestClient} live on {@link ReddeRestClient}, produced as a {@code
 * ReddeClient} bean by {@link ReddeClientProvider}.
 *
 * <p><strong>Why the split:</strong> a concrete class implementing a JAX-RS-annotated interface is
 * picked up by RESTEasy Reactive as a <em>server</em> resource (inherited {@code @Path}), which would
 * make a test fake collide with real endpoints at deploy time. Keeping this port annotation-free
 * avoids that. Cashout ({@code POST /v1/cashout}) is added in WU-PAY-7.
 */
public interface ReddeClient {

  /** Debit a customer wallet (customer pays merchant). Returns the synchronous first-response. */
  ReddeInitialResponse receive(String apikey, ReddeReceiveRequest body);

  /**
   * Pay OUT from the merchant to a recipient (MoMo or bank), i.e. a creator withdrawal (WU-PAY-7).
   * Returns the synchronous first-response; the confirmed PAID/FAILED arrives later via the cashout
   * callback or a {@link #status} pull-back.
   */
  ReddeInitialResponse cashout(String apikey, ReddeCashoutRequest body);

  /**
   * Authoritative status of a transaction by Redde's {@code transactionid}. This is the trusted
   * pull-back used to settle charges (ADR-28) — never the callback body alone.
   */
  ReddeStatusResponse status(String apikey, String appid, String transactionid);

  /** Initiate a hosted checkout (card + MoMo on Redde's page). {@code apikey} is a body field here. */
  ReddeCheckoutResponse checkout(ReddeCheckoutRequest body);

  /** Authoritative status of a hosted-checkout transaction by its {@code checkouttransid}. */
  ReddeStatusResponse checkoutStatus(String apikey, String appid, String checkouttransid);
}
