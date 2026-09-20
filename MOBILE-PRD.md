# BeatzClik Fan Mobile App — Product Requirements Document

**Version:** 1.0  
**Date:** 2026-09-20  
**Status:** Approved for implementation  
**Product surface:** Fan mobile app  
**Platforms:** iOS and Android  
**Technology:** Flutter

## 1. Overview

BeatzClik is a Ghanaian and African music platform combining streaming discovery with a buy-to-own marketplace. This PRD defines the production Flutter fan app represented by `Beatzclik Fan App Prototype (Standalone) (1).html` and backed by the existing BeatzClik REST API.

The mobile app gives fans one coherent place to discover music and podcasts, preview and play content, buy permanent ownership, manage a personal library, download eligible purchases, and receive platform notifications. It must reproduce all 30 prototype screens without fabricating backend capabilities.

### 1.1 Source-of-truth order

1. `API-CONTRACT.md` defines supported network behavior and wire shapes.
2. `Frontend/src/lib/api/`, `Frontend/src/features/`, and `Frontend/src/routes/` define existing consumer behavior.
3. The standalone fan prototype defines mobile visual direction and screen composition.
4. `BACKEND-PRD.md` defines domain invariants and backend intent.

Graphify's repository graph is used during delivery to trace routes, query functions, DTO mappers, and REST resources. It does not override the API contract.

### 1.2 Goals

- Deliver all 30 fan prototype screens on iOS and Android.
- Connect every supported action to the existing production-style API.
- Preserve server authority for ownership, previews, payment settlement, and download eligibility.
- Provide resilient playback, offline-owned downloads, and clear recovery from network failures.
- Achieve a visually distinctive, accessible implementation faithful to the prototype.

### 1.3 Non-goals

- Artist Studio and Admin Console functionality.
- New friend-graph or social-feed backend functionality.
- Live-session broadcasting, chat, or attendance services.
- A client-generated OTP protocol not present in the backend.
- Client-side payment settlement or ownership decisions.
- DRM claims beyond the protection provided by app-private storage and the server's authorization model.

## 2. Personas and primary journeys

### 2.1 Visitor

A visitor can open the app, view supported public discovery content, search the catalog, inspect artists and albums, and hear server-authorized previews. Authentication is required before any personal collection, purchase, notification, or download action.

### 2.2 Fan

A fan can complete onboarding, play owned or free content, receive previews for non-owned paid content, manage a collection, purchase content through enabled payment providers, access receipts, and download eligible purchases.

### 2.3 Returning buyer

A returning buyer expects owned content to remain owned, including after reinstall and on another device. Ownership is server state. Local downloads are device state and may need to be downloaded again.

## 3. Experience principles

1. **Music first.** Content and playback dominate the visual hierarchy.
2. **Ownership is explicit.** Owned, free, preview, price, and downloadable states must never be ambiguous.
3. **Payment truth over optimism.** A charge initiation is not a purchase success.
4. **Ghana-native commerce.** MoMo is a primary path, prices use GHS, and local payment terminology is clear.
5. **No fake affordances.** Unsupported friend activity, live sessions, or OTP exchanges are labelled rather than simulated.
6. **Recoverable by design.** Interrupted playback, downloads, authentication, and payment polling have safe recovery paths.

## 4. Screen inventory and disposition

