package org.shakvilla.beatzmedia.payments.adapter.out.integration.redde;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Synchronous first-response from Redde {@code POST /v1/receive} (and {@code /v1/cashout}).
 * {@code status} is {@code OK} (accepted for processing) or {@code FAILED} (rejected outright);
 * final PAID/FAILED arrives later via callback or {@code GET /v1/status/{transactionid}} poll.
 * {@code transactionid} is Redde's opaque reference for the charge — stored as our {@code
 * providerRef}. Unknown fields are ignored for forward-compat.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReddeInitialResponse(
    @JsonProperty("status") String status,
    @JsonProperty("reason") String reason,
    @JsonProperty("transactionid") String transactionid,
    @JsonProperty("clienttransid") String clienttransid,
    @JsonProperty("statusdate") String statusdate) {}
