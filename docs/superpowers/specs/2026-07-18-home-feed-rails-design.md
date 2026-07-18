# WU-CAT-8 — Home-Feed Discover Rails (design)

**Date:** 2026-07-18
**Area:** Backend (`backend/`, catalog module) + Frontend (`Frontend/`, home page) — **one WU, one branch, one PR**
**Branch:** `feat/WU-CAT-8-home-feed-rails` (off master)
**Status:** design approved; ready for implementation plan

## Goal

Replace the three mock-backed rails on the home page with real, ranked data by extending the
existing `/home` feed with a `rails` object. The home page's core rails (trending, top10, featured
albums, browse categories) are already wired; this fills the remaining three:

- **New releases** — albums, newest first.
- **Popular artists** — artists by monthly listeners.
- **Made-for-you / quick-pick playlists** — public playlists by followers.

Today `Frontend/src/routes/index.tsx` reads these from `lib/mock-data` (`albums`, `artists`,
`playlists`). After this WU they come from the real backend and the mock import is deleted.

## Scope decisions (locked)

1. **Extend `/home` with a nested `rails` object** — matches the shape the API-CONTRACT already
   anticipates (`GET /home → { trending, top10, featuredAlbums, rails: {...} }`). One endpoint, one
   round-trip. Not separate per-rail list endpoints.
2. **Rankings (confirmed):** new releases = albums `ORDER BY year DESC`; popular artists =
   `ORDER BY monthlyListeners DESC`; curated playlists = `WHERE isPublic ORDER BY followers DESC`.
   Simple ranked queries, not per-user personalization.
3. **One combined WU/branch/PR** covering the backend feed extension and the frontend wire together
   (not two sequenced PRs).

### Non-goals

- Personalization / recommendation (rails are globally ranked, not per-user).
- Editorial-curation tooling (admin) for choosing playlists.
- Any change to the already-wired trending / top10 / featuredAlbums / browse-categories.
- New public list endpoints (`/albums`, `/artists`, `/playlists`) — explicitly rejected in favor of
  the single `/home` `rails` object.

## Backend contract (additive to WU-CAT-2's `/home`)

`GET /v1/home` response gains a `rails` object; everything else is unchanged:

```
{
  trending: Track[],
  top10: Track[],
  featuredAlbums: Album[],
  rails: {
    newReleases:      Album[],     // albums, year DESC
    popularArtists:   Artist[],    // artists, monthlyListeners DESC
    curatedPlaylists: Playlist[]   // public playlists, followers DESC
  }
}
```

- The rail element shapes reuse the catalog's **existing** `AlbumView` / `ArtistView` /
  `PlaylistView` (the same shapes `/albums/:id`, `/artists/:id`, `/playlists/:id`, and `/search`
  already serve) — no new view types, no shape drift.
- `Playlist` items in a rail carry `trackIds` (the `tracks` array stays empty, per the existing
  playlist-list convention — embed via `/playlists/:id`); the home page only needs
  `title/image/creator/trackIds.length` for the card.
- **API-CONTRACT.md**: update the `/home` row to document the `rails` object (currently it lists a
  vague `rails: {...}` — make it concrete).

## Backend architecture (hexagonal, catalog module)

- **`CatalogRepository` (out port)** gains three read-only, ranked, limited queries:
  - `List<Album> newestAlbums(int limit)` — `ORDER BY year DESC`.
  - `List<ArtistProfile> popularArtists(int limit)` — `ORDER BY monthlyListeners DESC`.
  - `List<Playlist> curatedPlaylists(int limit)` — `WHERE isPublic = true ORDER BY followers DESC`.
- **`JpaCatalogRepository`** implements each with a JPQL `ORDER BY` + `setMaxResults(limit)`; the
  test **`FakeCatalogRepository`** implements them over its in-memory maps (sort + limit) so unit
  tests stay framework-free.
- **`HomeFeedView`** (in port) gains a nested `RailsView(List<AlbumView> newReleases,
  List<ArtistView> popularArtists, List<PlaylistView> curatedPlaylists)` field.