| # | Screen | Release behavior | Primary data/capability |
|---:|---|---|---|
| 1 | Splash | Functional | Local startup and session hydration |
| 2 | Welcome | Functional | Local navigation |
| 3 | Login | Functional | Auth login and social-auth contract where configured |
| 4 | Signup | Functional | Account registration |
| 5 | Forgot password | Functional | Password-reset request |
| 6 | OTP verification | Adapted | Email-link sent/awaiting-instructions presentation |
| 7 | Reset password | Functional | Reset token redemption |
| 8 | Genre onboarding | Functional | Taxonomy and fan preferences |
| 9 | Home | Functional | Home feed and catalog entities |
| 10 | Search | Functional | Catalog search |
| 11 | Now playing | Functional | Signed stream and player state |
| 12 | Library | Functional | Collection and catalog resolution |
| 13 | Artist profile | Functional | Artist, tracks, and albums |
| 14 | Album detail | Functional | Album with tracks and ownership |
| 15 | Friend activity | Coming soon | No backend endpoint |
| 16 | Paywall / MoMo | Functional | Cart, providers, and checkout entry |
| 17 | Podcasts | Functional | Podcast catalog |
| 18 | Podcast detail | Functional | Show and episode catalog |
| 19 | Offline downloads | Functional | Collection eligibility and signed download URL |
| 20 | Cart | Functional | Server-backed cart |
| 21 | Checkout / MoMo | Functional | Checkout and order polling |
| 22 | Receipt | Functional | Order snapshot |
| 23 | Lyrics | Functional with empty state | Track lyrics; 404 means unavailable |
| 24 | Live session | Coming soon | No backend endpoint |
| 25 | Notifications | Functional | Notification feed and read state |
| 26 | Profile and settings | Functional | Account, preferences, and session actions |
| 27 | Charts | Functional composition | Home trending/top data; no invented chart service |
| 28 | Store | Functional | Store catalog |
| 29 | Store premium | Functional filter | Store catalog premium/exclusive types |
| 30 | Store pre-orders | Honest filtered state | Store data only; no unsupported reservation action |

## 5. Functional requirements

### 5.1 Identity and session

#### HLFR-MOB-ID-01 — Account access

The app shall let a visitor register, sign in, restore an authenticated session, request password recovery, redeem a valid reset token, and sign out.

- **LLFR-MOB-ID-01.1:** Splash shall restore a securely stored bearer credential and call `GET /v1/me` before selecting the first protected route.
- **LLFR-MOB-ID-01.2:** Login shall validate required email/password input locally, call the backend login endpoint, persist the returned token securely, and load the account.
- **LLFR-MOB-ID-01.3:** Signup shall validate required fields and server errors without losing entered non-secret fields.
- **LLFR-MOB-ID-01.4:** Password recovery shall call the reset-request endpoint and display email-link instructions. It shall not claim that an OTP was issued.
- **LLFR-MOB-ID-01.5:** A reset deep link shall provide its token only to the reset endpoint and shall never log or render the token.
- **LLFR-MOB-ID-01.6:** Any terminal session failure shall clear credentials and protected in-memory state, then return to Welcome.

**Acceptance:** Given a valid stored session, when the app starts, then the fan reaches the correct post-onboarding destination without seeing the login screen. Given an invalid session, then credentials are cleared and Welcome is shown.

### 5.2 Onboarding and preferences

#### HLFR-MOB-PREF-01 — Fan taste profile

The app shall require a newly registered fan to select at least three valid genres and shall allow later preference editing.

- **LLFR-MOB-PREF-01.1:** Genre choices come from `GET /v1/taxonomy?kind=genre`.
- **LLFR-MOB-PREF-01.2:** Initial submission uses `POST /v1/me/preferences/onboarding` and remains blocked below three selections.
- **LLFR-MOB-PREF-01.3:** Later edits use `PUT /v1/me/preferences`.
- **LLFR-MOB-PREF-01.4:** Unknown/deactivated genres returned as validation failures shall be removed or highlighted before retry.

### 5.3 Discovery and catalog

#### HLFR-MOB-DISC-01 — Home and charts

The app shall present the home feed and chart views using supported home-feed data.

- **LLFR-MOB-DISC-01.1:** Home consumes `/v1/home` and renders trending tracks, top tracks, featured albums, new releases, popular artists, and curated playlists.
- **LLFR-MOB-DISC-01.2:** Charts uses supported trending/top collections and labels them according to the returned semantics; it shall not invent rank history or play counts.
- **LLFR-MOB-DISC-01.3:** Pull-to-refresh retains the last successful view until replacement data arrives.

#### HLFR-MOB-DISC-02 — Search and entity details

The app shall let fans search and inspect catalog entities.

