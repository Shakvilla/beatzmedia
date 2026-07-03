package org.shakvilla.beatzmedia.commerce.adapter.in.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.commerce.application.port.in.ListOrders;
import org.shakvilla.beatzmedia.commerce.application.port.in.OrderSnapshot;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

import io.quarkus.security.Authenticated;

/**
 * Thin REST resource for order history (LLFR-COMMERCE-02.4). {@code GET /v1/me/orders} → newest-first
 * page of the caller's OWN {@link OrderSnapshot}s (scoped by JWT subject; another account's orders are
 * never visible). Commerce ADD §5.1 / API-CONTRACT.md §6.
 */
@Path("/v1/me/orders")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class OrderResource {

  private final ListOrders listOrders;
  private final JsonWebToken jwt;

  @Inject
  public OrderResource(ListOrders listOrders, JsonWebToken jwt) {
    this.listOrders = listOrders;
    this.jwt = jwt;
  }

  @GET
  public Page<OrderSnapshot> list(
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("20") int size) {
    return listOrders.listOrders(new AccountId(jwt.getSubject()), new PageRequest(page, size));
  }
}
