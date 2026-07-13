package org.shakvilla.beatzmedia.payments.adapter.out.integration.redde;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for Redde {@code POST /v1/cashout} — pay OUT from the merchant to a recipient (WU-PAY-7,
 * the real disbursement rail replacing WU-PAY-4's ledger-only bookkeeping). Field names are Redde's
 * exact lowercase-concatenated wire format, mirroring {@link ReddeReceiveRequest}. Null fields are
 * omitted ({@code JsonInclude.NON_NULL}) so a MoMo cashout doesn't send empty bank fields and vice
 * versa.
 *
 * <ul>
 *   <li>{@code amount} — cedis, 2 decimal places.
 *   <li>{@code appid} — merchant app id, config {@code beatz.redde.app-id}.
 *   <li>{@code clientreference} — our free-text reference (the withdrawal id).
 *   <li>{@code clienttransid} — our transaction id, ≤10 digits ({@code ReddeClientTransIdGenerator}).
 *   <li>{@code paymentoption} — {@code MTN|AIRTELTIGO|VODAFONE} for MoMo, {@code BANK} for bank.
 *   <li>{@code recipientnumber} — MoMo wallet (momo) or destination account number (bank).
 *   <li>{@code recipientname} — account holder name (bank); omitted for MoMo.
 *   <li>{@code bankcode} — a Ghana bank code token (bank only); omitted for MoMo.
 * </ul>
 *
 * <p><strong>Human gate:</strong> the exact cashout wire field set is confirmed against the Redde
 * sandbox when real credentials are supplied (deploy secrets). This mapping follows the receive/DTO
 * conventions; adjust here only, behind the port, if the sandbox differs — nothing above the adapter
 * depends on these names.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReddeCashoutRequest(
    @JsonProperty("amount") BigDecimal amount,
    @JsonProperty("appid") String appid,
    @JsonProperty("clientreference") String clientreference,
    @JsonProperty("clienttransid") String clienttransid,
    @JsonProperty("description") String description,
    @JsonProperty("nickname") String nickname,
    @JsonProperty("paymentoption") String paymentoption,
    @JsonProperty("recipientnumber") String recipientnumber,
    @JsonProperty("recipientname") String recipientname,
    @JsonProperty("bankcode") String bankcode) {}
