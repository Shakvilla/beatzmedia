package org.shakvilla.beatzmedia.payments.adapter.out.integration.redde;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentEventType;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.ProviderException;
import org.shakvilla.beatzmedia.platform.domain.Money;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.Identifier;

/**
 * Real {@link PaymentGateway} backed by the Redde (Wigal) PSP (WU-PAY-6, ADR-27). One vendor covers
 * all rails: MoMo (mtn/telecel/airteltigo) charges directly via {@code POST /v1/receive}; card goes
 * through a hosted-checkout redirect ({@code POST /v1/checkout/}). Selected at runtime by
 * {@code FeatureKey.PSP_REDDE} through {@code PaymentGatewayRouter}.
 *
 * <p><strong>Trust model (ADR-28):</strong> Redde callbacks carry no HMAC. Settlement is confirmed
 * by an authenticated pull-back — {@code GET /v1/status/{transactionid}} (or {@code
 * /v1/checkoutstatus/{id}} for card) using our {@code apikey}. {@link #queryStatus} is that
 * pull-back and is the single source of truth for both the recon poll and the dedicated Redde
 * receive-webhook service ({@code HandleReddeReceiptService}).
 *
 * <p><strong>Human gate:</strong> {@code beatz.redde.api-key}/{@code app-id} are deploy secrets. If
 * blank, every network method fails closed with a {@link ProviderException} even when the flag is on,
 * so flipping {@code PSP_REDDE} without credentials cannot silently mis-charge.
 */
@ApplicationScoped
@Identifier("redde")
public class ReddePaymentGateway implements PaymentGateway {

  private final ReddeClient client;
  private final ReddeClientTransIdGenerator clientTransIds;
  private final ObjectMapper objectMapper;
  private final String apiKey;
  private final String appId;
  private final String merchantName;
  private final String logoLink;
  private final String checkoutSuccessUrl;
  private final String checkoutFailureUrl;

  @Inject
  public ReddePaymentGateway(
      @RestClient ReddeClient client,
      ReddeClientTransIdGenerator clientTransIds,
      ObjectMapper objectMapper,
      @ConfigProperty(name = "beatz.redde.api-key") String apiKey,
      @ConfigProperty(name = "beatz.redde.app-id") String appId,
      @ConfigProperty(name = "beatz.redde.merchant-name", defaultValue = "BeatzClik") String merchantName,
      @ConfigProperty(name = "beatz.redde.logo-link", defaultValue = "") String logoLink,
      @ConfigProperty(name = "beatz.redde.checkout-success-url", defaultValue = "") String checkoutSuccessUrl,
      @ConfigProperty(name = "beatz.redde.checkout-failure-url", defaultValue = "") String checkoutFailureUrl) {
    this.client = client;
    this.clientTransIds = clientTransIds;
    this.objectMapper = objectMapper;
    this.apiKey = apiKey;
    this.appId = appId;
    this.merchantName = merchantName;
    this.logoLink = logoLink;
    this.checkoutSuccessUrl = checkoutSuccessUrl;
    this.checkoutFailureUrl = checkoutFailureUrl;
  }

  @Override
  public boolean supportsDirectCharge(Provider provider) {
    // Redde can charge MoMo (and, conceptually, bank) directly; card must use hosted checkout.
    return provider != Provider.card;
  }

  @Override
  public ChargeHandle initiate(
      Provider provider, OrderRef ref, Money amount, PaymentMethodRef method) {
    requireConfigured();
    if (amount == null || !amount.isPositive()) {
      throw new ProviderException("Redde rejected charge: amount must be positive");
    }
    String paymentOption = momoPaymentOption(provider);
    ReddeReceiveRequest body =
        new ReddeReceiveRequest(
            amount.toCedis(),
            appId,
            ref.value(),
            clientTransIds.next(),
            "BeatzClik order " + ref.value(),
            merchantName,
            paymentOption,
            method.token());

    ReddeInitialResponse resp;
    try {
      resp = client.receive(apiKey, body);
    } catch (RuntimeException e) {
      throw new ProviderException("Redde receive call failed: " + e.getMessage());
    }
    if (resp == null || !"OK".equalsIgnoreCase(nullToEmpty(resp.status()))) {
      throw new ProviderException("Redde rejected charge: " + reasonOf(resp));
    }
    return new ChargeHandle(requireRef(resp.transactionid()));
  }

