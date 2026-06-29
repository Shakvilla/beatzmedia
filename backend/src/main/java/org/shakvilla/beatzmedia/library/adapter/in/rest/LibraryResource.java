package org.shakvilla.beatzmedia.library.adapter.in.rest;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.library.application.port.in.CollectionView;
import org.shakvilla.beatzmedia.library.application.port.in.GetCollection;
import org.shakvilla.beatzmedia.library.application.port.in.GetOwnedTrackIds;
import org.shakvilla.beatzmedia.library.application.port.in.ManageUserPlaylist;
import org.shakvilla.beatzmedia.library.application.port.in.ToggleFollow;
import org.shakvilla.beatzmedia.library.application.port.in.ToggleLike;
import org.shakvilla.beatzmedia.library.application.port.in.ToggleSave;
import org.shakvilla.beatzmedia.library.application.port.in.UserPlaylistView;
import org.shakvilla.beatzmedia.library.domain.FollowKind;
import org.shakvilla.beatzmedia.library.domain.PlaylistId;

import io.quarkus.security.Authenticated;

/**
 * Thin REST resource for all library endpoints. No business logic; maps HTTP ↔ input ports.
 * Library ADD §5.1 / API-CONTRACT §5.
 *
 * <ul>
 *   <li>GET  /v1/me/collection         → CollectionView (200)
 *   <li>PUT  /v1/me/likes/tracks/:id   → 204 / 404
 *   <li>DELETE /v1/me/likes/tracks/:id → 204
 *   <li>PUT  /v1/me/follows/artists/:id    → 204 / 404
 *   <li>DELETE /v1/me/follows/artists/:id  → 204
 *   <li>PUT  /v1/me/follows/playlists/:id  → 204 / 404
 *   <li>DELETE /v1/me/follows/playlists/:id→ 204
 *   <li>PUT  /v1/me/follows/shows/:id      → 204 / 404
 *   <li>DELETE /v1/me/follows/shows/:id    → 204
 *   <li>PUT  /v1/me/saved/albums/:id       → 204 / 404
 *   <li>DELETE /v1/me/saved/albums/:id     → 204
 *   <li>GET  /v1/me/playlists              → UserPlaylistView[]
 *   <li>POST /v1/me/playlists              → UserPlaylistView (201)
 *   <li>GET  /v1/me/playlists/:id          → UserPlaylistView
 *   <li>PATCH /v1/me/playlists/:id         → UserPlaylistView
 *   <li>DELETE /v1/me/playlists/:id        → 204
 *   <li>PUT  /v1/me/playlists/:id/tracks/:trackId → UserPlaylistView
 *   <li>DELETE /v1/me/playlists/:id/tracks/:trackId → UserPlaylistView
 *   <li>GET  /v1/me/owned               → String[]
 * </ul>
 */