- **LLFR-MOB-DISC-02.1:** Search is debounced, cancellable, and calls `/v1/search?q=` only for meaningful non-empty input.
- **LLFR-MOB-DISC-02.2:** Artist detail loads the artist plus artist tracks and albums.
- **LLFR-MOB-DISC-02.3:** Album detail requests tracks and displays price, ownership, downloadable, and play states from returned data.
- **LLFR-MOB-DISC-02.4:** Editorial playlist track IDs are resolved through `/v1/catalog/resolve` in batches within the contract cap.

### 5.4 Playback and lyrics

#### HLFR-MOB-PLAY-01 — Authorized playback

The app shall provide foreground and background audio while treating the server response as the authority for full versus preview playback.

- **LLFR-MOB-PLAY-01.1:** Selecting a track requests `GET /v1/tracks/{id}/stream` immediately before playback.
- **LLFR-MOB-PLAY-01.2:** When `previewSeconds` is non-null, playback stops at that boundary and presents an ownership purchase action.
- **LLFR-MOB-PLAY-01.3:** Signed URL expiry triggers one safe stream refresh and resumes near the prior position where permitted.
- **LLFR-MOB-PLAY-01.4:** The queue, compact player, Now Playing, lock-screen metadata, and notification controls share one player state.
- **LLFR-MOB-PLAY-01.5:** Lyrics uses `/v1/tracks/{id}/lyrics`; a 404 is an empty lyrics state, not a missing-track error.

### 5.5 Library and collection

#### HLFR-MOB-LIB-01 — Personal collection

The app shall provide server-backed likes, follows, saved albums, playlists, owned tracks, and downloadable-track eligibility.

- **LLFR-MOB-LIB-01.1:** Library hydrates from `GET /v1/me/collection` and resolves referenced catalog entities as needed.
- **LLFR-MOB-LIB-01.2:** Likes/follows/saves use the corresponding PUT/DELETE membership endpoint with optimistic UI and rollback.
- **LLFR-MOB-LIB-01.3:** Fans may create, rename, delete, and edit user playlists through `/v1/me/playlists` endpoints.
- **LLFR-MOB-LIB-01.4:** Owned badges come only from server ownership data and never from local purchase intent.

### 5.6 Offline downloads

#### HLFR-MOB-DL-01 — Device-local owned media

The app shall allow eligible owners to download and play authorized tracks offline.

- **LLFR-MOB-DL-01.1:** A download action is shown only when the collection includes the track in `downloadableTracks`.
- **LLFR-MOB-DL-01.2:** The app requests `GET /v1/tracks/{id}/download`, then transfers the returned asset into app-private storage.
- **LLFR-MOB-DL-01.3:** The signed URL is not stored as the durable media location.
- **LLFR-MOB-DL-01.4:** Transfer state supports queued, downloading, completed, failed, and removing.
- **LLFR-MOB-DL-01.5:** Offline playback uses only a complete local file associated with the signed-in owner.
- **LLFR-MOB-DL-01.6:** Sign-out makes protected local media unavailable to the next session; product policy may choose deletion or account-scoped retention.

### 5.7 Commerce and payments

#### HLFR-MOB-COM-01 — Server-backed cart

The app shall display and mutate the authenticated fan's cart using server-calculated totals.

- **LLFR-MOB-COM-01.1:** Cart data comes from `GET /v1/me/cart`.
- **LLFR-MOB-COM-01.2:** Add, quantity update, and removal actions use the corresponding cart endpoints.
- **LLFR-MOB-COM-01.3:** Subtotal, fee, and total are rendered from the response, never recomputed as transaction authority.
- **LLFR-MOB-COM-01.4:** Duplicate non-stackable ownership attempts surface the backend's validation result.

#### HLFR-MOB-COM-02 — Checkout and settlement

The app shall initiate payment, monitor settlement, and show a receipt only for a terminal successful order.

