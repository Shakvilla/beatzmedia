# Provider integrations design — MEGA S4, Resend, FROG, and Redde

Date: 2026-09-21  
Status: Design agreed in conversation; implementation has not started.  
Scope: Backend provider selection, existing media and message routing, deployment configuration, and verification. Preserve the published API contract unless an additive change is explicitly approved.

## Decision

Use the existing application output ports as provider boundaries. An operator changes the selected provider for **new** operations. Every created payment, payout, and queued delivery records the provider selected at creation. Status checks, webhooks, retries, and reconciliation use that recorded provider until the operation reaches a terminal state. Disabling a provider stops new operations through it; it does not abandon work already accepted by it.

For media, new uploads use the selected object store. Existing assets keep a durable storage location until their objects have been copied and verified. Changing the default store must never reinterpret an old object key as if it were already present in the new store.

Use `disabled` as an explicit production payment choice. Turning Redde off must not silently turn a real checkout into a successful sandbox charge. Sandbox payments remain available only in local/test environments or a deliberately isolated test environment.

## Current state and gaps

| Capability | Existing boundary | Current adapter | Gap |
| --- | --- | --- | --- |
| Media upload/read/signing | `ObjectStorePort`, `UrlSignerPort` | `S3ObjectStoreAdapter`, AWS SDK S3 client, MinIO locally | S3 region is hardcoded to `us-east-1`; bucket and key storage has no provider identity; SDK headers need S4 compatibility check. |
| Payments | `PaymentGateway` | `PaymentGatewayRouter` selects Redde or sandbox on every call | In-flight status checks can go to another gateway after a flag flip; payment and payout records store the rail and provider reference, but not the gateway vendor. |
| Notification email | `notifications.Mailer` | Quarkus SMTP | Resend adapter absent; current SMTP adapter does not use the existing idempotency key. |
| Identity email | `identity.Mailer` | Quarkus SMTP | Separate path for password resets; must share provider selection and delivery infrastructure with transactional email. |
| SMS | `SmsSender` | Generic HTTP adapter with bearer token and `{to, body}` | FROG needs different headers, request body, response parsing, and delivery callbacks. |

## Selection model

Maintain a small provider registry and explicit selection for each capability: object store (`minio`, `mega_s4`), email (`smtp`, `resend`), SMS (`capture`, `frog`), and payment (`disabled`, `sandbox`, `redde`). This is adapter selection inside the Quarkus service, not a runtime code loader. Keep credentials in deployment secrets; keep provider selection in controlled operational settings so it can change without a build. Validation must reject a selection whose adapter, required credentials, or endpoint are unavailable. Add audit entries for privileged selection changes.

Persist a stable provider code on every new `payment_intent`, `payout_txn`, and `delivery_attempt`. Do not reuse the existing `payment_intent.provider` field: it names the payment **rail** (`mtn`, `telecel`, `airteltigo`, `card`, `bank`), not the gateway vendor. Record the gateway choice before or atomically with the outbound initiation attempt so a timeout or retry can recover it. Route by recorded vendor for all later status queries, retries, and callback handling. A callback path identifies its vendor, but the stored operation and provider reference must also match before applying a state change.

For pre-migration records, backfill vendor identity from evidence available at migration time. Do not guess Redde versus sandbox from a currently active flag. Where provider references or historical data cannot distinguish them, review or quarantine unresolved nonterminal records before allowing a switch.

For media, record a store identifier on the durable asset location. The current `media_asset` holds `original_key`, `hls_key` (the full `.m4a` key), `preview_key`, and `lossless_key`; bucket names are global configuration. A first migration may treat the entire asset and its renditions as one location, provided migration copies and verifies all referenced objects before atomically changing the asset's location. If images or other consumers store object references outside `media_asset`, inventory them before selecting this schema. Keep the original source objects through a rollback window.

## MEGA S4 storage

Keep the existing S3 adapter and MinIO for local development and integration tests. Make the S3 endpoint, browser-visible presign endpoint, signing region, credentials, and bucket names specific to each configured store. Keep the originals and delivery buckets private. Preserve the server's ownership check and 30-second preview selection before issuing a short-lived download or playback URL. Images currently streamed through the API should continue using that access path until caching and public-image policy are designed.

MEGA S4 supports S3 path-style requests and SigV4 presigned URLs. Its published coverage excludes presigned POST and several checksum headers. Verify the actual AWS SDK request headers for `PutObject`, read, head, delete, and presigning; configure the SDK only after an S4 test demonstrates any necessary checksum changes. Test audio uploads up to the existing 500 MB limit, transformed artwork, full and preview playback, range requests from browsers, and signed URL expiry. A production cutover consists of: create private buckets and restricted keys; test new writes; copy old originals and all renditions; verify object count, size, and hashes where available; update durable location records; observe reads; then retire source copies after the rollback window.

MEGA source: <https://github.com/meganz/s4-specs/blob/main/README.md>.

## Resend email

Use one transactional email service internally for both notification and identity messages, with small compatibility adapters for the two existing ports. Keep Mailpit/SMTP in local and test profiles. The Resend adapter sends from a verified domain, passes the stable existing `EmailMessage.idempotencyKey` on notification sends, saves the provider message ID, and classifies errors for the existing retry flow. Password-reset messages need a durable idempotency policy that does not expose reset tokens in logs or keys; each newly generated reset token represents a distinct requested message.

