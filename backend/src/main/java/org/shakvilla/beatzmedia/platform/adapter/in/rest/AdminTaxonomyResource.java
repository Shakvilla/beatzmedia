package org.shakvilla.beatzmedia.platform.adapter.in.rest;

import java.util.List;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.platform.application.port.in.ManageTaxonomy;
import org.shakvilla.beatzmedia.platform.domain.TaxonomyKind;

/**
 * Super-admin management of the controlled lists.
 *
 * <p>Reads here differ from {@link TaxonomyResource}: the console needs deactivated terms too, plus
 * the usage count behind each one, so an operator can see what a deletion would affect before
 * attempting it.
 */
@Path("/v1/admin/taxonomy")
@Produces(MediaType.APPLICATION_JSON)
public class AdminTaxonomyResource {

  private final ManageTaxonomy taxonomy;
  private final JsonWebToken jwt;

  @Inject
  public AdminTaxonomyResource(ManageTaxonomy taxonomy, JsonWebToken jwt) {
    this.taxonomy = taxonomy;
    this.jwt = jwt;
  }

  /** Every term of a kind, active and inactive, each with the number of items using it. */
  @GET
  @RolesAllowed({"super-admin", "finance", "moderator", "editor", "support"})
  public List<AdminTaxonomyTermDto> list(@QueryParam("kind") String kind) {
    return taxonomy.listAll(TaxonomyKind.fromWireValue(kind)).stream()
        .map(t -> AdminTaxonomyTermDto.from(t, taxonomy.usageCount(t.id())))
        .toList();
  }

  @POST
  @RolesAllowed("super-admin")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response create(@QueryParam("kind") String kind, @Valid CreateTermRequest request) {
    var term =
        taxonomy.create(
            jwt.getSubject(),
            new ManageTaxonomy.CreateTermCommand(
                TaxonomyKind.fromWireValue(kind),
                request.label(),
                request.colorClass(),
                request.sortOrder()));
    return Response.status(Response.Status.CREATED)
        .entity(AdminTaxonomyTermDto.from(term, 0))
        .build();
  }

  @PATCH
  @Path("/{id}")
  @RolesAllowed("super-admin")
  @Consumes(MediaType.APPLICATION_JSON)
  public AdminTaxonomyTermDto update(@PathParam("id") String id, @Valid UpdateTermRequest request) {
    var term =
        taxonomy.update(
            jwt.getSubject(),
            id,
            new ManageTaxonomy.UpdateTermCommand(
                request.label(), request.colorClass(), request.sortOrder(), request.active()));
    return AdminTaxonomyTermDto.from(term, taxonomy.usageCount(term.id()));
  }

  /** 409 with the usage count when anything still references the term. */
  @DELETE
  @Path("/{id}")
  @RolesAllowed("super-admin")
  public Response delete(@PathParam("id") String id) {
    taxonomy.delete(jwt.getSubject(), id);
    return Response.noContent().build();
  }

  public record CreateTermRequest(
      @NotBlank(message = "label must not be blank")
          @Size(max = 60, message = "label must not exceed 60 characters")
          String label,
      String colorClass,
      Integer sortOrder) {}

  /** Every field optional — a null means "leave alone", so the console can PATCH one field. */
  public record UpdateTermRequest(
      @Size(max = 60, message = "label must not exceed 60 characters") String label,
      String colorClass,
      Integer sortOrder,
      Boolean active) {}
}
