package org.shakvilla.beatzmedia.store.adapter.in.rest;

import java.util.Optional;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.store.application.port.in.GetStoreItem;
import org.shakvilla.beatzmedia.store.application.port.in.ListStore;
import org.shakvilla.beatzmedia.store.application.port.in.ListStore.StoreQuery;
import org.shakvilla.beatzmedia.store.application.port.in.StoreItemView;
import org.shakvilla.beatzmedia.store.domain.Genre;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;
import org.shakvilla.beatzmedia.store.domain.StoreItemType;
import org.shakvilla.beatzmedia.store.domain.StoreSort;

/**
 * Thin REST resource for the public store browse/detail endpoints (LLFR-STORE-01.1 – 01.2). Maps
 * HTTP to input ports; no business logic. Store ADD §5.1 / API-CONTRACT.md §7.
 *
 * <ul>
 *   <li>GET /v1/store?type=&amp;genre=&amp;sort=&amp;page=&amp;size= → Page&lt;StoreItemDto&gt; (200); 422
 *       on an unknown {@code type}/{@code genre}/{@code sort} enum value.
 *   <li>GET /v1/store/:id → StoreItemDto (200); 404 NOT_FOUND.
 * </ul>
 *
 * <p>Both endpoints are public — no auth required, no purchase/ownership decoration (purchases
 * are a {@code commerce} cart line, {@code kind: "store"}; this module never mutates money or
 * inventory at sale time).
 */
@Path("/v1/store")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class StoreResource {

  private final ListStore listStore;
  private final GetStoreItem getStoreItem;

  @Inject
  public StoreResource(ListStore listStore, GetStoreItem getStoreItem) {
    this.listStore = listStore;
    this.getStoreItem = getStoreItem;
  }

  /** GET /v1/store?type=&genre=&sort=&page=&size= — LLFR-STORE-01.1. */
  @GET
  public Page<StoreItemView> list(
      @QueryParam("type") String type,
      @QueryParam("genre") String genre,
      @QueryParam("sort") String sort,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("20") int size) {
    StoreQuery query = new StoreQuery(parseType(type), parseGenre(genre), parseSort(sort));
    return listStore.list(query, new PageRequest(page, size));
  }

  /** GET /v1/store/:id — LLFR-STORE-01.2. */
  @GET
  @Path("/{id}")
  public StoreItemView get(@PathParam("id") String id) {
    return getStoreItem.get(new StoreItemId(id));
  }

  private static Optional<StoreItemType> parseType(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(StoreItemType.fromWireValue(raw));
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Invalid store item type: " + raw, "type");
    }
  }

  private static Optional<Genre> parseGenre(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Genre.fromWireValue(raw));
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Invalid genre: " + raw, "genre");
    }
  }

  private static StoreSort parseSort(String raw) {
    if (raw == null || raw.isBlank()) {
      return StoreSort.POPULAR;
    }
    try {
      return StoreSort.fromWireValue(raw);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Invalid sort: " + raw, "sort");
    }
  }
}
