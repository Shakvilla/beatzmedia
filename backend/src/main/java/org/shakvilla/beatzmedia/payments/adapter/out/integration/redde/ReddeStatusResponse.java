package org.shakvilla.beatzmedia.payments.adapter.out.integration.redde;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from Redde {@code GET /v1/status/{transactionid}} — the authoritative, authenticated
 * view of a transaction's state. This is the source of truth for the "verify by pull-back" trust
 * model (ADR-28): a callback is only trusted if this live pull agrees with it.
 *
 * <p>{@code status} is one of Redde's lifecycle tokens: {@code OK}/{@code PENDING}/{@code PROGRESS}
 * (in-flight) or {@code PAID}/{@code FAILED} (final). See {@link ReddeStatus} for the mapping onto
 * our {@code PaymentEventType}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReddeStatusResponse(
    @JsonProperty("status") String status,
    @JsonProperty("reason") String reason,
    @JsonProperty("transactionid") String transactionid,
    @JsonProperty("clienttransid") String clienttransid,
    @JsonProperty("clientreference") String clientreference,
    @JsonProperty("brandtransid") String brandtransid,
    @JsonProperty("statusdate") String statusdate) {}