@Path("/v1/me")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class LibraryResource {

  private final GetCollection getCollection;
  private final ToggleLike toggleLike;
  private final ToggleFollow toggleFollow;
  private final ToggleSave toggleSave;
  private final ManageUserPlaylist managePlaylist;
  private final GetOwnedTrackIds getOwned;
  private final JsonWebToken jwt;

  @Inject
  public LibraryResource(
      GetCollection getCollection,
      ToggleLike toggleLike,
      ToggleFollow toggleFollow,
      ToggleSave toggleSave,
      ManageUserPlaylist managePlaylist,
      GetOwnedTrackIds getOwned,
      JsonWebToken jwt) {
    this.getCollection = getCollection;
    this.toggleLike = toggleLike;
    this.toggleFollow = toggleFollow;
    this.toggleSave = toggleSave;
    this.managePlaylist = managePlaylist;
    this.getOwned = getOwned;
    this.jwt = jwt;
  }

  // -------- Collection --------

  @GET
  @Path("/collection")
  public CollectionView getCollection() {
    return getCollection.get(caller());
  }

  // -------- Likes --------

  @PUT
  @Path("/likes/tracks/{trackId}")
  public Response likeTrack(@PathParam("trackId") String trackId) {
    toggleLike.like(caller(), trackId);
    return Response.noContent().build();
  }

  @DELETE
  @Path("/likes/tracks/{trackId}")
  public Response unlikeTrack(@PathParam("trackId") String trackId) {
    toggleLike.unlike(caller(), trackId);
    return Response.noContent().build();
  }

  // -------- Follows --------

  @PUT
  @Path("/follows/artists/{id}")
  public Response followArtist(@PathParam("id") String id) {
    toggleFollow.follow(caller(), FollowKind.artist, id);
    return Response.noContent().build();
  }

  @DELETE
  @Path("/follows/artists/{id}")
  public Response unfollowArtist(@PathParam("id") String id) {
    toggleFollow.unfollow(caller(), FollowKind.artist, id);
    return Response.noContent().build();
  }

  @PUT
  @Path("/follows/playlists/{id}")
  public Response followPlaylist(@PathParam("id") String id) {
    toggleFollow.follow(caller(), FollowKind.playlist, id);
    return Response.noContent().build();
  }

  @DELETE
  @Path("/follows/playlists/{id}")
  public Response unfollowPlaylist(@PathParam("id") String id) {
    toggleFollow.unfollow(caller(), FollowKind.playlist, id);
    return Response.noContent().build();
  }

  @PUT
  @Path("/follows/shows/{id}")
  public Response followShow(@PathParam("id") String id) {
    toggleFollow.follow(caller(), FollowKind.show, id);
    return Response.noContent().build();
  }

  @DELETE
  @Path("/follows/shows/{id}")
  public Response unfollowShow(@PathParam("id") String id) {
    toggleFollow.unfollow(caller(), FollowKind.show, id);
    return Response.noContent().build();
  }

  // -------- Saved albums --------

  @PUT
  @Path("/saved/albums/{albumId}")
  public Response saveAlbum(@PathParam("albumId") String albumId) {
    toggleSave.save(caller(), albumId);
    return Response.noContent().build();
  }

  @DELETE
  @Path("/saved/albums/{albumId}")
  public Response unsaveAlbum(@PathParam("albumId") String albumId) {
    toggleSave.unsave(caller(), albumId);
    return Response.noContent().build();
  }

  // -------- User playlists --------

  @GET
  @Path("/playlists")
  public List<UserPlaylistView> listPlaylists() {
    return managePlaylist.listPlaylists(caller());
  }

  @POST
  @Path("/playlists")
  public Response createPlaylist(CreatePlaylistRequest req) {
    UserPlaylistView view = managePlaylist.createPlaylist(caller(), req.title());
    return Response.status(Response.Status.CREATED).entity(view).build();
  }

  @GET
  @Path("/playlists/{id}")
  public UserPlaylistView getPlaylist(@PathParam("id") String id) {
    return managePlaylist.getPlaylist(caller(), new PlaylistId(id));
  }

  @PATCH
  @Path("/playlists/{id}")
  public UserPlaylistView renamePlaylist(
      @PathParam("id") String id, RenamePlaylistRequest req) {
    return managePlaylist.renamePlaylist(caller(), new PlaylistId(id), req.title());
  }

  @DELETE
  @Path("/playlists/{id}")
  public Response deletePlaylist(@PathParam("id") String id) {
    managePlaylist.deletePlaylist(caller(), new PlaylistId(id));
    return Response.noContent().build();
  }

  @PUT
  @Path("/playlists/{id}/tracks/{trackId}")
  public UserPlaylistView addTrack(
      @PathParam("id") String id, @PathParam("trackId") String trackId) {
    return managePlaylist.addTrack(caller(), new PlaylistId(id), trackId);
  }

  @DELETE
  @Path("/playlists/{id}/tracks/{trackId}")
  public UserPlaylistView removeTrack(
      @PathParam("id") String id, @PathParam("trackId") String trackId) {
    return managePlaylist.removeTrack(caller(), new PlaylistId(id), trackId);
  }

  // -------- Owned --------

  @GET
  @Path("/owned")
  public List<String> ownedTrackIds() {
    return getOwned.ownedTrackIds(caller());
  }

  // -------- Helper --------

  private AccountId caller() {
    return new AccountId(jwt.getSubject());
  }
}
