# Provider integrations: requirements and acceptance scenarios

**Parent:** [PRD §13](../../../BACKEND-PRD.md#13-provider-integrations--phase-5). Existing LLFR and invariant IDs remain unchanged. These new IDs are additive and may be referenced in PRs and tests.

## Goals and boundaries

The service can choose MEGA S4, Resend, FROG, and Redde without changing frontend behavior or losing access to prior operations. Local development keeps MinIO, Mailpit, SMS capture, and isolated sandbox payments. A production provider outage can stop **new** operations while previously accepted operations continue to reconcile through their recorded provider. No secrets are stored in the provider-selection table or returned to the frontend. Super-admin-managed provider credentials are encrypted at rest with a deployment-held master key and audited without recording secret values.

Provider selection is an operational setting, distinct from the existing per-rail enablement flags (`PROVIDER_MTN`, etc.). A rail can be enabled while the payment gateway is disabled; in that case the payment creation request fails closed. `payment_intent.provider` remains the **rail** and a new field records the **gateway vendor**.

## Functional requirements

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| **HLFR-EXT-01** | A provider can be selected per capability while existing work remains pinned to its original provider. | EXT-01.1–01.4 and work units `WU-EXT-1`/`WU-EXT-2`. |
| LLFR-EXT-01.1 | A super-admin can read and change selected providers for storage, email, SMS, and payment through an audited, authenticated operational API. Selection validates the provider code, readiness, and environment policy; no credential values appear in responses. | Contract and REST tests: authorized change succeeds once; invalid/unconfigured choice fails; non-admin denied; exactly one audit entry. |
| LLFR-EXT-01.2 | A selection change affects only operations created after its commit. A queued operation or existing media asset retains its provider identity. | Concurrent toggle tests and persistence reload tests. |
| LLFR-EXT-01.3 | `disabled` rejects new real payment initiation; production cannot select `sandbox` unless the deployment is explicitly designated non-production. | Production-profile tests and payment resource test. |
| LLFR-EXT-01.4 | Missing selection rows, invalid provider IDs, or missing credentials fail closed, with a diagnostic that contains no secret. Existing callback/reconciliation routes remain active. | Startup and selection tests; replay of pre-switch operation. |
| **HLFR-EXT-02** | Store and deliver media and images through MEGA S4 while preserving access control. | EXT-02.1–02.4; `WU-EXT-3`. |
| LLFR-EXT-02.1 | S3 endpoint, signing region, credentials, public presign endpoint, and bucket names are configurable by store. MinIO remains local/test; MEGA S4 is a production option. | S4 compatibility test and local MinIO regression. |
| LLFR-EXT-02.2 | Originals and delivery objects stay private. Unowned for-sale content receives only the server-created 30-second preview. Purchased/free content can receive full delivery; signed URLs expire. | Playback/security tests, signed GET, 403 after expiry. |
| LLFR-EXT-02.3 | Every durable asset records its object-store location. A store switch routes new uploads to the new store and old reads to their stored location. | Mixed-store integration test after toggle and restart. |
| LLFR-EXT-02.4 | A migration copies and verifies all original, artwork, full, preview, and lossless objects before atomically changing an asset's location. Failed/partial copy leaves reads on the source. | Dry-run report, checksum/size validation, fault-injection restart test. |
| **HLFR-EXT-03** | Send transactional email through Resend with repeat-safe delivery. | EXT-03.1–03.3; `WU-EXT-4`/`WU-EXT-5`. |
| LLFR-EXT-03.1 | Notification email uses the Resend adapter when selected and passes a stable idempotency key. SMTP/Mailpit remains for local/test. | Fake HTTP/SDK test plus one authorized sandbox send. |
| LLFR-EXT-03.2 | Password reset uses the same selected transactional provider without logging the recipient or token. One reset request produces one message; a new request can produce a new message. | Identity unit/integration tests and redacted-log check. |
| LLFR-EXT-03.3 | API acceptance is tracked separately from final delivery. If delivery webhooks are enabled, only verified, deduplicated events update state. | Valid/invalid signature and duplicate/out-of-order event tests. |
| **HLFR-EXT-04** | Send SMS through FROG with stable message identity and delivery status. | EXT-04.1–04.3; `WU-EXT-6`. |
| LLFR-EXT-04.1 | FROG requests use documented headers/body, an approved sender ID, normalized destination, and stable client `msgid`; capture service remains local/test. | Fixture contract test and sandbox accepted send. |
| LLFR-EXT-04.2 | `ACCEPTD` means submitted, `DELIVRD` means delivered, and rejection/failure states are represented distinctly. Callbacks correlate to known `msgid` and cannot revive a terminal failure with an older event. | Lifecycle, duplicate, and out-of-order tests. |
| LLFR-EXT-04.3 | Ambiguous send timeouts are not blindly retried until FROG confirms repeated-`msgid` behavior. Callback authentication must be established before callback state becomes authoritative. | Provider evidence recorded in PR; fail-safe timeout test. |
| **HLFR-EXT-05** | Redde payment initiation and reconciliation are safe across provider switches. | EXT-05.1–05.4; `WU-EXT-2`/`WU-EXT-7`. |
| LLFR-EXT-05.1 | Each charge and payout records its gateway vendor independently of rail; subsequent status checks and webhooks use the recorded vendor even after the selection changes. | Toggle-with-inflight tests for charge and payout. |
| LLFR-EXT-05.2 | Webhook body/redirect alone never grants ownership or posts a payout. Terminal state comes from trusted provider status, with existing idempotency and balanced ledger rules. | Adversarial webhook replay and ledger/ownership tests. |
| LLFR-EXT-05.3 | Production payment `disabled` returns a stable error for new initiation and reports no chargeable methods to clients, while old Redde work still reconciles. | Resource and `GET /v1/payments/providers` contract tests. |
| LLFR-EXT-05.4 | Hosted card checkout follows the returned `checkoutUrl` and only treats server-confirmed order settlement as paid. | Frontend checkout test and browser/API integration scenario. |

## Nonfunctional requirements

- **NFR-EXT-01 Security:** TLS for external calls; private S4 buckets; least-privilege keys; no credential, reset token, address, phone number, or payment instrument in logs. Webhook authenticity is provider-specific. Redde callbacks require authenticated status pull as already designed. FROG callback trust is an explicit validation gate.
- **NFR-EXT-02 Reliability:** retries keep stable operation IDs; state changes are monotonic and idempotent; provider outages do not roll back the in-app notification or convert a real payment to sandbox. Define finite HTTP timeouts and classify 429/5xx versus permanent validation/authentication errors.
- **NFR-EXT-03 Observability:** metrics and traces identify capability and provider, not PII. Alert on aged pending payments, payout confirmations, media read errors, notification queue age, and provider rejection rates.
- **NFR-EXT-04 Compatibility:** keep existing public DTOs and semantics. New operational endpoints must be added to [API-CONTRACT.md](../../../API-CONTRACT.md), [frontend types](../../../Frontend/src/types/index.ts) only if consumed, and OpenAPI contract tests. No direct browser exposure of provider credentials or private object keys.
- **NFR-EXT-05 Migration:** forward-only Flyway; deterministic backfill. Any unresolved nonterminal historical record blocks switching that capability until classified. Source media stays available through a documented rollback window.

## Canonical acceptance scenarios

1. **Mid-flight charge:** create a Redde charge, select `disabled`, receive callback, pull Redde status, settle once, grant ownership once, and post balanced ledger entries once. A new charge is rejected.
2. **Mid-flight payout:** send via Redde, disable new payouts, receive duplicate cashout callbacks, confirm status, and post one disbursement. No re-send occurs.
3. **Mixed media:** upload asset A to MinIO, switch new writes to S4, upload B, restart, and fetch A from MinIO and B from S4. An unowned listener gets only preview for both.
4. **Email switch:** enqueue SMTP notification, select Resend, enqueue another, retry both, and observe each uses its recorded provider and stable key. Password reset uses current selection for a new request.
5. **SMS lifecycle:** FROG returns `ACCEPTD`, then sends `DELIVRD` twice; one final delivery is recorded. A delayed older failure event does not reverse it.
6. **Fail closed:** choose a provider with missing credentials or an invalid name and confirm no outbound request occurs; the admin receives a safe error and audit records only successful mutations.

## External provider references and verification status

- [MEGA S4 API coverage](https://github.com/meganz/s4-specs/blob/main/README.md): S3 path style and SigV4 presigning supported; presigned POST and several checksum headers unsupported. Real SDK compatibility remains to be exercised.
- [Resend introduction](https://resend.com/docs/introduction), [Java](https://resend.com/java), [idempotency](https://resend.com/changelog/idempotency-keys), [webhook verification](https://resend.com/changelog/managing-webhooks-via-api): provider behavior documented; account/domain setup remains an activation input.
- [FROG send](https://frogdocs.wigal.com.gh/send_general.html) and [callback](https://frogdocs.wigal.com.gh/callback.html): request/response documented; callback authentication and repeated-`msgid` semantics remain unverified.
- [Redde developer portal](https://developers.reddeonline.com/): the site did not expose readable endpoint text during review. Compare the [current adapter](../../../backend/src/main/java/org/shakvilla/beatzmedia/payments/adapter/out/integration/redde/ReddePaymentGateway.java) with merchant sandbox evidence before live activation.
