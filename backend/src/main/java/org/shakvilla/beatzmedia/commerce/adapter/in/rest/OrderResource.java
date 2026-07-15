package org.shakvilla.beatzmedia.commerce.adapter.in.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.commerce.application.port.in.GetOrder;
import org.shakvilla.beatzmedia.commerce.application.port.in.ListOrders;
import org.shakvilla.beatzmedia.commerce.application.port.in.OrderSnapshot;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

import io.quarkus.security.Authenticated;

/**
 * Thin REST resource for order history and order detail (LLFR-COMMERCE-02.4, WU-COM-3). Both
 * endpoints are scoped to the caller's OWN orders (by JWT subject); another account's orders are
 * never visible — a foreign or missing order id is 404, never 403 (§2.2). Commerce ADD §5.1 / §15
 * / API-CONTRACT.md §6.
 */
@Path("/v1/me/orders")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class OrderResource {

  private final ListOrders listOrders;
  private final GetOrder getOrder;
  private final JsonWebToken jwt;

  @Inject
  public OrderResource(ListOrders listOrders, GetOrder getOrder, JsonWebToken jwt) {
    this.listOrders = listOrders;
    this.getOrder = getOrder;
    this.jwt = jwt;
  }

  @GET
  public Page<OrderSnapshot> list(
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("20") int size) {
    return listOrders.listOrders(new AccountId(jwt.getSubject()), new PageRequest(page, size));
  }

  @GET
  @Path("/{orderId}")
  public OrderSnapshot get(@PathParam("orderId") String orderId) {
    return getOrder.getOrder(new AccountId(jwt.getSubject()), new OrderId(orderId));
  }
}