- **LLFR-MOB-COM-02.1:** Enabled rails come from `GET /v1/payments/providers`.
- **LLFR-MOB-COM-02.2:** `POST /v1/checkout` includes a fresh UUID idempotency key for one logical checkout attempt and reuses it only when retrying that same uncertain attempt.
- **LLFR-MOB-COM-02.3:** Processing polls `GET /v1/me/orders/{orderId}` with bounded backoff and app-lifecycle awareness.
- **LLFR-MOB-COM-02.4:** Ownership and collection refresh only after the order reports confirmed settlement.
- **LLFR-MOB-COM-02.5:** Pending, failed, cancelled, and timed-out outcomes remain distinguishable and recoverable.
- **LLFR-MOB-COM-02.6:** Receipt totals and line items come from the order snapshot.

### 5.8 Store

#### HLFR-MOB-STORE-01 — Marketplace browsing

The app shall present store inventory and purchase eligible items through the shared cart.

- **LLFR-MOB-STORE-01.1:** Store lists and details use `/v1/store` and `/v1/store/{id}`.
- **LLFR-MOB-STORE-01.2:** Premium/exclusive tabs are filters over supported store item types.
- **LLFR-MOB-STORE-01.3:** Pre-order presentation shall expose only states present in store data and shall not create an unsupported reservation operation.
- **LLFR-MOB-STORE-01.4:** Beat-license or merchandise variants require an explicit selection before adding to cart.

### 5.9 Podcasts

#### HLFR-MOB-POD-01 — Podcast discovery and ownership

The app shall support podcast browsing, show details, episode playback, follows, and purchasable episode or season items where returned by the API.

- **LLFR-MOB-POD-01.1:** Podcast lists support the contract's category and paging/filter parameters.
- **LLFR-MOB-POD-01.2:** Detail loads the show and `/v1/podcasts/{id}/episodes`.
- **LLFR-MOB-POD-01.3:** Free, owned, preview, episode-price, and season-pass states must be visually distinct.
- **LLFR-MOB-POD-01.4:** Podcast follows use the collection membership endpoint.

### 5.10 Notifications

#### HLFR-MOB-NOTIF-01 — In-app notification feed

The app shall show the authenticated fan's notifications and maintain read state.

- **LLFR-MOB-NOTIF-01.1:** The feed comes from `GET /v1/me/notifications`.
- **LLFR-MOB-NOTIF-01.2:** One notification or all notifications may be marked read through the corresponding POST endpoint.
- **LLFR-MOB-NOTIF-01.3:** A notification deep link may navigate only to a recognized safe in-app destination.
- **LLFR-MOB-NOTIF-01.4:** Push transport is out of scope unless a configured device-registration endpoint is added to the contract.

### 5.11 Settings

#### HLFR-MOB-SET-01 — Fan controls

The app shall provide account information, taste preferences, Wi-Fi-only download preference, theme preference, legal/about links, cache controls, and sign-out.

- **LLFR-MOB-SET-01.1:** Server-backed preferences use the preference contract; device-only settings remain local.
- **LLFR-MOB-SET-01.2:** Clearing cache shall not remove active ownership or server collection state.
- **LLFR-MOB-SET-01.3:** Sign-out stops playback, clears protected memory, invalidates credentials, and applies the approved local-download policy.

### 5.12 Contract-gap screens

#### HLFR-MOB-GAP-01 — Honest unavailable experiences

The app shall include the prototype's Friend Activity and Live Session screens without presenting unsupported functionality as active.

- **LLFR-MOB-GAP-01.1:** Friend Activity renders the approved visual shell, a concise coming-soon explanation, and no fabricated people or listening telemetry.
- **LLFR-MOB-GAP-01.2:** Live Session renders the approved visual shell, a concise coming-soon explanation, and no fake join/chat/payment action.
- **LLFR-MOB-GAP-01.3:** Analytics events may record that these screens were viewed but must not include sensitive listening content.

## 6. Navigation requirements

- Five signed-in root destinations: Home, Search, Charts, Library, Cart.
- Compact player above the root navigation whenever a playable item is loaded.
- Modal routes: Now Playing, paywall, checkout processing, receipt, lyrics, and future live presentation.
- Push routes: artist, album, podcast, downloads, notifications, store sections, and settings.
- Deep links: reset password, artist, album, track, podcast, store item, and order receipt where identifiers are valid.
- Auth redirects retain only a validated in-app path and never an arbitrary external URL.