Select the email provider when a delivery attempt is created and reuse it on retries. If webhook tracking is enabled, verify Resend's signature against the raw body and deduplicate event IDs before updating delivery state. An accepted API response means submitted, not delivered. Bounce or complaint events can mark a delivery failed and inform future sending policy. Preserve opt-in rules and in-app notification persistence independently of email availability.

Resend sources: <https://resend.com/docs/introduction>, <https://resend.com/java>, <https://resend.com/changelog/idempotency-keys>, <https://resend.com/changelog/managing-webhooks-via-api>.

## FROG SMS

Implement a dedicated FROG adapter for `SmsSender`; retain the capture adapter locally. FROG's general SMS endpoint accepts `API-KEY` and `USERNAME` headers and a JSON payload containing an approved `senderid`, a `destinations` array with recipient `destination` and client `msgid`, `message`, and `smstype`. Derive a stable `msgid` from the delivery attempt; do not generate a fresh ID on retry. Normalize the phone number and validate sender ID before submission. Parse FROG's response body: HTTP 200 with `ACCEPTD` means accepted for processing, not delivered.

Persist the selected SMS provider on the delivery attempt. Map delivery callbacks by `msgid`, deduplicate repeated or out-of-order statuses, and recognize `DELIVRD` as delivered. The published callback page does not specify a cryptographic signature, so establish callback authentication with FROG before making its callbacks authoritative; until then, correlate strictly with known message IDs and avoid exposing message details in responses. Confirm FROG's retry semantics for a repeated `msgid` in a merchant sandbox before enabling automatic retries after an ambiguous timeout.

FROG sources: <https://frogdocs.wigal.com.gh/send_general.html>, <https://frogdocs.wigal.com.gh/callback.html>.

## Redde payments

Retain the existing Redde adapter, receipt/cashout webhook handlers, reconciliation jobs, and ledger rules. Make gateway vendor immutable per payment intent or payout transaction after initiation. New charges and payouts use the currently selected live vendor; all later status checks use their stored vendor. The operator may disable new Redde traffic while old Redde callbacks and reconciliation continue. Do not grant ownership, post payout ledger entries, or conclude a refund from a browser redirect or callback payload alone: use the existing authenticated status pull and idempotent settlement path.

Before production activation, verify the implemented `/v1/receive`, `/v1/checkout/`, `/v1/status/{id}`, `/v1/checkoutstatus/{id}`, and `/v1/cashout` request/response behavior in the merchant sandbox, including callback delivery, duplicate callbacks, pending outcomes, Telecel mapping, card checkout URL handling in the frontend, and failure after a cashout was sent. The supplied Redde docs site, and its newer linked site, did not expose readable endpoint text during this design review; treat exact wire compatibility as unverified until sandbox evidence is collected. Do not enable the `bank` rail for charges unless Redde confirms a supported flow.

Existing design reference: `backend/docs/architecture/payments.md` (WU-PAY-6/7, ADR-27/28/29). Provider site: <https://developers.reddeonline.com/>.

## Configuration and operations

Keep existing `BEATZ_S3_*` and `BEATZ_REDDE_*` names during migration where possible. Proposed additional secret/config names are illustrative until implementation: `BEATZ_S3_REGION`, `BEATZ_S3_PUBLIC_ENDPOINT`, `BEATZ_RESEND_API_KEY`, `BEATZ_RESEND_FROM`, `BEATZ_RESEND_WEBHOOK_SECRET`, `BEATZ_FROG_API_KEY`, `BEATZ_FROG_USERNAME`, `BEATZ_FROG_SENDER_ID`, and `BEATZ_FROG_BASE_URL`. Avoid storing API keys in the feature-flag database or browser environment. Startup and activation validation should report missing configuration without logging secret values.

Use provider-specific health checks and counts for requests, accepted messages, terminal deliveries, failed sends, payment status queries, pending ages, and object store errors. Exclude recipient addresses, phone numbers, tokens, and payment credentials from logs. For rollout, start with sandbox and small internal transactions, then activate new work gradually. The emergency switch blocks new sends or money movement but leaves existing reconciliation workers and provider webhook receivers active.

## Verification and implementation order

1. Introduce provider identity and selection rules, with forward-only migrations and a safe backfill for existing nonterminal records. Add a production `disabled` payment state so deactivation never becomes a fake settlement.
2. Exercise the S3 adapter against MEGA S4 with a disposable bucket; make region and public endpoint configurable, then stage media migration.
3. Add shared transactional email routing and Resend; test both notifications and password reset, idempotent retries, and signed webhooks if enabled.
4. Add the FROG adapter and authenticated callback strategy; test accepted, delivered, rejected, timeout, duplicate callback, and retry cases.
5. Correct payment routing by stored vendor; validate Redde charge, hosted checkout, cashout, and reconciliation against sandbox with real merchant credentials before enabling production traffic.
6. Run the repository's required unit, integration, contract, architecture, migration, coverage, and Compose smoke gates for each implementation work unit. Confirm the frontend contract and preview/ownership/payment invariants remain intact.

## Open operational inputs

- MEGA S4 bucket names, endpoint/region, access credentials, and whether any media already exists in another production store.
- Verified Resend sending domain and sender address; desired email event webhooks.
- FROG account credentials, approved sender ID, callback authentication mechanism, and repeated-`msgid` behavior.
- Redde merchant sandbox and production credentials, callback configuration, exact supported rails, and provider evidence for any in-flight records at cutover.

These are activation inputs, not prerequisites to building the provider boundaries. Do not put actual secret values in this document or source control.
