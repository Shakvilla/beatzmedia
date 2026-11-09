# BeatzClik Fan Mobile — Implementation Plan

**Date:** 2026-09-21
**Source:** MOBILE-PRD.md v1.0 + docs/plans/2026-09-20-beatzclik-fan-mobile-design.md (Approved)
**Stack:** Flutter + Riverpod + GoRouter + Dio + flutter_secure_storage + just_audio/audio_service
**Contract:** API-CONTRACT.md, Frontend/src/types/index.ts, Frontend/src/lib/api/*
**Prototype:** Beatzclik Fan App Prototype (Standalone) (1).html (visual source of truth)

## Execution model

Vertical slices per MOBILE-PRD §13. Each phase ships UI + real API integration + loading/empty/error/offline + accessibility + tests together. No fake affordances (HLFR-MOB-GAP-01).

```
M0 scaffold → M1 networking/session/routing → M2 auth/onboarding → M3 discovery →
M4 playback → M5 library/downloads → M6 commerce → M7 podcasts/store →
M8 notifications/settings/gaps/deeplinks → M9 a11y/golden/hardening/release
```

Dependencies are linear (M(n) → M(n+1)). Within a phase, feature slices are parallelized where write scopes don't overlap.

## Phase breakdown

### M0 — Scaffold, tokens, CI, env (FOUNDATION)
**Goal:** `mobile/` builds on iOS/Android CI, design tokens faithful to prototype, env-configurable API origin.
- `mobile/` Flutter project (feature-first: `app/ core/ design_system/ features/`), Riverpod/GoRouter/Dio/freezed/json_serializable/flutter_secure_storage/just_audio+audio_service
- Design tokens: near-black #0A0A0B, surface #1C1C1F, primary #EE4094, warm off-white #F6F4EF, gold ownership accent, Bricolage Grotesque + JetBrains Mono, 44pt targets, glass treatment
- Env: `--dart-define=API_ORIGIN` + `mobile/.env.example`, HTTPS enforcement in release
- CI jobs: `dart format`, `flutter analyze`, `flutter test`, unsigned release artifact (no signing)
- Reuse: Frontend format.ts (Money GHS), API-CONTRACT money/duration conventions

**Verification:** `dart format --set-exit-if-changed . && flutter analyze && flutter test` green, app boots to empty shell.

### M1 — API core, errors, secure session, router guards
**HLFR:** MOB-ID-01 (session), cross-cutting §8.3/§9/§10
- Dio client: configurable origin + `/v1`, JSON, timeouts, cancellation, bearer interceptor, idempotency header, safe retry (idempotent reads only), normalized ApiError {code,message,field,status}
- Secure storage: credentials only in flutter_secure_storage; never log tokens/signed URLs/payment creds
- Session: Splash restore → `GET /v1/me`, single 401 coordinator, clear + redirect to Welcome
- GoRouter: 5-tab shell (Home/Search/Charts/Library/Cart), compact player slot, modal vs push, auth guard + safe deep-link validation (no arbitrary external URL), onboarding gate
- Tests: unit (error mapping, idempotency), widget (guards), repo (mocked HTTP contract shapes)

### M2 — Splash, Welcome, Login, Signup, Forgot/OTP/Reset, Genre onboarding
**HLFR:** MOB-ID-01, MOB-PREF-01
- LLFR-MOB-ID-01.1..01.6, MOB-PREF-01.1..01.4
- Endpoints: `/auth/*`, `/me`, `/me/password/reset`, `/taxonomy?kind=genre`, `/me/preferences/onboarding`, `/me/preferences`
- OTP screen adapted → email-link instructions (PRD §4 #6, §16); reset token never logged/rendered
- Tests: form validation, taxonomy mapping, onboarding 3-genre gate

### M3 — Home, Search, Charts, Artist, Album, reusable catalog UI
**HLFR:** MOB-DISC-01/02
- Home: `GET /v1/home` (trending/top10/featuredAlbums/rails), pull-to-refresh retains last success, one rail failure doesn't erase others
- Charts: composition over existing trending/top (no invented rank history)
- Search: debounced + cancellable, `GET /v1/search?q=` only for non-empty, `GET /browse-categories`, resolve via `POST /v1/catalog/resolve` (batch ≤200)
- Artist/Album: `GET /artists/:id` + tracks/albums/shows, `GET /albums/:id?tracks=true` with ownership/downloadable states
- Shared widgets: catalog cards, price/ownership badges, image-led surfaces, mono metadata

### M4 — Player service, compact player, Now Playing, Lyrics, previews
**HLFR:** MOB-PLAY-01
- Audio service singleton: queue/position/state, `GET /v1/tracks/:id/stream` immediately before play, honor `previewSeconds` (stop + purchase CTA), signed URL expiry → one refresh resume, background notification + lock-screen + headset, interruptions/audio focus
- Now Playing modal + compact player above bottom nav share same state
- Lyrics: `GET /v1/tracks/:id/lyrics` — 404 = empty state
- Tests: preview timing, expiry refresh, queue logic

### M5 — Collection, playlists, Library, authorized downloads
**HLFR:** MOB-LIB-01, MOB-DL-01
- `GET /v1/me/collection` + resolve, optimistic PUT/DELETE for likes/follows/saves with rollback, playlists CRUD (`/v1/me/playlists*`)
- Ownership badges from server only
- Downloads: eligibility from `downloadableTracks` (not catalog `downloadable`), `GET /v1/tracks/:id/download` → app-private file + manifest (trackId/format/date/path/state: queued/downloading/completed/failed/removing), never persist signed URL, offline playback only from complete local file, sign-out policy (unavailable to next session)

### M6 — Cart, providers, checkout, polling, receipt, ownership refresh
**HLFR:** MOB-COM-01/02
- `GET /v1/me/cart` (server totals authoritative), add/update/remove via cart endpoints, duplicate non-stackable → surface backend validation
- `GET /v1/payments/providers` for rails, `POST /v1/checkout` with fresh UUID idempotency-key (reuse only on uncertain retry), bounded backoff polling `GET /v1/me/orders/:id` lifecycle-aware, distinguish pending/failed/cancelled/timeout, receipt from order snapshot, ownership refresh only after confirmed settlement — no optimistic ownership, MoMo-first GHS copy

### M7 — Podcasts and Store variants
**HLFR:** MOB-POD-01, MOB-STORE-01
- Podcasts: `GET /v1/podcasts?category=&page=` + `GET /v1/podcasts/:id` + `/episodes`, free/owned/preview/season-pass states distinct, follows via collection
- Store: `GET /v1/store?type=&sort=` + `GET /v1/store/:id`, premium/exclusive = filters over types, pre-orders presentation-only, beat-license/merch variants require selection before cart

### M8 — Notifications, settings, gap screens, deep links
**HLFR:** MOB-NOTIF-01, MOB-SET-01, MOB-GAP-01, §6 navigation
- Notifications: `GET /v1/me/notifications`, mark one/all read, deep link → safe in-app destination only
- Settings: `GET /v1/me/preferences` + `PUT`, device prefs (Wi-Fi-only downloads, theme), legal/about, cache clear (no ownership wipe), sign-out stops playback + clears protected memory
- Gaps: Friend Activity + Live Session = polished coming-soon shells, no fabricated data, analytics view event only
- Deep links: reset password, artist/album/track/podcast/storeItem/order receipt — IDs validated

### M9 — Accessibility, golden QA, integration hardening, release builds
**NFR:** §11, Test strategy §12
- Semantics for artwork/player/ownership/price/tabs/progress, dynamic text no-clip, reduced motion, WCAG AA contrast, 44pt targets
- Golden tests: iOS + Android viewports for core screens
- Integration: auth→onboarding→playback→purchase→ownership→downloads
- Hardening: cache corruption fallback, app restart during checkout/download recovery, transport/policy, crash/diagnostics (privacy-conscious)
- Release: unsigned artifacts, hardcoded checks from §15/§17

## Architecture decisions (from design, locked)

- Feature-first vertical slices; repositories isolate DTOs from domain; shared infra only when ≥2 features need it
- Riverpod for session/queries/mutations/player/downloads/connectivity/prefs; immutable states
- One audio service owned queue; UI observes
- Dio rules: retries only for idempotent reads + transport failures; checkout retry only under original idempotency-key

## Risks & mitigations

- Flutter not preinstalled on this host (no brew) → scaffold manually without `flutter create`, use git-clone install in CI; verify with local manual check or CI image
- Prototype is bundled HTML (opaque visuals) → extract tokens by reading bundled CSS/JS once file is available; designer to replicate near-black/magenta/gold system + iOS glass → Android adaptation
- Server invariants (MONEY as {amount,currency GHS} cedis, preview 30s, ownership on settlement, 200-id resolve cap) → map 1:1 from Frontend/src/lib/api/mappers.ts + API-CONTRACT.md, add unit tests per invariant
- Offline/cache policy (§10/§11.2) → app-private storage + manifest, cleared on logout, cache failure → clean state

## Verification per phase

Each phase: `dart format`, `flutter analyze`, `flutter test` (unit+widget+repo), contract-shaped mocked HTTP, integration journey slice, golden where UI present. Final M9 adds cross-phase integration + release build smoke.

## Next step

Proceed with M0 dispatch (3 lanes in parallel):
- Lane A (fixer): `mobile/` scaffold + pubspec + analysis_options + folder skeleton + main.dart shell
- Lane B (designer): design_system tokens + primitives faithful to prototype
- Lane C (fixer/platform): env config + CI workflow for mobile

Lanes A/B can run fully parallel; Lane C depends on A's pubspec/app structure (file paths) — dispatch A+B now, C after A completes or as non-conflicting parallel if scoped to .github/workflows.
