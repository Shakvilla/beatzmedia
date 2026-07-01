package org.shakvilla.beatzmedia.commerce.adapter.in.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.commerce.application.port.in.AddCartItem;
import org.shakvilla.beatzmedia.commerce.application.port.in.AddCartItemCommand;
import org.shakvilla.beatzmedia.commerce.application.port.in.CartView;
import org.shakvilla.beatzmedia.commerce.application.port.in.GetCart;
import org.shakvilla.beatzmedia.commerce.application.port.in.RemoveCartItem;
import org.shakvilla.beatzmedia.commerce.application.port.in.UpdateCartItem;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

import io.quarkus.security.Authenticated;

/**
 * Thin REST resource for cart endpoints. No business logic; maps DTO → command, calls the input
 * port, maps result → DTO. Commerce ADD §5.1 / API-CONTRACT.md §6.
 *
 * <ul>
 *   <li>GET /v1/me/cart → CartView (200)
 *   <li>POST /v1/me/cart/items → CartView (200)
 *   <li>PATCH /v1/me/cart/items/:lineId → CartView (200)
 *   <li>DELETE /v1/me/cart/items/:lineId → CartView (200)
 * </ul>
 *
 * <p>Ownership of {@code /me/*} mutations is enforced by extracting the {@code sub} claim from the
 * Bearer JWT (same pattern as {@code identity.MeResource} / {@code library.LibraryResource}); the
 * application layer never accesses another account's cart.
 */
@Path("/v1/me/cart")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class CartResource {

  private final GetCart getCart;
  private final AddCartItem addCartItem;
  private final UpdateCartItem updateCartItem;
  private final RemoveCartItem removeCartItem;
  private final JsonWebToken jwt;

  @Inject
  public CartResource(
      GetCart getCart,
      AddCartItem addCartItem,
      UpdateCartItem updateCartItem,
      RemoveCartItem removeCartItem,
      JsonWebToken jwt) {
    this.getCart = getCart;
    this.addCartItem = addCartItem;
    this.updateCartItem = updateCartItem;
    this.removeCartItem = removeCartItem;
    this.jwt = jwt;
  }

  @GET
  public CartView getCart() {
    return getCart.getCart(caller());
  }

  @POST
  @Path("/items")
  public CartView addItem(AddCartItemRequest req) {
    if (req == null || req.kind() == null || req.refId() == null) {
      throw new ValidationException("kind and refId are required");
    }
    return addCartItem.add(
        caller(), new AddCartItemCommand(req.kind(), req.refId(), req.qty(), req.metadata()));
  }

  @PATCH
  @Path("/items/{lineId}")
  public CartView updateItem(@PathParam("lineId") String lineId, UpdateCartItemRequest req) {
    if (req == null || req.qty() == null) {
      throw new ValidationException("qty is required", "qty");
    }
    return updateCartItem.updateQuantity(caller(), lineId, req.qty());
  }

  @DELETE
  @Path("/items/{lineId}")
  public CartView removeItem(@PathParam("lineId") String lineId) {
    return removeCartItem.remove(caller(), lineId);
  }

  private AccountId caller() {
    return new AccountId(jwt.getSubject());
  }
}