## 7. Visual and interaction requirements

The implementation shall reproduce the prototype's near-black canvas, warm off-white typography, magenta primary action, gold ownership accent, image-led content surfaces, compact mono metadata, rounded controls, and iOS-inspired glass treatment while adapting appropriately to Android system conventions.

The UI must support small phones through large phones, device safe areas, dynamic text, reduced motion, screen readers, external keyboards where relevant, and minimum 44×44 logical-point primary touch targets. Color cannot be the sole ownership, error, or selection indicator.

## 8. Technical architecture

### 8.1 Project layout

The app resides in `mobile/` and uses feature-first slices with explicit presentation, application, domain, and data boundaries where complexity requires them. Simple presentational components are not forced through ceremonial layers.

### 8.2 State management

Riverpod owns session state, queries, mutations, player state, downloads, connectivity, and device preferences. Controllers expose immutable states. Long-lived global state is limited to authentication, player, download manager, connectivity, and app settings.

### 8.3 Networking

Dio uses a configurable API origin plus `/v1`, JSON defaults, request timeouts, cancellation, a bearer interceptor, idempotency support, safe retry rules, and normalized API errors. Automatic retries are limited to safe idempotent reads and transport failures. Checkout is retried only under its original logical idempotency key.

### 8.4 Persistence

Secure storage contains credentials. Lightweight local persistence contains cache metadata, device preferences, and download manifests. Media files remain in app-private storage. Schema changes are versioned and migratable.

### 8.5 Playback

One audio service owns the queue, position, current item, playback state, background notification, interruptions, audio focus, and headset controls. UI screens observe it rather than creating audio players.

## 9. Error, empty, and offline states

Every server-backed screen shall implement loading, content, empty, recoverable error, terminal error where applicable, refresh, and offline-stale states. Validation errors appear near the responsible input. Authentication expiry has one coordinated recovery path. Payment and download errors preserve enough non-sensitive state to retry safely.

## 10. Security and privacy

- Store bearer tokens only in platform secure storage.
- Never log tokens, reset tokens, signed media URLs, payment credentials, or full sensitive request bodies.
- Require HTTPS in non-development builds.
- Treat all deep-link identifiers and destinations as untrusted input.
- Keep downloaded files out of public media galleries and file-provider exports.
- Clear protected memory on logout and prevent cross-account cache leakage.
- Use idempotency keys for money and side-effect requests.
- Do not infer ownership from receipts cached only on the device.

## 11. Non-functional requirements

### 11.1 Performance

- First useful frame displays without waiting for remote catalog data.
- Standard navigation animation targets 60 fps on supported mid-range devices.
- Long rails and lists use lazy construction, bounded image decoding, and pagination where supported.
- Search cancels obsolete requests.
- Player operations remain responsive while screens load.

### 11.2 Reliability

- App restart during checkout can recover an in-progress order when its identifier is available.
- App restart during a download results in a resumable or safely restartable transfer state.
- One failed rail does not necessarily erase already available home content.
- Cache corruption falls back to a clean local state without affecting server ownership.

### 11.3 Accessibility

- Screen-reader names and states exist for artwork actions, player controls, ownership, prices, tabs, and progress.
- Dynamic text does not clip essential content at supported accessibility sizes.
- Reduced-motion preference suppresses nonessential scale and parallax effects.
- Contrast meets WCAG AA for essential text and controls.

### 11.4 Observability

Release builds support privacy-conscious crash reporting and structured diagnostics when configured. Events use non-sensitive identifiers and distinguish screen load, playback start, preview completion, cart action, checkout initiation, settlement result, and download result.

## 12. Test strategy and quality gates

| Layer | Required coverage |
|---|---|
| Unit | DTO mapping, controllers, formatters, preview timing, idempotency, download transitions |
| Widget | All 30 screens and their meaningful loading/empty/error/offline states |
| Repository | Contract-shaped success and error responses with mocked HTTP |
| Integration | Auth, onboarding, playback, checkout settlement, ownership refresh, downloads |
| Golden | Core screens on representative iOS and Android sizes |
| Accessibility | Semantics, text scaling, contrast review, touch-target review |

