package org.shakvilla.beatzmedia.platform.adapter.in.rest;

import java.util.List;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.shakvilla.beatzmedia.platform.application.port.in.ManageTaxonomy;
import org.shakvilla.beatzmedia.platform.domain.TaxonomyKind;

/**
 * Public read of the controlled lists — {@code GET /v1/taxonomy?kind=genre}.
 *
 * <p>{@code @PermitAll} because every picker needs it: the signup/browse surfaces are unauthenticated,
 * and the studio wizard needs genres before a release exists. Only <em>active</em> terms are served,
 * so deactivating a term removes it from all pickers without touching content that already uses it.
 */
@Path("/v1/taxonomy")
@Produces(MediaType.APPLICATION_JSON)
public class TaxonomyResource {

  private final ManageTaxonomy taxonomy;

  @Inject
  public TaxonomyResource(ManageTaxonomy taxonomy) {
    this.taxonomy = taxonomy;
  }

  /**
   * @param kind one of {@code genre}, {@code podcast_category}, {@code event_category},
   *     {@code browse_category}. An unknown kind is 422, never an empty list.
   */
  @GET
  @PermitAll
  public List<TaxonomyTermDto> list(@QueryParam("kind") String kind) {
    return taxonomy.listActive(TaxonomyKind.fromWireValue(kind)).stream()
        .map(TaxonomyTermDto::from)
        .toList();
  }
}
