# Release Checklist — BeatzClik Fan Mobile (M9)

## Definition of Done (MOBILE-PRD §17 + Approved Design §Definition of done)

- [ ] All 30 prototype screens have approved disposition (28 functional + 2 honest coming-soon Friend Activity/Live Session, no fabricated data)
- [ ] Supported endpoints integrated per API-CONTRACT.md (auth, taxonomy, home/search/catalog resolve, stream, lyrics, collection, cart/checkout/polling/orders, store, podcasts, notifications, preferences)
- [ ] Money uses {amount:int,currency:GHS} GHS cedis, int arithmetic, Dio JSON, never recomputed client-side for tx authority
- [ ] Ownership only from server after confirmed settlement — no optimistic unlock, preview honor previewSeconds
- [ ] Secure storage only for bearer token, never log token/signed URL/payment bodies, HTTPS required in release (AppEnv.isSecureOrigin)
- [ ] Responsive: small→large phones, safe areas, dynamic text no-clip, reduced motion, 44pt targets, WCAG AA
- [ ] Navigation: 5-tab shell (Home/Search/Charts/Library/Cart) + compact player above nav, modal Now Playing, push details, deep links (reset, artist, album, track, podcast, store, receipt) with _isSafePath
- [ ] Error/empty/offline: every server-backed screen has loading/content/empty/recoverable error/terminal/offline-stale + retry

## Hardening (NFR §11)

- [ ] App restart during checkout → orderId persisted in checkoutProvider idempotencyKey, polling resumes
- [ ] Download restart → queued/downloading/completed/failed/removing, resume/restart, sign-out clears protected memory + makes downloads unavailable
- [ ] One rail failure doesn’t erase home feed, cache corruption → clean state without ownership loss
- [ ] Single 401 coordinator (sessionProvider logout) prevents thundering herd

## Quality Gates (MOBILE-PRD §12 + CI)

- [ ] `dart format --set-exit-if-changed .` green
- [ ] `flutter analyze` green (flutter_lints)
- [ ] `flutter test` green (unit: DTO mapping, idempotency, preview timing; widget: all 30 screens loading/empty/error/offline; repo: mocked HTTP contract; integration: auth→onboarding→playback→purchase→ownership→downloads)
- [ ] Golden: iOS + Android viewports for Home, Search, Player, Library, Cart (M9)
- [ ] CI `mobile` job (`subosito/flutter-action` 3.24.5, pub get, format, analyze, test, apk --debug) green on PR

## Observability

- Crash reporting + structured diagnostics when configured, non-sensitive IDs only, events: screen load, playback start, preview complete, cart action, checkout initiation, settlement result, download result

## Human Gates

- Deploy secrets (Redde/payment prod keys) confirmed before prod
- Final logout retention policy for local downloads confirmed
- Branch `feat/mobile-fan-*` → PR with Conventional Commit, template (WU/LLFR, DoD, test evidence, migrations, ADD)