Required CI commands are `dart format --set-exit-if-changed .`, `flutter analyze`, `flutter test`, and platform release builds in the appropriate signing-free CI mode.

## 13. Delivery roadmap

| Phase | Work units | Depends on |
|---|---|---|
| M0 | Flutter scaffold, design tokens, CI, environment configuration | — |
| M1 | API core, errors, secure session, router guards | M0 |
| M2 | Splash, welcome, login, signup, reset, onboarding | M1 |
| M3 | Home, search, charts, artist, album, reusable catalog UI | M2 |
| M4 | Player service, compact player, Now Playing, lyrics, previews | M3 |
| M5 | Collection, playlists, library, authorized downloads | M4 |
| M6 | Cart, providers, checkout, polling, receipt, ownership refresh | M5 |
| M7 | Podcasts and store variants | M6 |
| M8 | Notifications, settings, gap screens, deep links | M7 |
| M9 | Accessibility, golden QA, integration hardening, release builds | M8 |

Each phase is delivered as vertical slices; UI, integration, errors, accessibility, and tests ship together.

## 14. Requirements traceability

| Capability | Requirements | Prototype screens | Existing API areas |
|---|---|---|---|
| Identity | HLFR-MOB-ID-01 | 1–7 | Auth, `/me`, password reset |
| Preferences | HLFR-MOB-PREF-01 | 8, 26 | Taxonomy, `/me/preferences` |
| Discovery | HLFR-MOB-DISC-01/02 | 9, 10, 13, 14, 27 | Home, search, catalog resolve |
| Playback | HLFR-MOB-PLAY-01 | 11, 23 | Track stream, lyrics |
| Library | HLFR-MOB-LIB-01 | 12 | Collection and playlists |
| Downloads | HLFR-MOB-DL-01 | 19 | Collection eligibility, track download |
| Commerce | HLFR-MOB-COM-01/02 | 16, 20–22 | Cart, providers, checkout, orders |
| Podcasts | HLFR-MOB-POD-01 | 17, 18 | Podcasts and episodes |
| Store | HLFR-MOB-STORE-01 | 28–30 | Store list/detail |
| Notifications | HLFR-MOB-NOTIF-01 | 25 | `/me/notifications` |
| Settings | HLFR-MOB-SET-01 | 26 | Account/preferences plus device state |
| Contract gaps | HLFR-MOB-GAP-01 | 15, 24 | None; honest coming-soon states |

## 15. Success metrics

- 100% of the 30-screen inventory is implemented with its approved functional or honest-gap disposition.
- No client path grants ownership before backend-confirmed settlement.
- No non-owner playback exceeds the server-authorized preview.
- All money displayed from API models uses GHS formatting without floating-point transaction logic.
- Critical integration journeys pass on both iOS and Android CI targets.
- Crash-free session and playback-start success metrics meet release targets established before production rollout.
- Accessibility and golden-review gates pass for all primary journeys.

## 16. Open decisions and approved defaults

| Topic | Approved default |
|---|---|
| Friend activity | Coming-soon screen; no fabricated feed |
| Live sessions | Coming-soon screen; no fabricated join/chat |
| OTP screen | Adapted to truthful email-link instructions |
| Charts | Derived from supported home feed collections |
| Pre-orders | Presentation-only unless the store response explicitly supports a state/action |
| Platforms | iOS and Android have equal release scope |
| Architecture | Feature-first vertical slices using Riverpod, GoRouter, and Dio |
| Local download protection | App-private, account-scoped access; final logout retention policy to be confirmed before release |

## 17. Definition of done

The mobile app is ready for release when every screen has the approved disposition, supported endpoints are integrated, core journeys pass on iOS and Android, signed-stream and settlement invariants are preserved, protected data is handled safely, accessibility checks pass, and CI produces unsigned release artifacts without analysis or test failures.