- **`GetHomeFeedService`** populates the rails from the three repo methods, with limit constants
  beside the existing `TRENDING_LIMIT` / `FEATURED_ALBUMS_LIMIT`: `NEW_RELEASES_LIMIT = 10`,
  `POPULAR_ARTISTS_LIMIT = 10`, `CURATED_PLAYLISTS_LIMIT = 6`.
- **`PublicCatalogResource`** (`GET /home`) serializes the extended view — additive, no route change.
- **No migration** — all three queries are read-only over existing tables (`album.year`,
  `artist_profile.monthly_listeners`, `playlist.is_public`/`followers`), which already exist and are
  already indexed enough for a small `LIMIT` scan on seed-scale data.

## Frontend architecture (`Frontend/`)

- **`lib/api/mappers.ts`** — no new mappers needed; `toAlbum`, `toArtist`, `toPlaylist` (and
  `AlbumWire`/`ArtistWire`/`PlaylistWire`) already exist.
- **`lib/api/queries/catalog.ts`** — `HomeFeedWire` gains
  `rails: { newReleases: AlbumWire[]; popularArtists: ArtistWire[]; curatedPlaylists: PlaylistWire[] }`;
  `homeQuery`'s mapper adds `rails: { newReleases: wire.rails.newReleases.map(toAlbum),
  popularArtists: wire.rails.popularArtists.map(toArtist),
  curatedPlaylists: wire.rails.curatedPlaylists.map(toPlaylist) }`.
- **`routes/index.tsx`** — replace the mock rails with the feed's rails: `home.rails.newReleases`
  (New releases), `home.rails.popularArtists` (Popular artists),
  `home.rails.curatedPlaylists` (Made-for-you rail **and** the top-3 quick-pick cards via
  `.slice(0, 3)`). **Delete** `import { artists, playlists, albums } from '../lib/mock-data'`. No
  visual change while data is present.

## Error handling & empty states

- The `/home` query already fails/loads as one unit (Suspense/ensureQueryData) — the rails ride the
  same request, so no new error path.
- **Empty rails:** a rail (`MediaRail`/quick-pick block) renders **only when its list is non-empty**,
  so sparse seed data doesn't leave a titled section with no cards. (The mocks always had data; real
  data may be thinner.) This is the one deliberate behavior addition.

## Testing

- **Backend unit** — `GetHomeFeedService` populates all three rails from the (fake) repo, respecting
  the limits; `FakeCatalogRepository` sort+limit matches the ranking.
- **Backend integration (Testcontainers)** — the three repo queries return rows in the correct order
  and honor `limit`, over seeded data; `GET /home` returns a well-formed `rails` object.
- **Backend contract** — the `/home` response validates against the documented shape; existing
  `/home` consumers still see `trending/top10/featuredAlbums` unchanged (additive).
- **Frontend (Vitest)** — `homeQuery` maps `rails` (each list through the right mapper); a render
  test that a rail with an empty list is not rendered. Gate: `npm run build` clean, lint 0 new
  (Node 22.17.1 via nvm).

## Files touched

**Backend:** `catalog/application/port/out/CatalogRepository.java`,
`catalog/adapter/out/persistence/JpaCatalogRepository.java`,
`catalog/fakes/FakeCatalogRepository.java` (test),
`catalog/application/port/in/HomeFeedView.java`,
`catalog/application/service/GetHomeFeedService.java`,
`catalog/adapter/in/rest/PublicCatalogResource.java` (+ any REST response DTO for `/home`),
tests (repo IT, service test, contract test), `API-CONTRACT.md`, `backend/.project/backlog.yaml`
(register **WU-CAT-8**, `depends_on: [WU-CAT-2]`; confirm the next-free id at registration).

**Frontend:** `Frontend/src/lib/api/queries/catalog.ts`, `Frontend/src/routes/index.tsx`
(+ the `homeQuery`/index tests).

## Definition of done

Backend gate `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh` green (user-run);
frontend `npm run build` clean + lint 0 new; the home page renders all three rails from real data
(mock import gone); API-CONTRACT + catalog ADD + backlog updated in the same PR; one PR to master.
