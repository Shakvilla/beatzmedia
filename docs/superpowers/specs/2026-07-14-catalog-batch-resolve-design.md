# Catalog Batch Resolve Endpoint — Design

**Date:** 2026-07-14
**Status:** Approved design (pre-implementation)
**Branch:** `feat/catalog-resolve` (off `master`)

## Purpose

The frontend's list screens (starting with the **Library** screen in the upcoming collection-wiring
slice) hold **id-arrays** — the collection API returns `likedTracks: ["last-last", …]`,
`followedArtists: [...]`, `savedAlbums: [...]`, `followedPlaylists: [...]` — but render **rich cards**
that need each item's full object (title, cover art, price, ownership, etc.). The catalog exposes only
single-item reads (`GET /tracks/{id}`, `/artists/{id}`, `/albums/{id}`, `/playlists/{id}`), so a
library with N items would fire N HTTP requests.

This spec adds **one batch endpoint** that resolves id-lists to their full views in a single request,
so list screens fetch once instead of per-item. It is a standalone backend change, shipped and merged
on its own before the frontend collection slice consumes it.

## Scope

**In:** a new read-only endpoint `POST /v1/catalog/resolve` in the catalog module, plus its input
port, application service, request DTO, response view, unit + contract tests, and docs.

**Out:** any frontend change (the collection/library slice consumes this separately); any new schema,
migration, or persistence-port method (the batch reads already exist); shows/podcasts resolution
(different module, not needed by the Library screen); pagination (id-lists are inherently bounded by
what the caller already holds).

## Key facts grounding this design

- **The batch persistence reads already exist** on `CatalogRepository` (built for WU-CAT-2 home feed),
  so no outbound-port or adapter change is needed:
  - `List<Track> tracksByIds(List<String> ids)`
  - `List<Album> albumsByIds(List<String> ids)`
  - `List<ArtistProfile> artistsByIds(List<String> ids)`
  - `List<Playlist> playlistsByIds(List<String> ids)`
  Each is an `IN (…)` query returning only rows that exist — **inherently lenient**.
- **Per-caller track ownership already has a mapper:** `TrackMapper.toView(track, callerId,
  ownershipReader)` decorates each `TrackView` with the caller's effective `ownership`/`price`. The
  resolve service reuses it, so resolved tracks carry the same ownership a single `GET /tracks/{id}`
  would.
- The existing `PublicCatalogResource` is `@Path("/v1") @PermitAll` and extracts an **optional** caller
  id from the JWT via a private `callerId()` helper (`Optional<String>`, empty when no/invalid token).
  The resolve endpoint follows the same auth model.
- Response reuses the existing view records — `TrackView`, `ArtistView`, `AlbumView`, `PlaylistView` —
  so nothing new appears on the wire; the shapes already match `Frontend/src/types/index.ts`.

## Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | `POST` (body), not `GET` (query params) | Id-lists can be dozens of entries across four kinds; a JSON body avoids URL-length limits and is cleaner than repeated query params. The endpoint is still a pure read (no state change). |
| D2 | One endpoint resolving all four kinds in a single call | The Library screen needs tracks + artists + albums + playlists together; one round trip beats four. |
| D3 | **Lenient** — unknown/removed ids are silently omitted, never a 404 | A collection referencing a since-deleted track must still load. The `…ByIds` reads already return only extant rows, so this falls out naturally. |
| D4 | `@PermitAll` + optional JWT caller id (mirrors `PublicCatalogResource`) | Track ownership is per-caller; an anonymous caller gets `for-sale`/`free` with no owned decoration, exactly like the single-item reads. No new authz surface. |
| D5 | Reuse existing view records + `TrackMapper`; add small static mappers for artist/album/playlist if their view-mapping is currently inline in the single-get services | No wire-shape drift; DRY with the single-item path. |
| D6 | Cap at **200 ids per kind** (≤800 total per request) → `422 VALIDATION` if any kind exceeds it | Prevents an unbounded `IN (…)` / response. Generous vs. any real collection; documented. |

## Architecture

