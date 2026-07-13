package org.shakvilla.beatzmedia.payments.adapter.out.integration.redde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway.ChargeHandle;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway.CheckoutHandle;
import org.shakvilla.beatzmedia.payments.domain.OrderRef;
import org.shakvilla.beatzmedia.payments.domain.PaymentEventType;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.ProviderException;
import org.shakvilla.beatzmedia.payments.fakes.FakeReddeClient;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link ReddePaymentGateway} (WU-PAY-6): the anti-corruption mapping between Redde's
 * wire shapes and our domain, the "verify by pull-back" trust (ADR-28), and fail-closed on missing
 * credentials.
 */
@Tag("unit")
class ReddePaymentGatewayTest {

  private static final Money TEN_CEDIS = Money.ofMinor(1000, Currency.GHS);
  private static final OrderRef ORDER = new OrderRef("BZ-2026-1");

  private final FakeReddeClient client = new FakeReddeClient();

  private ReddePaymentGateway gateway(String apiKey, String appId) {
    ReddeClientTransIdGenerator ids =
        new ReddeClientTransIdGenerator(null) {
          @Override
          public String next() {
            return "42";
          }
        };
    return new ReddePaymentGateway(
        client,
        ids,
        new ObjectMapper(),
        Optional.of(apiKey),
        Optional.of(appId),
        "BeatzClik",
        Optional.of("logo"),
        Optional.of("https://ok"),
        Optional.of("https://no"));
  }

  private ReddePaymentGateway gateway() {
    return gateway("test-key", "app-1");
  }

  private static PaymentMethodRef momo(Provider provider, String wallet) {
    return new PaymentMethodRef(
        provider, org.shakvilla.beatzmedia.payments.domain.MethodKind.momo, wallet);
  }

  @Test
  void initiateMapsTelecelToVodafoneAndReturnsTransactionId() {
    client.onReceive(new ReddeInitialResponse("OK", "accepted", "103046", "42", "2026-07-13"));

    ChargeHandle handle =
        gateway().initiate(Provider.telecel, ORDER, TEN_CEDIS, momo(Provider.telecel, "233241234567"));

    assertEquals("103046", handle.providerRef());
    // paymentoption maps telecel -> VODAFONE (Vodafone Cash was rebranded Telecel Cash)
    assertEquals("VODAFONE", client.receiveRequests.get(0).paymentoption());
    assertEquals("233241234567", client.receiveRequests.get(0).walletnumber());
    assertEquals(new java.math.BigDecimal("10.00"), client.receiveRequests.get(0).amount());
  }

  @Test
  void initiateThrowsProviderExceptionOnFailedStatus() {
    client.onReceive(new ReddeInitialResponse("FAILED", "insufficient funds", null, "42", "d"));
    assertThrows(
        ProviderException.class,
        () -> gateway().initiate(Provider.mtn, ORDER, TEN_CEDIS, momo(Provider.mtn, "0244")));
  }

  @Test
  void initiateWrapsClientErrorAsProviderException() {
    client.onReceiveThrow(new RuntimeException("connection refused"));
    assertThrows(
        ProviderException.class,
        () -> gateway().initiate(Provider.mtn, ORDER, TEN_CEDIS, momo(Provider.mtn, "0244")));
  }

  @Test
  void initiateRejectsNonMomoRail() {
    assertThrows(
        ProviderException.class,
        () -> gateway().initiate(Provider.bank, ORDER, TEN_CEDIS, momo(Provider.bank, "acct")));
  }

  @Test
  void queryStatusMapsReddeTokens() {
    client.onStatus(status("PAID"));
    assertEquals(PaymentEventType.SETTLED, gateway().queryStatus(Provider.mtn, "103046").outcome());

    client.onStatus(status("FAILED"));
    assertEquals(PaymentEventType.FAILED, gateway().queryStatus(Provider.mtn, "103046").outcome());

    client.onStatus(status("PROGRESS"));
    assertEquals(PaymentEventType.PENDING, gateway().queryStatus(Provider.mtn, "103046").outcome());
  }

  @Test
  void queryStatusUsesCheckoutEndpointForCard() {
    client.onStatus(status("PAID"));
    gateway().queryStatus(Provider.card, "54");
    assertEquals(1, client.checkoutStatusLookups.size());
    assertEquals("54", client.checkoutStatusLookups.get(0));
    assertTrue(client.statusLookups.isEmpty());
  }

  @Test
  void queryStatusThrowsWhenRailUnreachable() {
    client.onStatusThrow(new RuntimeException("timeout"));
    assertThrows(ProviderException.class, () -> gateway().queryStatus(Provider.mtn, "x"));
  }

  @Test
  void initiateCheckoutReturnsUrlAndTransId() {
    client.onCheckout(
        new ReddeCheckoutResponse("OK", "accepted", "ref", "tok", "https://checkout.redde/xyz", "54"));

    CheckoutHandle handle = gateway().initiateCheckout(ORDER, TEN_CEDIS);

    assertEquals("54", handle.checkoutTransId());
    assertEquals("https://checkout.redde/xyz", handle.checkoutUrl());
    assertEquals("test-key", client.checkoutRequests.get(0).apikey()); // apikey is a body field here
  }

  @Test
  void supportsDirectChargeIsFalseOnlyForCard() {
    assertTrue(gateway().supportsDirectCharge(Provider.mtn));
    assertFalse(gateway().supportsDirectCharge(Provider.card));
  }

  @Test
  void verifySignaturePullsBackTrueOnTerminalFalseOnPending() {
    byte[] body = "{\"transactionid\":\"103046\",\"status\":\"PAID\"}".getBytes();

    client.onStatus(status("PAID"));
    assertTrue(gateway().verifySignature(Provider.mtn, null, body));

    client.onStatus(status("PROGRESS"));
    assertFalse(gateway().verifySignature(Provider.mtn, null, body));
  }

  @Test
  void verifySignatureFalseOnBodyWithoutTransactionId() {
    assertFalse(gateway().verifySignature(Provider.mtn, null, "{}".getBytes()));
  }

  @Test
  void failsClosedWhenApiKeyBlank() {
    ProviderException e =
        assertThrows(
            ProviderException.class,
            () ->
                gateway("", "app-1")
                    .initiate(Provider.mtn, ORDER, TEN_CEDIS, momo(Provider.mtn, "0244")));
    assertTrue(e.getMessage().contains("not configured"));
  }

  private static ReddeStatusResponse status(String status) {
    return new ReddeStatusResponse(status, "reason", "103046", "42", "ref", "brand", "2026-07-13");
  }
}
