# BeatzClik Fan Mobile

Flutter fan app — Ghanaian and African music discovery + buy-to-own marketplace. Backed by `/v1` API (`API-CONTRACT.md`). Visual source: `Beatzclik Fan App Prototype (Standalone) (1).html`.

## Stack

Flutter 3.24+ / Dart 3.5+, Riverpod + GoRouter + Dio + flutter_secure_storage + just_audio/audio_service, freezed/json_serializable, Hive (download manifest + cache), cached_network_image.

Feature-first: `lib/app/` (startup, router, shell), `lib/core/` (api, errors, storage, connectivity), `lib/design_system/` (tokens), `lib/features/*`.

## Prerequisites

- Flutter stable (see `mobile/pubspec.yaml` env). Install via `git clone https://github.com/flutter/flutter.git -b stable`.
- Backend running at `http://127.0.0.1:8080` for local dev (`./mvnw quarkus:dev` from `backend/`).

## Run

```bash
cd mobile
flutter pub get
dart run build_runner build --delete-conflicting-outputs
flutter run --dart-define=API_ORIGIN=http://127.0.0.1:8080   # local
flutter run --dart-define=API_ORIGIN=https://api.beatzclik.com # prod default
```

Env: `API_ORIGIN` via `--dart-define` (see `lib/core/api/env.dart` and `.env.example`). Release builds require `https://`.

## Checks (must be green before PR)

```bash
dart format --set-exit-if-changed .
flutter analyze
flutter test
```

CI mirrors this + unsigned release build.

## Project map

```
mobile/lib/
  app/            router, shell
  core/api/       Dio client, env
  core/errors/    ApiError
  core/storage/   secure_store
  design_system/  tokens (canvas #0A0A0B, surface #1C1C1F, primary #EE4094, text #F6F4EF, gold #D4AF37)
  features/       auth, onboarding, discovery, player, library, commerce, podcasts, store, notifications, settings
```

See `docs/plans/2026-09-21-beatzclik-fan-mobile-implementation.md` for phased roadmap M0→M9.
