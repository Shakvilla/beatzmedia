# BeatzClik Fan Mobile App — Approved Design

**Date:** 2026-09-20  
**Status:** Approved  
**Targets:** iOS and Android  
**Framework:** Flutter

## Purpose

Build the complete fan-facing BeatzClik mobile experience represented by `Beatzclik Fan App Prototype (Standalone) (1).html`, connected to the existing `/v1` backend. The mobile app must preserve BeatzClik's buy-to-own model, server-enforced previews, ownership semantics, Ghana Cedi money representation, and asynchronous payment settlement behavior.

The standalone prototype is the visual source of truth. `API-CONTRACT.md` is the wire-contract source of truth. The existing React application is the behavioral reference where the prototype is ambiguous. Graphify is used to trace frontend routes and query modules to backend REST resources during implementation.

## Scope

The app covers all 30 prototype screens:

1. Splash
2. Welcome
3. Login
4. Signup
5. Forgot password
6. OTP verification presentation, adapted to email-link recovery
7. Reset password
8. Genre onboarding
9. Home
10. Search
11. Now playing
12. Library
13. Artist profile
14. Album detail
15. Friend activity — honest coming-soon state
16. Paywall and MoMo purchase entry
17. Podcasts
18. Podcast detail
19. Offline downloads
20. Cart
21. Checkout and MoMo processing
22. Receipt
23. Full-screen lyrics
24. Live session — honest coming-soon state
25. Notifications
26. Profile and settings
27. Charts
28. Store
29. Store premium
30. Store pre-orders

Friend activity and live sessions have no corresponding backend capability. They remain polished, non-deceptive coming-soon surfaces. Password recovery uses the backend's email-link flow rather than claiming an unsupported OTP exchange. Charts are composed from supported discovery data. Pre-orders are a store presentation and must not invent availability or purchase behavior absent from the returned store item.

## Architecture

The app is a new top-level `mobile/` Flutter project organized as feature-first vertical slices:

```text
mobile/lib/
  app/             startup, routing, dependency composition, app shell
  core/            API, authentication, storage, connectivity, errors
  design_system/   tokens, typography, icons, reusable visual primitives
  features/        auth, onboarding, discovery, player, library, commerce,
                   podcasts, social, store, notifications, settings
```

The selected implementation stack is:

- Riverpod for application and server state
- GoRouter for guarded navigation and deep links
- Dio for HTTP, interceptors, cancellation, and transfer progress
- Flutter secure storage for bearer credentials
- just_audio and audio_service for playback and background controls
- app-private file storage plus a local manifest for authorized downloads
- freezed/json_serializable for immutable models and wire mapping

Repositories isolate wire DTOs from domain models. Features depend on repository contracts, not directly on Dio. Shared infrastructure is introduced only when at least two features need it.

## Navigation

The signed-in shell has five persistent destinations: Home, Search, Charts, Library, and Cart. A compact player appears above the bottom navigation whenever a track is active. Now Playing is a modal route; detail screens use native push/back behavior.

Startup follows this gate:

```text
Splash -> restore token -> GET /v1/me
  |- invalid or absent session -> Welcome
  |- onboarding incomplete -> Genre onboarding
  `- ready -> Home
```

Authentication, onboarding, payment completion, password-reset links, and content details support deep links. Protected routes redirect to login while retaining a safe intended destination.

## Data and state flow

Riverpod controllers expose explicit loading, data, empty, refreshing, offline, and error states. Likes, follows, playlists, and cart changes may update optimistically with rollback. Checkout and ownership never do.

Checkout is Cart -> payment method -> `POST /v1/checkout` with an idempotency key -> Processing -> order polling -> Receipt. The app refreshes ownership only after confirmed settlement. A pending or failed payment is never represented as a completed purchase.

Playback requests `/tracks/{id}/stream`. The server decides whether the response is a full stream or a preview. The player honors `previewSeconds`, handles signed-URL expiry, and cannot widen playback locally.

## Offline and downloads

Previously loaded read models may be displayed from cache with a visible offline indicator. Mutations that require server confirmation remain disabled or retryable while offline.

Only tracks listed as downloadable for the current owner may be downloaded. The app stores the media file in app-private storage and records track ID, format, download date, path, and transfer state in a local manifest. Signed download URLs are never persisted as durable media references. Removing a local file does not change ownership.

## Errors and security

The client normalizes validation, authentication, authorization, connectivity, unavailable-media, payment, rate-limit, and unexpected failures. UI errors contain a recovery action where one is possible. Logs exclude bearer tokens, signed media URLs, payment credentials, and sensitive profile fields.

A single session-expiry coordinator handles concurrent `401` responses. Money and side-effect POSTs use idempotency keys. Certificate and transport policy use platform-secure defaults; production builds reject cleartext traffic.

## Testing

- Unit tests: mapping, controllers, money formatting, preview behavior, idempotency, download state
- Widget tests: all 30 screens across loading, data, empty, error, offline, and accessibility states
- Repository tests: mocked responses conforming to `API-CONTRACT.md`
- Integration tests: auth, onboarding, preview playback, purchase settlement, ownership refresh, downloads
- Golden tests: representative iOS and Android viewports
- CI: format, analyze, tests, and release builds

## Definition of done

A vertical slice is complete only when its UI matches the approved prototype, real supported endpoints are integrated, unsupported behavior is labelled honestly, loading/empty/error/offline states exist, accessibility semantics are present, and unit/widget tests pass.

