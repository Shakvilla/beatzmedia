# Frontend → Backend Wiring — Slice 1: Foundation + Auth + Catalog

**Date:** 2026-07-14
**Status:** Approved design (pre-implementation)
**Branch:** `feat/frontend-wiring-catalog`

## Purpose

BeatzClik's frontend (`Frontend/`, React 19 + TanStack Router/Query SPA) is currently 100%
mock-backed: it has **no network layer at all**. The Quarkus backend (`backend/`) is
feature-complete and implements the shapes in `Frontend/src/types/index.ts`. This effort swaps the
frontend's mocks for real backend calls **with no visual change**.

The full migration is a multi-plan **program**. This spec covers only **Slice 1**, whose job is to
build the shared foundation and prove the entire wiring pattern end-to-end on one read-heavy domain
(**catalog**). Later slices replicate the proven pattern domain-by-domain.

## Scope

**In scope (Slice 1):**
- Run/build plumbing: Vite dev proxy, `VITE_API_URL`, backend CORS.
- A reusable API client (base URL, JSON, Bearer auth, error parsing, 401 handling).
- A TanStack Query layer for catalog, driven by TanStack Router loaders.
- Real auth (login/signup/social/logout/become-artist/session hydration).
- Catalog read screens wired to live data: home, artist, album, track (+ lyrics, browse categories).

**Out of scope (later slices, stay mocked):**
- Playback/streaming URLs & ownership (`/tracks/:id/stream`, `/me/owned`), the player queue.
- Library/collection, cart/checkout/orders, tips.
- Studio (artist), Admin console.
- Podcasts, Events, live search-as-you-type, notifications.

## Key facts grounding this design

- **The seam is `getX()`.** Every screen imports synchronous getters from `src/lib/*-data.ts`
  (`getArtist(id)`, etc.) and calls them inline in render bodies across ~61 route files. There is no
  `useQuery`/`fetch`/`axios` anywhere; the mounted `QueryClient` in `src/main.tsx` is dead scaffolding.
- **The running backend is the source of truth, not `API-CONTRACT.md`.** The contract is marked
  "proposed" (shapes authoritative, paths provisional) and the built backend has drifted from it
  (e.g. it requires `Idempotency-Key` on money POSTs; `checkoutUrl` was added later). Endpoint paths
  below were confirmed against the backend's actual `@Path` annotations.
- **Auth is fully mocked and default-signed-in** in `src/features/auth/auth-context.tsx` (seeded demo
  artist/admin in localStorage `beatzclik-auth`, no token, no Authorization header).
- **Confirmed backend catalog surface** (all under `/v1`, `PublicCatalogResource` + siblings):
  `/home`, `/browse-categories`, `/search`, `/artists/{id}`, `/artists/{id}/tracks`,
  `/artists/{id}/albums`, `/artists/{id}/shows`, `/albums/{id}`, `/tracks/{id}`, `/tracks/{id}/lyrics`.
- **Auth/session surface:** `/v1/auth/login|signup|social|logout`, `/v1/me`, `/v1/me/become-artist`.
- **CORS is not enabled** on the backend today (`application.properties` has only `http.port=8080`).

## Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | Ship a **thin vertical slice** (foundation + auth + catalog) first, not the whole program. | Proves the pattern against the real backend before replicating; keeps the spec small and current. |
| D2 | **TanStack Router loaders + Query cache.** Routes fetch via `queryClient.ensureQueryData` in a `loader`; components read via `useSuspenseQuery`. | Data is guaranteed before render, so component bodies stay synchronous — least churn across ~61 call sites, best "no visual change" fit. Query still powers caching/mutations/deep components. |
| D3 | **JWT in localStorage + `Authorization: Bearer`**, hydrate via `GET /v1/me`, drop on 401/logout. | Matches the app's existing localStorage session; survives refresh; works with the backend as-built (no refresh-token flow exists). Accepts standard XSS exposure for this app class. |
| D4 | The **running backend is authoritative**; thin mappers in `queryFn` absorb any field drift from `types/index.ts`; large drift is flagged, not silently reshaped. | Contract doc is provisional; types are the UI's real shape. |
| D5 | Include the small **backend CORS** config change in this slice/branch. | A browser SPA needs it for any non-proxied deployment; trivial and belongs with the wiring. |

## Architecture

### A. Run/build plumbing
- `vite.config.ts`: add `server.proxy` forwarding `/v1` → `http://localhost:8080` (dev has no CORS
  issue; client uses relative URLs).
- `VITE_API_URL` env var, defaulting to `/v1`, for prod/non-proxied builds.
- `backend/src/main/resources/application.properties`: enable `quarkus.http.cors=true` with allowed
  origins/methods/headers. Goes on this feature branch; follows backend conventions (user runs
  `verify.sh`).

### B. API client — `src/lib/api/`
- `token.ts` — `getToken()/setToken()/clearToken()` over localStorage key `beatzclik-token`.
- `client.ts` — `apiFetch<T>(path, opts)`:
  - prepends `VITE_API_URL` base;
  - sets `Content-Type: application/json`, serializes JSON bodies;
  - attaches `Authorization: Bearer <token>` when a token is present;
  - parses the backend error envelope `{error:{code,message,field?}}` into a thrown typed `ApiError`
    (carrying `status`, `code`, `message`, `field`);
  - handles `204 No Content` (returns `undefined`);
  - on `401`: clears the token and invokes a registered `onUnauthorized()` hook;
  - built-in support for an `Idempotency-Key` header (unused until the commerce slice).

