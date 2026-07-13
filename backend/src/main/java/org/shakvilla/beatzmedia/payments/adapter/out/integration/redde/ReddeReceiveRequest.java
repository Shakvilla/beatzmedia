package org.shakvilla.beatzmedia.payments.adapter.out.integration.redde;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for Redde {@code POST /v1/receive} — debit a customer's MoMo wallet (customer pays
 * the merchant). Field names are Redde's exact lowercase-concatenated wire format (WU-PAY-6).
 *
 * <ul>
 *   <li>{@code amount} — cedis, 2 decimal places.
 *   <li>{@code appid} — merchant app id (from Wigal), config {@code beatz.redde.app-id}.
 *   <li>{@code clientreference} — our free-text reference (the order ref); optional.
 *   <li>{@code clienttransid} — our transaction id, MUST be ≤10 digits (see {@code
 *       ReddeClientTransIdGenerator}).
 *   <li>{@code paymentoption} — telco, uppercase: {@code MTN|AIRTELTIGO|VODAFONE}.
 *   <li>{@code walletnumber} — the customer's MSISDN (the {@code PaymentMethodRef.token}).
 * </ul>
 */
public record ReddeReceiveRequest(
    @JsonProperty("amount") BigDecimal amount,
    @JsonProperty("appid") String appid,
    @JsonProperty("clientreference") String clientreference,
    @JsonProperty("clienttransid") String clienttransid,
    @JsonProperty("description") String description,
    @JsonProperty("nickname") String nickname,
    @JsonProperty("paymentoption") String paymentoption,
    @JsonProperty("walletnumber") String walletnumber) {}