Standard hexagonal inbound flow — adapter → application → domain — identical in shape to the existing
catalog read endpoints.

- **Inbound REST** — add to `catalog/adapter/in/rest/PublicCatalogResource.java`:
  ```
  @POST @Path("/catalog/resolve")
  @Consumes(APPLICATION_JSON)
  public ResolvedCatalogView resolve(ResolveCatalogRequest request) { … maps to port, passes callerId() … }
  ```
  New request DTO `ResolveCatalogRequest(List<String> trackIds, List<String> artistIds,
  List<String> albumIds, List<String> playlistIds)` — every field nullable; a null/missing list is
  treated as empty.
- **Input port** — `catalog/application/port/in/ResolveCatalog.java`:
  ```
  ResolvedCatalogView resolve(ResolveCatalog.Command command, Optional<String> callerId);
  record Command(List<String> trackIds, List<String> artistIds, List<String> albumIds, List<String> playlistIds);
  ```
  and the response view `ResolvedCatalogView(List<TrackView> tracks, List<ArtistView> artists,
  List<AlbumView> albums, List<PlaylistView> playlists)`.
- **Application service** — `catalog/application/service/ResolveCatalogService.java` (`@ApplicationScoped
  @Transactional`): for each non-empty id-list, call the matching `…ByIds` repo read and map each
  domain object to its view (tracks via `TrackMapper.toView(t, callerId, ownershipReader)`; artists/
  albums/playlists via their existing/extracted mappers). Empty/absent lists resolve to empty result
  lists. Validate total id count against the D6 cap → throw the domain validation exception mapped to
  `422`.
- **Domain** — unchanged. No new domain types.

## Data flow

```
POST /v1/catalog/resolve  { trackIds, artistIds, albumIds, playlistIds }  [+ optional Bearer]
  → PublicCatalogResource.resolve → ResolveCatalog.resolve(command, callerId)
      → CatalogRepository.tracksByIds / artistsByIds / albumsByIds / playlistsByIds   (IN(…), lenient)
      → map each domain object to its View (TrackMapper applies per-caller ownership)
  → 200 ResolvedCatalogView { tracks[], artists[], albums[], playlists[] }
```

## Error handling

- Over-cap request (D6) → `422` `{ error: { code: "VALIDATION", … } }` via the existing
  `DomainExceptionMapper`.
- Unknown ids → **not** an error; omitted from the result (D3).
- Malformed JSON body → `400` (framework default), consistent with other POST endpoints.
- No auth required; a present-but-expired token behaves as anonymous for ownership (empty caller id).

## Testing

- **Unit** (`ResolveCatalogServiceTest`, fake `CatalogRepository` + `OwnershipReader`): resolves each
  kind; omits unknown ids; empty/null lists → empty lists; a for-sale track owned by the caller maps to
  `owned`; over-cap → validation exception.
- **Integration/REST** (`CatalogResolveIT`, Testcontainers, real Postgres + seed data): `POST` with a
  mix of real and bogus ids returns only the real ones across all four kinds, with a `200`; an
  authenticated caller sees correct per-caller `ownership` on resolved tracks; over-cap → `422`.
- **Contract:** extend the catalog contract test to assert `ResolvedCatalogView`'s four arrays and that
  member objects match the `TrackView`/`ArtistView`/`AlbumView`/`PlaylistView` shapes the frontend
  types expect.

## Definition of done

- Endpoint + port + service + DTOs implemented; `TrackView`/`ArtistView`/`AlbumView`/`PlaylistView`
  reused unchanged.
- Unit + integration + contract tests pass; ArchUnit green (normal adapter→application→domain flow, no
  cross-module access); Spotless clean; coverage gate met.
- `API-CONTRACT.md` amended (additive: the new endpoint + `ResolvedCatalogView`).
- Catalog module ADD (`backend/docs/architecture/catalog.md`) updated with the resolve endpoint in the
  same PR.
- `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh` green (run by the user per
  convention).
- One PR against `master`, Conventional Commit, merged before the frontend collection slice starts.