### C. Query layer — `src/lib/api/queries/catalog.ts`
`queryOptions` factories, one per endpoint:
`homeQuery()`, `browseCategoriesQuery()`, `artistQuery(id)`, `artistTracksQuery(id)`,
`artistAlbumsQuery(id)`, `albumQuery(id)`, `trackQuery(id)`, `lyricsQuery(id)`.
Each: `{ queryKey: [...], queryFn: () => apiFetch<Shape>(path) }`, returning types from
`src/types/index.ts`. Field drift is absorbed by a thin mapper inside the `queryFn`.

### D. Loaders + component reads
- `src/main.tsx`: create the router with `createRootRouteWithContext<{ queryClient: QueryClient }>()`
  and `createRouter({ context: { queryClient } })`.
- Catalog routes (`routes/index.tsx` = home, `routes/artist/$artistId.tsx`,
  `routes/album/$albumId.tsx`, the track route) gain a
  `loader: ({ context: { queryClient }, params }) => queryClient.ensureQueryData(<query>(params.id))`.
- Each call site swaps `const artist = getArtist(id)` →
  `const { data: artist } = useSuspenseQuery(artistQuery(id))`. The loader pre-fills the cache, so the
  read stays synchronous — no `isLoading` branches added. Deep non-route consumers (e.g.
  `features/discover/components/featured-carousel.tsx`) use `useSuspenseQuery` too.
- Add route-level `pendingComponent`/`errorComponent` reusing existing skeleton/spinner styling for
  the brief fetch window — the one unavoidable new UI.

### E. Auth wiring — `src/features/auth/auth-context.tsx`
- Rewrite `login`/`signup`/`social`/`logout`/`becomeArtist` to call `/v1/auth/*` and `/v1/me/*`; store
  the returned JWT; set `account` from the response.
- On mount: if a token exists, hydrate the session via `GET /v1/me`; otherwise signed-out (replaces the
  seeded auto-login — an intended, unavoidable behavior change).
- **`useAuth()`'s shape is unchanged**, so `components/layout/app-shell.tsx`, the header, and
  `components/studio/artist-gate.tsx` need no edits.
- Register the client's `onUnauthorized` hook to clear session + navigate to `/login`.

## Data flow

```
Route navigation
  → Router loader: queryClient.ensureQueryData(<query>(id))
      → queryFn: apiFetch('/v1/...') [+ Bearer token]
          → backend JSON  |  ApiError (thrown, typed)
      → data cached in QueryClient
  → Component renders: useSuspenseQuery(<query>(id)) reads cache synchronously
```

Auth:
```
login() → apiFetch POST /v1/auth/login → { token, account }
        → setToken(localStorage) + setAccount(context)
App mount → token? GET /v1/me → account  |  no token → signed-out
401 anywhere → clearToken + onUnauthorized() → navigate /login
```

## Error handling
- All failures surface as a thrown `ApiError` from `apiFetch`.
- Route loaders let errors propagate to the route `errorComponent`.
- `401` is special-cased in the client (clear token + logout redirect) before the error propagates.
- Component-level reads use `useSuspenseQuery`; the route `errorComponent` is the catch surface.

## Testing & verification
- **Manual (primary):** run `cd backend && ./mvnw quarkus:dev` + `cd Frontend && npm run dev`; log in
  for real; confirm home/artist/album/track render **identically** to the mock version with live data.
- **Unit (Vitest):** `apiFetch` error-envelope parsing + Bearer-header attachment; one catalog query
  factory. (Add Vitest if not already configured — minimal, client-only.)
- Backend CORS change verified via the standard backend gate (user runs `verify.sh`).

## Risks

- **R1 — Seed data.** "No visual change" only holds if the backend DB actually contains catalog
  content. If empty, screens come back bare. Verify early at implementation; may add a small dev seed
  (fixture or migration).
- **R2 — Shape drift.** Some backend responses may not match `types/index.ts` field-for-field. Thin
  mappers absorb small drift; large drift is flagged rather than silently reshaped.
- **R3 — Loading states.** Introducing loaders adds a brief pending window the mock app never had.
  Mitigated by reusing existing skeleton/spinner styling so it reads as "no visual change."

## Definition of done (Slice 1)
- Vite proxy + `VITE_API_URL` + backend CORS in place; dev stack runs against real backend.
- API client (`token.ts`, `client.ts`) with Bearer auth, error parsing, 401 handling.
- Catalog query factories + router loaders + `useSuspenseQuery` reads for home/artist/album/track/lyrics/browse.
- Real auth in `auth-context.tsx` with `useAuth()` shape unchanged; `/login` reachable and functional.
- Home/artist/album/track verified visually identical to the mock version against the running backend.
- Vitest unit tests for the client pass; backend `verify.sh` green.
- All other domains remain on mocks and still work.