  @Override
  public CheckoutHandle initiateCheckout(OrderRef ref, Money amount) {
    requireConfigured();
    if (amount == null || !amount.isPositive()) {
      throw new ProviderException("Redde rejected checkout: amount must be positive");
    }
    ReddeCheckoutRequest body =
        new ReddeCheckoutRequest(
            amount.toCedis(),
            apiKey,
            appId,
            "BeatzClik order " + ref.value(),
            logoLink,
            merchantName,
            clientTransIds.next(),
            checkoutSuccessUrl,
            checkoutFailureUrl);

    ReddeCheckoutResponse resp;
    try {
      resp = client.checkout(body);
    } catch (RuntimeException e) {
      throw new ProviderException("Redde checkout call failed: " + e.getMessage());
    }
    if (resp == null || !"OK".equalsIgnoreCase(nullToEmpty(resp.status()))) {
      throw new ProviderException("Redde rejected checkout: " + reasonOf(resp));
    }
    return new CheckoutHandle(requireRef(resp.checkouttransid()), requireRef(resp.checkouturl()));
  }

  @Override
  public ProviderStatus queryStatus(Provider provider, String providerRef) {
    requireConfigured();
    ReddeStatusResponse resp;
    try {
      resp =
          provider == Provider.card
              ? client.checkoutStatus(apiKey, appId, providerRef)
              : client.status(apiKey, appId, providerRef);
    } catch (RuntimeException e) {
      // Rail unreachable / unknown ref — the port contract says throw so the poll leaves it pending.
      throw new ProviderException("Redde status query failed: " + e.getMessage());
    }
    return toProviderStatus(resp);
  }

  /**
   * Defensive pull-back verification for the generic {@code /v1/payments/webhooks/{provider}} path
   * (ADR-28). Redde's real callbacks arrive at the dedicated {@code /redde/receive} path handled by
   * {@code HandleReddeReceiptService} (which trusts via {@link #queryStatus} directly), so this is a
   * fallback: it extracts the {@code transactionid} from the raw body and returns {@code true} only
   * if an authenticated status pull confirms a terminal outcome for it. Signature is ignored (Redde
   * sends none).
   */
  @Override
  public boolean verifySignature(Provider provider, String signature, byte[] rawBody) {
    if (rawBody == null) {
      return false;
    }
    String transactionId = extractTransactionId(rawBody);
    if (transactionId == null || transactionId.isBlank()) {
      return false;
    }
    try {
      ProviderStatus pulled = queryStatus(provider, transactionId);
      return pulled.outcome() == PaymentEventType.SETTLED
          || pulled.outcome() == PaymentEventType.FAILED;
    } catch (ProviderException e) {
      return false;
    }
  }

  // ---- mapping helpers --------------------------------------------------

  /** Map a Redde lifecycle token onto our settlement outcome. */
  static ProviderStatus toProviderStatus(ReddeStatusResponse resp) {
    String status = resp == null ? null : resp.status();
    return switch (nullToEmpty(status).trim().toUpperCase()) {
      case "PAID" -> ProviderStatus.settled();
      case "FAILED" -> ProviderStatus.failed(resp == null ? "failed" : reasonOf(resp));
      default -> ProviderStatus.pending(); // OK / PENDING / PROGRESS / unknown → keep waiting
    };
  }

  /** MoMo rail → Redde {@code paymentoption}. Vodafone Cash was rebranded Telecel Cash in Ghana. */
  private static String momoPaymentOption(Provider provider) {
    return switch (provider) {
      case mtn -> "MTN";
      case telecel -> "VODAFONE";
      case airteltigo -> "AIRTELTIGO";
      default ->
          throw new ProviderException(
              "Redde direct debit supports MoMo only (mtn/telecel/airteltigo); got " + provider);
    };
  }

  private String extractTransactionId(byte[] rawBody) {
    try {
      JsonNode node = objectMapper.readTree(rawBody);
      JsonNode txid = node.get("transactionid");
      return txid == null || txid.isNull() ? null : txid.asText();
    } catch (Exception e) {
      return null;
    }
  }

  private void requireConfigured() {
    if (isBlank(apiKey) || isBlank(appId)) {
      throw new ProviderException(
          "Redde is not configured (BEATZ_REDDE_API_KEY / BEATZ_REDDE_APP_ID missing)");
    }
  }

  private static String requireRef(String value) {
    if (value == null || value.isBlank()) {
      throw new ProviderException("Redde returned a blank reference");
    }
    return value;
  }

  private static String reasonOf(ReddeInitialResponse resp) {
    return resp == null || isBlank(resp.reason()) ? "unknown" : resp.reason();
  }

  private static String reasonOf(ReddeCheckoutResponse resp) {
    return resp == null || isBlank(resp.reason()) ? "unknown" : resp.reason();
  }

  private static String reasonOf(ReddeStatusResponse resp) {
    return resp == null || isBlank(resp.reason()) ? "failed" : resp.reason();
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
