package org.shakvilla.beatzmedia.payments.adapter.out.integration.redde;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.payments.adapter.out.integration.PspGateway;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentEventType;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.PayoutDestination;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.ProviderException;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalId;
import org.shakvilla.beatzmedia.platform.domain.Money;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
@PspGateway(PspGateway.Vendor.REDDE)
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
      ReddeClient client,
      ReddeClientTransIdGenerator clientTransIds,
      ObjectMapper objectMapper,
      @ConfigProperty(name = "beatz.redde.api-key") Optional<String> apiKey,
      @ConfigProperty(name = "beatz.redde.app-id") Optional<String> appId,
      @ConfigProperty(name = "beatz.redde.merchant-name", defaultValue = "BeatzClik") String merchantName,
      @ConfigProperty(name = "beatz.redde.logo-link") Optional<String> logoLink,
      @ConfigProperty(name = "beatz.redde.checkout-success-url") Optional<String> checkoutSuccessUrl,
      @ConfigProperty(name = "beatz.redde.checkout-failure-url") Optional<String> checkoutFailureUrl) {
    this.client = client;
    this.clientTransIds = clientTransIds;
    this.objectMapper = objectMapper;
    // The credential/URL properties default to an EMPTY value (human gate), which SmallRye rejects
    // for a plain String @ConfigProperty ("Failed to load config value ... for java.lang.String").
    // Inject as Optional (empty value -> Optional.empty()) and resolve to "" so the app boots with
    // Redde unconfigured; the fail-closed guard then blocks calls until real secrets are supplied.
    this.apiKey = apiKey.orElse("");
    this.appId = appId.orElse("");
    this.merchantName = merchantName;
    this.logoLink = logoLink.orElse("");
    this.checkoutSuccessUrl = checkoutSuccessUrl.orElse("");
    this.checkoutFailureUrl = checkoutFailureUrl.orElse("");
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
  public DisburseHandle disburse(
      Provider provider, WithdrawalId withdrawalId, Money amount, PayoutDestination destination) {
    requireConfigured();
    if (amount == null || !amount.isPositive()) {
      throw new ProviderException("Redde rejected cashout: amount must be positive");
    }
    if (destination == null) {
      throw new ProviderException("Redde rejected cashout: destination is required");
    }
    ReddeCashoutRequest body = cashoutRequest(withdrawalId, amount, destination);

    ReddeInitialResponse resp;
    try {
      resp = client.cashout(apiKey, body);
    } catch (RuntimeException e) {
      throw new ProviderException("Redde cashout call failed: " + e.getMessage());
    }
    if (resp == null || !"OK".equalsIgnoreCase(nullToEmpty(resp.status()))) {
      throw new ProviderException("Redde rejected cashout: " + reasonOf(resp));
    }
    return new DisburseHandle(requireRef(resp.transactionid()));
  }

  /** Build the cashout body for a structured destination: MoMo telco+wallet, or BANK code+account. */
  private ReddeCashoutRequest cashoutRequest(
      WithdrawalId withdrawalId, Money amount, PayoutDestination destination) {
    String clientRef = withdrawalId.value();
    String description = "BeatzClik payout " + clientRef;
    return switch (destination) {
      case PayoutDestination.Momo m ->
          new ReddeCashoutRequest(
              amount.toCedis(),
              appId,
              clientRef,
              clientTransIds.next(),
              description,
              merchantName,
              momoPaymentOption(m.network()),
              m.walletNumber(),
              null,
              null);
      case PayoutDestination.Bank b ->
          new ReddeCashoutRequest(
              amount.toCedis(),
              appId,
              clientRef,
              clientTransIds.next(),
              description,
              merchantName,
              "BANK",
              b.accountNumber(),
              b.accountName(),
              b.bankCode().name());
    };
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
