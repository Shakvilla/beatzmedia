# Provider integrations: configuration and activation matrix

**Purpose:** name the configuration an agent must wire and the non-secret selection values an operator may change. Names marked **proposed** become canonical only when their WU adds them to [application.properties](../../../backend/src/main/resources/application.properties), the deployment guide, and tests. Do not put real values in this repository.

## Configuration sources

| Kind | Source | Rule |
| --- | --- | --- |
| Provider selection | Audited `provider_selection` rows controlled by the operational API | Contains capability, provider code, version, actor, and update time only. Missing/invalid selection fails closed. |
| Provider secrets | Environment or deployment secret store | Never database rows, frontend bundles, Graphify output, logs, or API responses. |
| Non-secret provider config | `application.properties`/environment | Endpoint, region, bucket, sender, callback and timeouts may be externally configured; treat URLs and IDs as operational data. |
| Local dependencies | [Docker Compose](../../../backend/docker-compose.yml) | MinIO, Mailpit, and SMS capture stay on by default for local smoke tests. |

## Existing variables to preserve

| Capability | Existing variable names | Current meaning |
| --- | --- | --- |
| Storage | `BEATZ_S3_ENDPOINT`, `BEATZ_S3_ACCESS_KEY`, `BEATZ_S3_SECRET_KEY`, `BEATZ_S3_BUCKET_ORIGINALS`, `BEATZ_S3_BUCKET_DELIVERY`, `BEATZ_SIGNED_URL_TTL_SECONDS` | Current S3/MinIO client and signed delivery. `beatz.s3.public-endpoint` already exists as a direct config property without a listed env placeholder. |
| Payment | `BEATZ_REDDE_API_KEY`, `BEATZ_REDDE_APP_ID`, `BEATZ_REDDE_BASE_URL`, `BEATZ_REDDE_MERCHANT_NAME`, `BEATZ_REDDE_LOGO_LINK`, `BEATZ_REDDE_CHECKOUT_SUCCESS_URL`, `BEATZ_REDDE_CHECKOUT_FAILURE_URL` | Current Redde adapter. Legacy `BEATZ_PAYMENT_WEBHOOK_SECRET` is for signed generic callbacks; Redde uses authenticated status pull. |
| Email | `QUARKUS_MAILER_FROM`, `QUARKUS_MAILER_HOST`, `QUARKUS_MAILER_PORT`, `QUARKUS_MAILER_START_TLS`, `QUARKUS_MAILER_MOCK` | SMTP/Mailpit path, retained for local/test. |
| SMS | `BEATZ_SMS_ENDPOINT`, `BEATZ_SMS_API_KEY` | Existing generic capture sender; these alone do not configure FROG. |
| Clients | `VITE_API_URL`, `API_ORIGIN` | Backend API origins only; provider keys must never enter these client settings. |

## Provider integration variables

| Capability | Names | Required to select live provider |
| --- | --- | --- |
| MEGA S4 | `BEATZ_S4_ENDPOINT`, `BEATZ_S4_PUBLIC_ENDPOINT`, `BEATZ_S4_REGION`, `BEATZ_S4_ACCESS_KEY`, `BEATZ_S4_SECRET_KEY`, `BEATZ_S4_BUCKET_ORIGINALS`, `BEATZ_S4_BUCKET_DELIVERY` | Reachable HTTPS endpoint, signing region and public host, least-privilege credentials, private buckets; S4 compatibility and location WUs complete. Avoid reusing MinIO keys in production. |
| Resend | `BEATZ_RESEND_API_KEY`, `BEATZ_RESEND_FROM`, `BEATZ_RESEND_ENDPOINT` (test override only); `BEATZ_RESEND_WEBHOOK_SECRET` remains proposed if receiving events | Verified sending domain and sender. Webhooks are not yet implemented. |
| FROG | `BEATZ_FROG_API_KEY`, `BEATZ_FROG_USERNAME`, `BEATZ_FROG_SENDER_ID`, `BEATZ_FROG_BASE_URL`, `BEATZ_FROG_CALLBACK_SECRET` (only if provider supports a shared secret) | Approved sender ID and account credentials. Callback trust and duplicate-`msgid` behavior need vendor evidence before enabling authoritative callbacks or ambiguous-timeout retries. |
| Redde | Keep `BEATZ_REDDE_*`; add a dedicated callback URL config only if merchant setup requires it. | Merchant sandbox/live account and callback configuration, validated rails, and gateway-pinned records. |

The provider-selection foundation made the S4, Resend, and FROG names above canonical in `application.properties`. The S4 names remain separate from MinIO because both clients must run during migration.

## Selection values and environment policy

| Capability | Local/test default | Production initial state | Allowed production activation |
| --- | --- | --- | --- |
| `storage` | `minio` | Existing verified store until migration | `mega_s4` only after EXT-3 and EXT-4, private-bucket test, and old-asset inventory. |
| `email` | `smtp` to Mailpit | Existing verified SMTP or `disabled` if no real SMTP exists | `resend` after verified domain, key, and EXT-5/6 evidence. An explicit `disabled` email choice may be needed for controlled outage response; if added, password-reset UX and queued-message semantics must be specified. |
| `sms` | `capture` | `disabled` until FROG activation | `frog` after EXT-7 and sender/callback checks. `capture` is prohibited in production. |
| `payment` | `sandbox` | `disabled` | `redde` after EXT-2 and EXT-9 merchant-sandbox evidence. `sandbox` is prohibited in production. |

The selection enum must include `disabled` for any capability whose production initial state or emergency stop requires it. This refines the prior design's email/SMS choices. A disabled email/SMS provider means **no new external send**; an already queued attempt retains its recorded provider and can retry. If the product needs to pause *all* retries, specify a separate queue-pause control rather than reinterpret the provider field. In-app notifications continue to persist when email/SMS is disabled.

## Activation checklist for an operator

1. Deploy and verify schema and provider-aware reads before enabling a new write provider. Confirm the target credentials exist without logging them.
2. Confirm the selected provider's readiness, callback URL/DNS configuration, and one safe sandbox request. For S4, verify browser presigned URL host and playback; for Redde, verify live merchant status pull and old pending rows.
3. Change selection through the audited super-admin API with `expectedVersion`. Record the resulting version and change time.
4. Watch provider-specific error rates, queue age, pending payments/payouts, and signed URL failures. On a problem, choose `disabled` or the previous provider for **new** work; leave recorded-provider reconciliation active.
5. For media rollback, use the retained source objects and location manifest. Do not delete source buckets merely because the default changed.
