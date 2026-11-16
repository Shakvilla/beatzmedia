package org.shakvilla.beatzmedia.studio.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.studio.application.port.in.ListAdminPodcastEpisodes;
import org.shakvilla.beatzmedia.studio.application.port.in.ListAdminPodcastEpisodes.AdminEpisodeView;

/** Admin-only visibility into Studio podcast uploads, before and after fan projection. */
@Path("/v1/admin/podcasts/episodes")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"super-admin", "moderator", "support"})
@Tag(name = "Administrator Management")
@SecurityRequirement(name = "BearerAuth")
public class AdminPodcastResource {
  private final ListAdminPodcastEpisodes listEpisodes;

  @Inject
  public AdminPodcastResource(ListAdminPodcastEpisodes listEpisodes) {
    this.listEpisodes = listEpisodes;
  }

  @GET
  @Operation(summary = "List podcast episode uploads across creators")
  public Page<AdminEpisodeView> list(
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("50") int size) {
    return listEpisodes.list(new PageRequest(page, size));
  }
}
