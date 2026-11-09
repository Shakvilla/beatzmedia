# FROG SMS and phone verification implementation plan

Design: [FROG SMS and phone verification](./2026-11-02-frog-sms-phone-verification-design.md)

## Goal

Let an authenticated fan prove ownership of a phone number by receiving and verifying an OTP via FROG SMS. Add FROG credentials and provider status to super-admin settings. Keep Resend email behavior intact and independently routed.

## Work units

### 1. Identity phone verification state and challenges

- Extend `identity.domain.FanSettings` with `phoneVerified` and a safe phone-change transition.
- Add an OTP challenge aggregate with account ownership, normalized destination, keyed code hash, expiry, attempts, resend timing, provider identity, and consumption state.
- Add a repository port and JPA adapter; add a forward-only migration for `fan_settings.phone_verified` and the challenge table/indexes.
- Add an OTP hash secret to `application.properties` and production configuration guard. Generate codes with `SecureRandom`; use constant-time hash comparison.
- Refs: `identity/domain/FanSettings.java`, `identity/application/service/UpdateFanSettingsService.java`, `identity/adapter/out/persistence/FanSettingsMapper.java`, `identity/adapter/out/persistence/JpaAccountRepository.java`, `backend/src/main/resources/db/migration/`.

### 2. Start and confirm API

- Add application input ports/services for start and confirm.
- Start request validates/normalizes the number, enforces per-account and per-destination cooldown/limits, saves a challenge, then sends its code through a cross-module notification SMS input port.
- Confirm validates challenge ownership, expiry, attempt count and hash, consumes it once, saves the phone as verified, and invalidates other active challenges for the account.
- Expose authenticated `POST /v1/me/phone-verification` and `POST /v1/me/phone-verification/confirm`; return masked destination and expiry/cooldown metadata only.
- Add `phoneVerified` to `FanSettingsDto` and current contract types. Phone changes through `PATCH /me/settings` clear verification.
- Refs: `identity/adapter/in/rest/MeResource.java`, `identity/adapter/in/rest/FanSettingsPatch.java`, `identity/adapter/in/rest/FanSettingsDto.java`, `API-CONTRACT.md`, identity architecture ADD.

### 3. SMS routing and FROG adapter

- Keep `SmsSender` as the outbound port; add explicit provider selection and provider-pinned sends, matching `Mailer` behavior.
- Qualify the existing capture sender and add a router that reads current SMS selection for new work and uses the recorded provider for retries.
- Add `FrogSmsSender` using `API-KEY`/`USERNAME`, `/api/v3/sms/send`, the documented `destinations` payload, JSON response parsing, and transient/permanent error mapping. Treat only HTTP success plus `ACCEPTD` as accepted. Send a stable `msgid` derived from the challenge or delivery attempt.
- Add identity transactional-SMS input port and a notifications adapter, following the existing `IdentityContactAdapter` cross-module pattern.
- Pin selected SMS provider in new `delivery_attempt` records; preserve provider identity on retries.
- Refs: `notifications/application/port/out/SmsSender.java`, `notifications/adapter/out/integration/HttpSmsSender.java`, `notifications/application/service/DeliveryAttemptService.java`, `notifications/application/service/DeliveryRetrySweepJob.java`, `notifications/adapter/out/integration/IdentityContactAdapter.java`.

### 4. FROG super-admin configuration

- Add `/admin/provider-config/frog` GET/PUT with redacted status, versioned updates, environment fallback, encrypted database storage, and audit record.
- Add readiness checking from stored configuration or environment; remove FROG's production “unreleased” block only after the adapter is registered.
- Add API query/mutation types and a FROG section to `admin.settings.tsx`; saving credentials and activating SMS remain separate actions.
- Refs: `admin/application/port/in/ManageProviderConfig.java`, `admin/application/service/ProviderConfigService.java`, `admin/adapter/in/rest/AdminProviderConfigResource.java`, `platform/adapter/out/integration/ConfigProviderReadinessProbe.java`, `Frontend/src/lib/api/queries/admin-provider-config.ts`, `Frontend/src/routes/admin.settings.tsx`.

### 5. Phone verification UI and communication behavior

- Replace the settings page's local-only phone placeholder with persisted API state and a phone verification panel: edit number, send code, confirm code, masked number, verified/unverified status, cooldown, and errors.
- SMS notifications require a verified phone. Email and SMS notification delivery stay independent; SMS failure does not suppress Resend or in-app notifications. OTP delivery itself does not fall back to email.
- Refs: `Frontend/src/routes/settings.tsx`, `Frontend/src/lib/api/client.ts`, identity's `GetNotificationContactService`, `notifications/application/service/DispatchSubscriber.java`.

### 6. Verification and documentation

- Cover challenge lifecycle/security, phone-change invalidation, OTP REST ownership, FROG protocol/error mapping, provider readiness/redaction, and email/SMS independence with unit and integration tests.
- Add an adapter-level fake HTTP server test; never call FROG in routine tests.
- Update `API-CONTRACT.md`, identity/notifications/admin architecture docs, deployment/config docs, `backend/.project/backlog.yaml`, and Graphify links after implementation.
- Run focused backend tests and frontend typecheck/build; then migration and broader required gates as practical. No real SMS test without explicit recipient and merchant credentials.

## Acceptance criteria

- Phone verification is single-use, expiring, attempt-limited, throttled, account-bound, and cleared when a different number is saved.
- Only a successful FROG submission is shown as sent; the API never returns the OTP or full phone in logs.
- FROG activation fails closed until credentials, sender ID, encryption, and readiness are valid; `capture` remains the local/test default.
- FROG and Resend can be selected independently. A failure or disabled state in one channel never blocks the other channel or in-app notification persistence.
- Existing sign-in, password reset, email notifications, and Mailpit/Resend selection keep their current behavior.
