package org.shakvilla.beatzmedia.catalog.adapter.in.rest;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.catalog.application.port.in.AlbumView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ArtistView;
import org.shakvilla.beatzmedia.catalog.application.port.in.BrowseCategoryView;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetAlbum;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetArtist;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetHomeFeed;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetLyrics;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetPlaylist;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetTrack;
import org.shakvilla.beatzmedia.catalog.application.port.in.HomeFeedView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ListBrowseCategories;
import org.shakvilla.beatzmedia.catalog.application.port.in.LyricsView;
import org.shakvilla.beatzmedia.catalog.application.port.in.PlaylistView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ResolveCatalog;
import org.shakvilla.beatzmedia.catalog.application.port.in.ResolvedCatalogView;
import org.shakvilla.beatzmedia.catalog.application.port.in.Search;
import org.shakvilla.beatzmedia.catalog.application.port.in.SearchResultsView;
import org.shakvilla.beatzmedia.catalog.application.port.in.ShowView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.domain.AlbumId;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.PlaylistId;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/**
 * Thin REST resource for the public catalog read endpoints (LLFR-CATALOG-01.4 – 01.7). Maps HTTP
 * to input ports; no business logic. Catalog ADD §5.1 / API-CONTRACT.md §3.
 *
 * <ul>
 *   <li>GET /v1/artists/:id → Artist (404 ARTIST_NOT_FOUND)
 *   <li>GET /v1/artists/:id/tracks → Track[]
 *   <li>GET /v1/artists/:id/albums → Album[]
 *   <li>GET /v1/artists/:id/shows → Show[]
 *   <li>GET /v1/albums/:id?tracks=true → Album (+ tracks)
 *   <li>GET /v1/tracks/:id → Track
 *   <li>GET /v1/tracks/:id/lyrics → { lines: {time, text}[] }
 *   <li>GET /v1/playlists/:id → Playlist (+ tracks)
 * </ul>
 */
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class PublicCatalogResource {

  private final GetArtist getArtist;
  private final GetAlbum getAlbum;
  private final GetTrack getTrack;
  private final GetLyrics getLyrics;
  private final GetPlaylist getPlaylist;
  private final GetHomeFeed getHomeFeed;
  private final Search search;
  private final ListBrowseCategories listBrowseCategories;
  private final ResolveCatalog resolveCatalog;
  private final JsonWebToken jwt;

  @Inject
  public PublicCatalogResource(
      GetArtist getArtist,
      GetAlbum getAlbum,
      GetTrack getTrack,
      GetLyrics getLyrics,
      GetPlaylist getPlaylist,
      GetHomeFeed getHomeFeed,
      Search search,
      ListBrowseCategories listBrowseCategories,
      ResolveCatalog resolveCatalog,
      JsonWebToken jwt) {
    this.getArtist = getArtist;
    this.getAlbum = getAlbum;
    this.getTrack = getTrack;
    this.getLyrics = getLyrics;
    this.getPlaylist = getPlaylist;
    this.getHomeFeed = getHomeFeed;
    this.search = search;
    this.listBrowseCategories = listBrowseCategories;
    this.resolveCatalog = resolveCatalog;
    this.jwt = jwt;
  }

  /** GET /v1/artists/:id — LLFR-CATALOG-01.4. */
  @GET
  @Path("/artists/{id}")
  public ArtistView getArtist(@PathParam("id") String id) {
    return getArtist.getArtist(new ArtistId(id));
  }

  /** GET /v1/artists/:id/tracks — LLFR-CATALOG-01.4. */
  @GET
  @Path("/artists/{id}/tracks")
  public List<TrackView> getArtistTracks(@PathParam("id") String id) {
    return getArtist.tracks(new ArtistId(id), callerId());
  }

  /** GET /v1/artists/:id/albums — LLFR-CATALOG-01.4. */
  @GET
  @Path("/artists/{id}/albums")
  public List<AlbumView> getArtistAlbums(@PathParam("id") String id) {
    return getArtist.albums(new ArtistId(id));
  }

  /** GET /v1/artists/:id/shows — LLFR-CATALOG-01.4. */
  @GET
  @Path("/artists/{id}/shows")
  public List<ShowView> getArtistShows(@PathParam("id") String id) {
    return getArtist.shows(new ArtistId(id));
  }

  /** GET /v1/albums/:id?tracks=true — LLFR-CATALOG-01.5. */
  @GET
  @Path("/albums/{id}")
  public AlbumView getAlbum(
      @PathParam("id") String id,
      @QueryParam("tracks") @DefaultValue("false") boolean includeTracks) {
    return getAlbum.get(new AlbumId(id), includeTracks, callerId());
  }

  /** GET /v1/tracks/:id — LLFR-CATALOG-01.6. */
  @GET
  @Path("/tracks/{id}")
  public TrackView getTrack(@PathParam("id") String id) {
    return getTrack.get(new TrackId(id), callerId());
  }

  /** GET /v1/tracks/:id/lyrics — LLFR-CATALOG-01.6. */
  @GET
  @Path("/tracks/{id}/lyrics")
  public LyricsView getLyrics(@PathParam("id") String id) {
    return getLyrics.get(new TrackId(id));
  }

  /** GET /v1/playlists/:id — LLFR-CATALOG-01.7. */
  @GET
  @Path("/playlists/{id}")
  public PlaylistView getPlaylist(@PathParam("id") String id) {
    return getPlaylist.get(new PlaylistId(id), callerId());
  }

  /** GET /v1/home — LLFR-CATALOG-01.1. */
  @GET
  @Path("/home")
  public HomeFeedView getHomeFeed() {
    return getHomeFeed.get(callerId());
  }

  /** GET /v1/search?q= — LLFR-CATALOG-01.2. Returns 422 MISSING_QUERY when q is blank. */
  @GET
  @Path("/search")
  public SearchResultsView search(@QueryParam("q") String q) {
    return search.search(q, callerId());
  }

  /** GET /v1/browse-categories — LLFR-CATALOG-01.3. */
  @GET
  @Path("/browse-categories")
  public List<BrowseCategoryView> browseCategories() {
    return listBrowseCategories.list();
  }

  /** POST /v1/catalog/resolve — batch id-list resolution for list screens (e.g. library). */
  @POST
  @Path("/catalog/resolve")
  @Consumes(MediaType.APPLICATION_JSON)
  public ResolvedCatalogView resolveCatalog(ResolveCatalogRequest request) {
    return resolveCatalog.resolve(
        new ResolveCatalog.Command(
            request.trackIds(), request.artistIds(), request.albumIds(), request.playlistIds()),
        callerId());
  }

  /** Extract caller account id from JWT sub, if a valid token is present. */
  private Optional<String> callerId() {
    try {
      String sub = jwt.getSubject();
      return (sub != null && !sub.isBlank()) ? Optional.of(sub) : Optional.empty();
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
