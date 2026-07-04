package org.shakvilla.beatzmedia.podcasts.adapter.in.rest;

import java.util.List;
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

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.podcasts.application.port.in.GetEpisodeStreamUrl;
import org.shakvilla.beatzmedia.podcasts.application.port.in.GetPodcast;
import org.shakvilla.beatzmedia.podcasts.application.port.in.ListEpisodes;
import org.shakvilla.beatzmedia.podcasts.application.port.in.ListPodcasts;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PodcastEpisodeView;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PodcastView;
import org.shakvilla.beatzmedia.podcasts.application.port.in.StreamUrlResult;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastCategory;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;

/**
 * Thin REST resource for the podcasts browse/detail/gated-stream endpoints (LLFR-PODCAST-01.1 –
 * 01.3). Maps HTTP to input ports; no business logic — the INV-3 rendition decision lives entirely
 * in {@code GetEpisodeStreamUrlService}. Podcasts ADD §5.1 / API-CONTRACT.md §8.
 *
 * <ul>
 *   <li>GET /v1/podcasts?category=&amp;page=&amp;size= → Page&lt;PodcastDto&gt; (200)
 *   <li>GET /v1/podcasts/:id → PodcastDto (200); 404 NOT_FOUND
 *   <li>GET /v1/podcasts/:id/episodes → PodcastEpisodeDto[] (200); 404 NOT_FOUND
 *   <li>GET /v1/podcasts/episodes/:id/stream → StreamUrlResponse (200); 404 NOT_FOUND
 * </ul>
 *
 * <p>Auth is optional on every read (PRD §6.8 / ADD §9): an anonymous caller gets
 * {@code isOwned=false} decoration and a preview stream for gated episodes; an authenticated
 * caller's identity is always derived from the verified JWT subject — never a client-supplied
 * body/query field. The stream endpoint is namespaced under {@code /podcasts/episodes/:id/stream}
 * (not {@code /episodes/:id/stream}) to keep a single {@code @Path("/v1")} root without a
 * structural route collision against {@code /podcasts/:id} (WU-PLY-1 routing lesson).
 */
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class PodcastResource {

  private final ListPodcasts listPodcasts;
  private final GetPodcast getPodcast;
  private final ListEpisodes listEpisodes;
  private final GetEpisodeStreamUrl getEpisodeStreamUrl;
  private final JsonWebToken jwt;

  @Inject
  public PodcastResource(
      ListPodcasts listPodcasts,
      GetPodcast getPodcast,
      ListEpisodes listEpisodes,
      GetEpisodeStreamUrl getEpisodeStreamUrl,
      JsonWebToken jwt) {
    this.listPodcasts = listPodcasts;
    this.getPodcast = getPodcast;
    this.listEpisodes = listEpisodes;
    this.getEpisodeStreamUrl = getEpisodeStreamUrl;
    this.jwt = jwt;
  }

  /** GET /v1/podcasts?category=&page=&size= — LLFR-PODCAST-01.1. */
  @GET
  @Path("/podcasts")
  public Page<PodcastView> listPodcasts(
      @QueryParam("category") String category,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("20") int size) {
    Optional<PodcastCategory> parsedCategory = parseCategory(category);
    return listPodcasts.list(parsedCategory, new PageRequest(page, size));
  }

  /** GET /v1/podcasts/:id — LLFR-PODCAST-01.2. */
  @GET
  @Path("/podcasts/{id}")
  public PodcastView getPodcast(@PathParam("id") String id) {
    return getPodcast.get(new PodcastId(id));
  }

  /** GET /v1/podcasts/:id/episodes — LLFR-PODCAST-01.3. */
  @GET
  @Path("/podcasts/{id}/episodes")
  public List<PodcastEpisodeView> listEpisodes(@PathParam("id") String id) {
    return listEpisodes.list(new PodcastId(id), callerId());
  }

  /** GET /v1/podcasts/episodes/:id/stream — LLFR-PODCAST-01.3 (INV-3 gated stream). */
  @GET
  @Path("/podcasts/episodes/{id}/stream")
  public StreamUrlResponse getEpisodeStreamUrl(@PathParam("id") String id) {
    StreamUrlResult result = getEpisodeStreamUrl.getStreamUrl(new EpisodeId(id), callerId());
    return new StreamUrlResponse(
        result.audioUrl(), result.previewSeconds().orElse(null), result.expiresAt());
  }

  private Optional<PodcastCategory> parseCategory(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(PodcastCategory.fromWireValue(raw));
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Invalid podcast category: " + raw, "category");
    }
  }

  /** Extract caller account id from JWT sub, if a valid token is present. */
  private Optional<AccountId> callerId() {
    try {
      String sub = jwt.getSubject();
      return (sub != null && !sub.isBlank()) ? Optional.of(new AccountId(sub)) : Optional.empty();
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
