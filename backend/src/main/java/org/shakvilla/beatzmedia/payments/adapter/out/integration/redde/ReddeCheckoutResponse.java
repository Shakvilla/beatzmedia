package org.shakvilla.beatzmedia.payments.adapter.out.integration.redde;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from Redde {@code POST /v1/checkout/}. On {@code status == OK} it carries the {@code
 * checkouturl} to redirect the customer's browser to, plus {@code checkouttransid} (used later by
 * {@code GET /v1/checkoutstatus/{id}} to confirm settlement server-side). On {@code FAILED},
 * {@code reason} explains why and the url fields are null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReddeCheckoutResponse(
    @JsonProperty("status") String status,
    @JsonProperty("reason") String reason,
    @JsonProperty("referenceid") String referenceid,
    @JsonProperty("responsetoken") String responsetoken,
    @JsonProperty("checkouturl") String checkouturl,
    @JsonProperty("checkouttransid") String checkouttransid) {}
