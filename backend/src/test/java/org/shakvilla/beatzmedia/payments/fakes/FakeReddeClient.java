package org.shakvilla.beatzmedia.payments.fakes;

import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.payments.adapter.out.integration.redde.ReddeCashoutRequest;
import org.shakvilla.beatzmedia.payments.adapter.out.integration.redde.ReddeCheckoutRequest;
import org.shakvilla.beatzmedia.payments.adapter.out.integration.redde.ReddeCheckoutResponse;
import org.shakvilla.beatzmedia.payments.adapter.out.integration.redde.ReddeClient;
import org.shakvilla.beatzmedia.payments.adapter.out.integration.redde.ReddeInitialResponse;
import org.shakvilla.beatzmedia.payments.adapter.out.integration.redde.ReddeReceiveRequest;
import org.shakvilla.beatzmedia.payments.adapter.out.integration.redde.ReddeStatusResponse;

/**
 * Configurable in-memory {@link ReddeClient} fake for tests (no WireMock/Mockito in this repo). Prime
 * the canned responses (or an error) per endpoint and inspect the captured requests. A {@code null}
 * response with a {@code null} error throws {@link IllegalStateException} so a test never silently
 * hits an unconfigured path.
 */
public class FakeReddeClient implements ReddeClient {

  public final List<ReddeReceiveRequest> receiveRequests = new ArrayList<>();
  public final List<ReddeCashoutRequest> cashoutRequests = new ArrayList<>();
  public final List<ReddeCheckoutRequest> checkoutRequests = new ArrayList<>();
  public final List<String> statusLookups = new ArrayList<>();
  public final List<String> checkoutStatusLookups = new ArrayList<>();

  private ReddeInitialResponse receiveResponse;
  private RuntimeException receiveError;
  private ReddeInitialResponse cashoutResponse;
  private RuntimeException cashoutError;
  private ReddeStatusResponse statusResponse;
  private RuntimeException statusError;
  private ReddeCheckoutResponse checkoutResponse;
  private RuntimeException checkoutError;

  public FakeReddeClient onReceive(ReddeInitialResponse resp) {
    this.receiveResponse = resp;
    this.receiveError = null;
    return this;
  }

  public FakeReddeClient onReceiveThrow(RuntimeException e) {
    this.receiveError = e;
    return this;
  }

  public FakeReddeClient onCashout(ReddeInitialResponse resp) {
    this.cashoutResponse = resp;
    this.cashoutError = null;
    return this;
  }

  public FakeReddeClient onCashoutThrow(RuntimeException e) {
    this.cashoutError = e;
    return this;
  }

  public FakeReddeClient onStatus(ReddeStatusResponse resp) {
    this.statusResponse = resp;
    this.statusError = null;
    return this;
  }

  public FakeReddeClient onStatusThrow(RuntimeException e) {
    this.statusError = e;
    return this;
  }

  public FakeReddeClient onCheckout(ReddeCheckoutResponse resp) {
    this.checkoutResponse = resp;
    this.checkoutError = null;
    return this;
  }

  public FakeReddeClient onCheckoutThrow(RuntimeException e) {
    this.checkoutError = e;
    return this;
  }

  @Override
  public ReddeInitialResponse receive(String apikey, ReddeReceiveRequest body) {
    receiveRequests.add(body);
    if (receiveError != null) {
      throw receiveError;
    }
    return require(receiveResponse, "receive");
  }

  @Override
  public ReddeInitialResponse cashout(String apikey, ReddeCashoutRequest body) {
    cashoutRequests.add(body);
    if (cashoutError != null) {
      throw cashoutError;
    }
    return require(cashoutResponse, "cashout");
  }

  @Override
  public ReddeStatusResponse status(String apikey, String appid, String transactionid) {
    statusLookups.add(transactionid);
    if (statusError != null) {
      throw statusError;
    }
    return require(statusResponse, "status");
  }

  @Override
  public ReddeCheckoutResponse checkout(ReddeCheckoutRequest body) {
    checkoutRequests.add(body);
    if (checkoutError != null) {
      throw checkoutError;
    }
    return require(checkoutResponse, "checkout");
  }

  @Override
  public ReddeStatusResponse checkoutStatus(String apikey, String appid, String checkouttransid) {
    checkoutStatusLookups.add(checkouttransid);
    if (statusError != null) {
      throw statusError;
    }
    return require(statusResponse, "checkoutStatus");
  }

  private static <T> T require(T value, String endpoint) {
    if (value == null) {
      throw new IllegalStateException("FakeReddeClient." + endpoint + " called but not configured");
    }
    return value;
  }
}
