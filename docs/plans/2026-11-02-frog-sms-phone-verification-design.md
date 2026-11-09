# FROG SMS and phone verification design

Date: 2026-11-02
Status: Approved direction; implementation plan
Scope: FROG transactional SMS provider, super-admin FROG configuration, and authenticated fan phone-number verification. Login MFA is deferred.

## Context

The platform already has provider selection for `sms` (`capture`, `frog`, `disabled`), encrypted/versioned provider runtime configuration, provider readiness checks, audited super-admin configuration, separate notification delivery attempts, and a `SmsSender` output port. The current SMS adapter still sends a generic `{to, body, idempotencyKey}` request and is not FROG-compatible. `fan_settings.phone` exists, but there is no phone verified state or OTP workflow. Email has Resend selection and a provider configuration form in the super-admin settings UI.

FROG general SMS uses `POST /api/v3/sms/send`, headers `API-KEY` and `USERNAME`, and a JSON body with `senderid`, `destinations[{destination,msgid}]`, `message`, and `smstype`. `ACCEPTD` means accepted for processing, not delivered. Delivery status is reported later by the configured callback URL. FROG separately offers provider-managed OTP APIs, but BeatzClik will use its general SMS endpoint for a BeatzClik-managed verification code.

## Decisions

1. Identity owns verification state and OTP challenges. Store phone verification separately from account/admin verification.
2. BeatzClik generates a cryptographically random numeric code, stores only a keyed hash, and verifies it locally. Use a 5-minute expiry, bounded verification attempts, and resend cooldown/rate limits. Never log OTPs or full phone numbers.
3. An authenticated user requests verification for a normalized phone number. The challenge binds to account ID and exact normalized number. Successful verification marks only that number verified. A changed phone immediately becomes unverified. Verification is not a login requirement in this release.
4. OTP delivery is SMS-only. Email is not a fallback because email receipt does not establish control of the phone number. Existing transactional email remains on Resend/SMTP selection.
5. FROG is implemented as an outbound adapter behind `SmsSender`; capture remains the local/test adapter. Provider selection is pinned to each delivery attempt as already modeled, so retries use the same provider after a settings switch.
6. For ordinary notifications, email and SMS remain independent attempts. Failure/disablement of one channel must not prevent the other or the in-app notification. SMS dispatch requires the current SMS contact/consent rules plus a verified phone.
7. Super-admin FROG configuration contains API key, username, and approved sender ID. It uses the existing encrypted runtime configuration store, redacted read views, expected-version updates, audit writes, and provider readiness/selection flow. Environment configuration remains a supported fallback. The FROG base URL stays a server-side setting with a production-safe default/allowlist; it is never user-controlled from the admin browser.
8. Callback status can update delivery state only after callback authentication is agreed with Wigal. Until then, callback integration is deferred or observation-only and cannot claim delivery as authoritative.

## API and state outline

- Add a phone verification resource under `/v1/me`, authenticated and scoped to the JWT subject. `POST /v1/me/phone-verification` starts/restarts a challenge for a submitted phone number; `POST /v1/me/phone-verification/confirm` accepts the submitted code. Responses expose masked number, challenge expiry/resend availability, and verification state only; never return the code.
- Extend current-account/settings wire types with `phoneVerified` (additive). Existing `PATCH /me/settings` phone updates clear verification when the normalized value changes.
- Add a challenge persistence table with challenge ID, account ID, normalized phone, keyed code hash, created/expiry times, attempt count, consumed time, and send status. Enforce at most one active challenge per account; expire/consume challenges atomically and rate-limit by account plus destination.
- Add a FROG config read/write resource under `/v1/admin/provider-config/frog`, mirroring Resend's redacted/versioned pattern. Provider selection remains `/v1/admin/provider-config/sms`.

## Frontend outline

Add phone-verification status and actions to the user's account/contact settings surface. The phone entry starts verification; a second form submits the code; show masked destination, expiry/resend timer, loading/error/success states, and a clear unverified badge. Admin Provider settings adds FROG fields and readiness copy; switching to FROG remains a separate explicit action after saving valid credentials. Email provider controls remain unchanged.

## Error and security behavior

Normalize Ghana local numbers to E.164 using selected country context; reject malformed numbers before sending. A challenge is single-use, expiry-bound, attempt-limited, and throttled. Sending is idempotent per challenge/message ID where the provider supports it. A timeout after request submission is ambiguous; do not issue a new provider message ID on retry. Distinguish FROG queued acceptance from actual delivery. Map invalid/config/auth/sender errors to permanent delivery errors; throttling, network, and server errors to transient errors with bounded retry. Do not include credentials, codes, full phones, or provider response bodies in logs or error payloads.

## Verification plan

Unit tests cover phone normalization, challenge lifecycle, rate limits, attempt exhaustion, code hashing, provider request/response mapping, config validation/redaction, and selection pinning. Integration tests cover start/confirm, changed number invalidation, owner isolation, FROG accepted/rejected/timeout responses, retries, and channel independence. Keep FROG calls behind a fake HTTP server in tests; local development continues to use capture. Update `API-CONTRACT.md`, identity/notifications/admin architecture docs, configuration reference, and graphify references. Run focused backend/frontend verification and migration checks; no real SMS is sent without merchant credentials and explicit test recipient.

## Implementation sequence

1. Add identity challenge domain/application ports and persistence, plus a forward-only Flyway migration.
2. Add authenticated start/confirm endpoints and `phoneVerified` to account/settings responses. Clear it when the number changes.
3. Add FROG transport and provider selection routing, retaining the capture adapter for local/test.
4. Add encrypted FROG credentials, super-admin controls, audit/version checks, and readiness validation.
5. Add user phone verification UI and focused tests; preserve independent Resend email delivery.
6. Update API, architecture, deployment configuration, and graph references; verify migration, backend, and frontend gates.

## Source documentation

- [FROG general SMS](https://frogdocs.wigal.com.gh/send_general.html)
- [FROG delivery callback](https://frogdocs.wigal.com.gh/callback.html)
- [FROG OTP generation](https://frogdocs.wigal.com.gh/otp_generate.html)
- [FROG OTP verification](https://frogdocs.wigal.com.gh/otp_verify.html)
- Existing provider design: `docs/plans/2026-09-21-provider-integrations-design.md`
