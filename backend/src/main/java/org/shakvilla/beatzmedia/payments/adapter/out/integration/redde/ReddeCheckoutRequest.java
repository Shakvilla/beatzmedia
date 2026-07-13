package org.shakvilla.beatzmedia.payments.adapter.out.integration.redde;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for Redde {@code POST /v1/checkout/} — initiate a hosted checkout (card + MoMo on
 * Redde's own page). Unlike {@code /v1/receive}, the {@code apikey} is a BODY field here, not a
 * header. On success Redde returns a {@code checkouturl} the merchant redirects the customer's
 * browser to (WU-PAY-6, card path).
 *
 * <p>{@code successcallback}/{@code failurecallback} are FRONTEND URLs Redde bounces the browser to;
 * they are never trusted server-side for settlement (ADR-28) — settlement is confirmed only via the
 * pull-back-verified webhook or the recon poll's {@code GET /v1/checkoutstatus/{id}}.
 */
public record ReddeCheckoutRequest(
    @JsonProperty("amount") BigDecimal amount,
    @JsonProperty("apikey") String apikey,
    @JsonProperty("appid") String appid,
    @JsonProperty("description") String description,
    @JsonProperty("logolink") String logolink,
    @JsonProperty("merchantname") String merchantname,
    @JsonProperty("clienttransid") String clienttransid,
    @JsonProperty("successcallback") String successcallback,
    @JsonProperty("failurecallback") String failurecallback) {}
